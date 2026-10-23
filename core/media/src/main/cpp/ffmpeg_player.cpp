#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "playback_support.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/imgutils.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>
}

namespace {
int64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}
struct FreePacket { void operator()(AVPacket *packet) const { av_packet_free(&packet); } };
struct Item {
    enum Kind { Data, Flush, End } kind = Data;
    uint64_t epoch = 0;
    int64_t targetMs = 0;
    std::unique_ptr<AVPacket, FreePacket> packet;
    static Item marker(Kind kind, uint64_t epoch, int64_t target = 0) {
        Item item; item.kind = kind; item.epoch = epoch; item.targetMs = target; return item;
    }
};
struct Player {
    JavaVM *vm = nullptr;
    jobject owner = nullptr;
    jmethodID preparedCallback = nullptr, renderedCallback = nullptr, audioCallback = nullptr;
    jmethodID audioClockCallback = nullptr, audioResetCallback = nullptr, audioDrainCallback = nullptr, audioEndedCallback = nullptr;
    jmethodID seekBeginCallback = nullptr, seekCompleteCallback = nullptr;
    jmethodID completionCallback = nullptr, errorCallback = nullptr;
    std::thread worker;
    std::mutex controlMutex;
    std::condition_variable controlChanged;
    playback::Timeline timeline;
    int64_t observedAudioMs = -1, observedAudioAtMs = 0; // protected by controlMutex
    bool clockPrimed = false;
    int64_t clockTargetMs = 0;
    std::atomic<bool> abort{false}, started{false}, paused{false}, finished{false}, failed{false};
    std::atomic<bool> opened{false}, firstOutput{false};
    std::atomic<uint64_t> epoch{0}, audioDone{UINT64_MAX}, videoDone{UINT64_MAX};
    std::atomic<int64_t> pendingSeekMs{-1}, positionMs{0}, durationMs{0};
    bool seekNotification = false; // protected by controlMutex
    std::atomic<bool> looping{false};
    bool hasAudio = false, hasVideo = false; // immutable after prepare, before consumers start
    std::atomic<float> speed{1.f};
    playback::EpochQueue<Item> audioQueue{256, 2 * 1024 * 1024};
    playback::EpochQueue<Item> videoQueue{256, 16 * 1024 * 1024};
    std::string source, headers;
    std::mutex windowMutex;
    ANativeWindow *window = nullptr;
    std::atomic<int> videoWidth{0}, videoHeight{0};
    int64_t formatStartUs = 0;
};
class Env {
public:
    explicit Env(Player *player) : player_(player) {
        jint result = player->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) attached_ = player->vm->AttachCurrentThread(&env, nullptr) == JNI_OK;
    }
    ~Env() { if (attached_) player_->vm->DetachCurrentThread(); }
    JNIEnv *env = nullptr;
private:
    Player *player_;
    bool attached_ = false;
};
bool clearException(JNIEnv *env) {
    if (!env->ExceptionCheck()) return false;
    env->ExceptionClear();
    return true;
}
void simple(Player *p, jmethodID method) {
    Env j(p); if (!j.env) return;
    j.env->CallVoidMethod(p->owner, method);
    clearException(j.env);
}
void stop(Player *p) {
    {
        std::lock_guard<std::mutex> lock(p->controlMutex);
        p->abort.store(true);
    }
    p->controlChanged.notify_all();
    p->audioQueue.abort();
    p->videoQueue.abort();
}
void fail(Player *p, int error, const std::string &message) {
    if (p->abort.load() || p->failed.exchange(true)) return;
    p->finished.store(true);
    stop(p);
    Env j(p); if (!j.env) return;
    jstring text = j.env->NewStringUTF(message.c_str());
    j.env->CallVoidMethod(p->owner, p->errorCallback, error, text);
    clearException(j.env);
    j.env->DeleteLocalRef(text);
}
std::string errorText(int error) {
    char text[AV_ERROR_MAX_STRING_SIZE] = {};
    av_strerror(error, text, sizeof(text));
    return text;
}
bool current(Player *p, uint64_t epoch) { return !p->abort.load() && p->epoch.load() == epoch; }
bool recordPosition(Player *p, uint64_t epoch, int64_t ptsUs) {
    std::lock_guard<std::mutex> lock(p->controlMutex);
    if (!current(p, epoch)) return false;
    p->positionMs.store(std::max<int64_t>(0, ptsUs / 1000));
    return true;
}
int interruptIo(void *opaque) {
    auto *p = static_cast<Player *>(opaque);
    return p->abort.load() || (p->opened.load() && p->pendingSeekMs.load() >= 0);
}
void setWindow(Player *p, ANativeWindow *window) {
    std::lock_guard<std::mutex> lock(p->windowMutex);
    if (p->window) ANativeWindow_release(p->window);
    p->window = window;
    if (window && p->videoWidth.load() > 0 && p->videoHeight.load() > 0)
        ANativeWindow_setBuffersGeometry(window, p->videoWidth.load(), p->videoHeight.load(), WINDOW_FORMAT_RGBA_8888);
}
int64_t audioClock(Player *p) {
    Env j(p); if (!j.env) return -1;
    jlong value = j.env->CallLongMethod(p->owner, p->audioClockCallback);
    return clearException(j.env) ? -1 : value;
}
bool resetAudio(Player *p, uint64_t epoch, int64_t targetMs) {
    Env j(p); if (!j.env) return false;
    jboolean ok = j.env->CallBooleanMethod(p->owner, p->audioResetCallback,
                                          static_cast<jlong>(epoch), static_cast<jlong>(targetMs));
    return !clearException(j.env) && ok;
}
bool writeAudio(Player *p, const uint8_t *data, int bytes, uint64_t epoch, int64_t ptsUs) {
    if (bytes <= 0) return true;
    if (!current(p, epoch)) return false;
    Env j(p); if (!j.env) return false;
    jbyteArray array = j.env->NewByteArray(bytes);
    if (!array) { clearException(j.env); return false; }
    j.env->SetByteArrayRegion(array, 0, bytes, reinterpret_cast<const jbyte *>(data));
    jboolean ok = j.env->CallBooleanMethod(p->owner, p->audioCallback, array,
                                          static_cast<jlong>(epoch), static_cast<jlong>(ptsUs / 1000));
    bool exception = clearException(j.env);
    j.env->DeleteLocalRef(array);
    return !exception && ok;
}
bool drainAudio(Player *p, uint64_t epoch) {
    Env j(p); if (!j.env) return false;
    jboolean ok = j.env->CallBooleanMethod(p->owner, p->audioDrainCallback, static_cast<jlong>(epoch));
    return !clearException(j.env) && ok;
}
void rendered(Player *p) {
    if (!p->firstOutput.exchange(true)) simple(p, p->renderedCallback);
}
void seekComplete(Player *p, uint64_t epoch, int64_t positionMs) {
    {
        std::lock_guard<std::mutex> lock(p->controlMutex);
        if (!current(p, epoch) || !p->seekNotification) return;
        p->seekNotification = false;
    }
    Env j(p); if (!j.env) return;
    j.env->CallVoidMethod(p->owner, p->seekCompleteCallback,
                         static_cast<jlong>(epoch), static_cast<jlong>(positionMs));
    clearException(j.env);
    p->controlChanged.notify_all();
}
void requestSeek(Player *p, int64_t target, bool notify) {
    uint64_t epoch;
    std::unique_lock<std::mutex> lock(p->controlMutex);
    if (p->abort.load()) return;
    if (p->durationMs.load() > 0) target = std::min(target, p->durationMs.load());
    target = std::max<int64_t>(0, target);
    epoch = p->epoch.fetch_add(1) + 1;
    p->pendingSeekMs.store(target);
    p->positionMs.store(target);
    p->timeline.running(false, nowMs());
    p->timeline.reset(target, nowMs());
    p->clockTargetMs = target;
    p->clockPrimed = false;
    p->observedAudioMs = -1;
    p->observedAudioAtMs = nowMs();
    p->seekNotification = notify;
    // Notify the Java writer before new queues become visible. Old nonblocking writes cancel.
    {
        Env j(p);
        if (j.env) {
            j.env->CallVoidMethod(p->owner, p->seekBeginCallback, static_cast<jlong>(epoch));
            clearException(j.env);
        }
    }
    p->audioQueue.reset(epoch, Item::marker(Item::Flush, epoch, target));
    p->videoQueue.reset(epoch, Item::marker(Item::Flush, epoch, target));
    lock.unlock();
    p->controlChanged.notify_all();
}
bool runnable(Player *p, uint64_t epoch, bool preview = false) {
    std::unique_lock<std::mutex> lock(p->controlMutex);
    p->controlChanged.wait(lock, [&] {
        return !current(p, epoch) || (p->started.load() &&
            (!p->paused.load() || (preview && p->seekNotification)));
    });
    return current(p, epoch);
}
enum class Due { Ready, Drop, Cancelled };
Due waitUntil(Player *p, uint64_t epoch, int64_t ptsUs, bool useAudio, bool preview) {
    while (runnable(p, epoch, preview)) {
        double clock;
        {
            std::unique_lock<std::mutex> lock(p->controlMutex);
            if (!current(p, epoch)) return Due::Cancelled;
            if (preview && p->paused.load() && p->seekNotification) return Due::Ready;
            if (!p->clockPrimed) {
                if (useAudio && p->hasAudio && p->audioDone.load() != epoch) {
                    bool woke = p->controlChanged.wait_for(lock, std::chrono::milliseconds(250), [&] {
                        return !current(p, epoch) || p->clockPrimed || p->paused.load() || p->audioDone.load() == epoch;
                    });
                    // Some containers put a long video chunk before the first audio
                    // packet. Permit video to free queue space instead of starving demux.
                    if (woke || !p->audioQueue.empty()) continue;
                }
                p->timeline.reset(p->clockTargetMs, nowMs());
                p->timeline.running(p->started.load() && !p->paused.load(), nowMs());
                p->clockPrimed = true;
                p->controlChanged.notify_all();
            }
            clock = p->timeline.position(nowMs());
        }
        if (useAudio && p->audioDone.load() != epoch) {
            int64_t value = audioClock(p);
            bool empty = p->audioQueue.empty();
            std::lock_guard<std::mutex> lock(p->controlMutex);
            if (!current(p, epoch)) return Due::Cancelled;
            if (value >= 0) {
                int64_t now = nowMs();
                if (value != p->observedAudioMs) {
                    p->observedAudioMs = value;
                    p->observedAudioAtMs = now;
                    p->timeline.reset(value, now);
                }
                // A starved audio queue must not block demux behind a full video queue.
                // Advance from the last observed audio time until packets arrive again.
                clock = empty && now - p->observedAudioAtMs > 250 ? p->timeline.position(now) : value;
            }
        }
        double delta = ptsUs / 1000.0 - clock;
        if (delta < -180) return Due::Drop;
        if (delta <= 5) return current(p, epoch) ? Due::Ready : Due::Cancelled;
        std::unique_lock<std::mutex> lock(p->controlMutex);
        p->controlChanged.wait_for(lock, std::chrono::milliseconds(
                std::max<int64_t>(1, std::min<int64_t>(10, static_cast<int64_t>(delta / p->speed.load())))));
    }
    return Due::Cancelled;
}
int64_t frameTimeUs(AVFrame *frame, AVStream *stream, Player *p, int64_t fallback) {
    int64_t pts = frame->best_effort_timestamp;
    if (pts == AV_NOPTS_VALUE) pts = frame->pts;
    return pts == AV_NOPTS_VALUE ? fallback : av_rescale_q(pts, stream->time_base, AV_TIME_BASE_Q) - p->formatStartUs;
}
template<class Output> bool decode(Player *p, AVCodecContext *codec, AVPacket *packet,
                                   AVFrame *frame, uint64_t epoch, Output output) {
    auto receive = [&]() -> int {
        int ret;
        while ((ret = avcodec_receive_frame(codec, frame)) >= 0) {
            bool ok = !current(p, epoch) || output(frame);
            av_frame_unref(frame);
            if (!ok) return AVERROR_EXTERNAL;
            if (!current(p, epoch)) return AVERROR(EAGAIN);
        }
        return ret;
    };
    if (!current(p, epoch)) return true;
    int ret = avcodec_send_packet(codec, packet);
    if (ret == AVERROR(EAGAIN)) {
        ret = receive();
        if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) return false;
        if (!current(p, epoch)) return true;
        ret = avcodec_send_packet(codec, packet);
    }
    if (ret < 0 && ret != AVERROR_EOF) return false;
    ret = receive();
    return ret == AVERROR(EAGAIN) || ret == AVERROR_EOF;
}

void videoLoop(Player *p, AVCodecContext *codec, AVStream *stream) {
    AVFrame *frame = av_frame_alloc();
    if (!frame) { fail(p, AVERROR(ENOMEM), "Unable to allocate video frame"); return; }
    SwsContext *scale = nullptr;
    std::vector<uint8_t> rgba;
    Item item;
    int64_t floorUs = 0, nextUs = 0;
    while (p->videoQueue.pop(item)) {
        if (!current(p, item.epoch)) continue;
        if (item.kind == Item::Flush) {
            avcodec_flush_buffers(codec);
            floorUs = nextUs = item.targetMs * 1000;
            continue;
        }
        auto output = [&](AVFrame *decoded) -> bool {
            int64_t ptsUs = frameTimeUs(decoded, stream, p, nextUs);
            AVRational rate = stream->avg_frame_rate;
            nextUs = ptsUs + (rate.num > 0 && rate.den > 0 ? av_rescale_q(1, av_inv_q(rate), AV_TIME_BASE_Q) : 40000);
            if (ptsUs < floorUs) return true;
            Due due = waitUntil(p, item.epoch, ptsUs, p->hasAudio, true);
            if (due != Due::Ready) return true;
            int width = decoded->width, height = decoded->height;
            if (width <= 0 || height <= 0) return false;
            scale = sws_getCachedContext(scale, width, height, static_cast<AVPixelFormat>(decoded->format),
                                        width, height, AV_PIX_FMT_RGBA, SWS_BILINEAR, nullptr, nullptr, nullptr);
            if (!scale) return false;
            int size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, width, height, 1);
            if (size <= 0) return false;
            rgba.resize(size);
            uint8_t *planes[4] = {}; int lines[4] = {};
            if (av_image_fill_arrays(planes, lines, rgba.data(), AV_PIX_FMT_RGBA, width, height, 1) < 0) return false;
            sws_scale(scale, decoded->data, decoded->linesize, 0, height, planes, lines);
            bool posted = false;
            {
                std::lock_guard<std::mutex> lock(p->windowMutex);
                if (current(p, item.epoch) && p->window) {
                    ANativeWindow_setBuffersGeometry(p->window, width, height, WINDOW_FORMAT_RGBA_8888);
                    ANativeWindow_Buffer buffer{};
                    if (ANativeWindow_lock(p->window, &buffer, nullptr) == 0) {
                        for (int y = 0; y < std::min(height, buffer.height); ++y)
                            std::memcpy(static_cast<uint8_t *>(buffer.bits) + y * buffer.stride * 4,
                                        rgba.data() + y * lines[0], static_cast<size_t>(std::min(width, buffer.width)) * 4);
                        posted = ANativeWindow_unlockAndPost(p->window) == 0;
                    }
                }
            }
            if (posted && recordPosition(p, item.epoch, ptsUs)) {
                rendered(p);
                seekComplete(p, item.epoch, ptsUs / 1000);
            }
            return true;
        };
        if (!runnable(p, item.epoch, true)) continue;
        bool ok = decode(p, codec, item.kind == Item::End ? nullptr : item.packet.get(), frame, item.epoch, output);
        if (!ok && current(p, item.epoch)) { fail(p, AVERROR_INVALIDDATA, "Video decode or rendering failed"); break; }
        if (item.kind == Item::End && current(p, item.epoch)) {
            // Keep the final picture visible for its frame duration, including video-only files.
            waitUntil(p, item.epoch, std::max(floorUs, nextUs), p->hasAudio, false);
            if (!current(p, item.epoch)) continue;
            { std::lock_guard<std::mutex> lock(p->controlMutex); p->videoDone.store(item.epoch); }
            p->controlChanged.notify_all();
        }
    }
    sws_freeContext(scale);
    av_frame_free(&frame);
}

void audioLoop(Player *p, AVCodecContext *codec, AVStream *stream) {
    AVFrame *frame = av_frame_alloc();
    if (!frame) { fail(p, AVERROR(ENOMEM), "Unable to allocate audio frame"); return; }
    SwrContext *swr = nullptr;
    AVChannelLayout inputLayout{};
    int inputRate = 0, inputFormat = -1;
    int64_t floorUs = 0, nextUs = 0;
    bool first = true;
    std::vector<uint8_t> pcm;
    Item item;
    auto outputPcm = [&](int samples, int64_t ptsUs) -> bool {
        int skip = playback::trimSamples(ptsUs, 48000, samples, floorUs);
        nextUs = ptsUs + av_rescale(samples, AV_TIME_BASE, 48000);
        if (skip == samples) return true;
        ptsUs += av_rescale(skip, AV_TIME_BASE, 48000);
        if (first) {
            Due due = waitUntil(p, item.epoch, ptsUs, false, false);
            if (due == Due::Cancelled) return true;
            // Audio must establish its own clock even after a slow initial decode.
            first = false;
        }
        bool ok = writeAudio(p, pcm.data() + skip * 4, (samples - skip) * 4, item.epoch, ptsUs);
        if (ok && recordPosition(p, item.epoch, ptsUs)) {
            if (!p->hasVideo) { rendered(p); seekComplete(p, item.epoch, ptsUs / 1000); }
        }
        return ok || !current(p, item.epoch);
    };
    while (p->audioQueue.pop(item)) {
        if (!current(p, item.epoch)) continue;
        if (item.kind == Item::Flush) {
            avcodec_flush_buffers(codec);
            swr_free(&swr);
            av_channel_layout_uninit(&inputLayout);
            inputRate = 0; inputFormat = -1;
            floorUs = nextUs = item.targetMs * 1000;
            first = true;
            if (!resetAudio(p, item.epoch, item.targetMs) && current(p, item.epoch)) {
                fail(p, AVERROR_EXTERNAL, "Unable to reset audio output"); break;
            }
            continue;
        }
        auto output = [&](AVFrame *decoded) -> bool {
            if (!runnable(p, item.epoch)) return true;
            if (decoded->sample_rate <= 0 || decoded->ch_layout.nb_channels <= 0) return false;
            if (!swr || inputRate != decoded->sample_rate || inputFormat != decoded->format ||
                    av_channel_layout_compare(&inputLayout, &decoded->ch_layout) != 0) {
                swr_free(&swr);
                av_channel_layout_uninit(&inputLayout);
                if (av_channel_layout_copy(&inputLayout, &decoded->ch_layout) < 0) return false;
                inputRate = decoded->sample_rate; inputFormat = decoded->format;
                AVChannelLayout stereo = AV_CHANNEL_LAYOUT_STEREO;
                if (swr_alloc_set_opts2(&swr, &stereo, AV_SAMPLE_FMT_S16, 48000, &inputLayout,
                                       static_cast<AVSampleFormat>(inputFormat), inputRate, 0, nullptr) < 0 ||
                        swr_init(swr) < 0) return false;
            }
            int64_t delay = swr_get_delay(swr, inputRate);
            int64_t ptsUs = frameTimeUs(decoded, stream, p, nextUs) - av_rescale(delay, AV_TIME_BASE, inputRate);
            int capacity = static_cast<int>(av_rescale_rnd(delay + decoded->nb_samples, 48000, inputRate, AV_ROUND_UP));
            if (capacity <= 0) return true;
            pcm.resize(static_cast<size_t>(capacity) * 4);
            uint8_t *planes[] = {pcm.data()};
            int samples = swr_convert(swr, planes, capacity, const_cast<const uint8_t **>(decoded->extended_data), decoded->nb_samples);
            return samples >= 0 && outputPcm(samples, ptsUs);
        };
        if (!runnable(p, item.epoch)) continue;
        bool ok = decode(p, codec, item.kind == Item::End ? nullptr : item.packet.get(), frame, item.epoch, output);
        if (ok && item.kind == Item::End && current(p, item.epoch)) {
            // Flush delayed resampler samples before waiting for the hardware playback head.
            while (swr && current(p, item.epoch)) {
                int capacity = swr_get_out_samples(swr, 0);
                if (capacity <= 0) break;
                pcm.resize(static_cast<size_t>(capacity) * 4);
                uint8_t *planes[] = {pcm.data()};
                int samples = swr_convert(swr, planes, capacity, nullptr, 0);
                if (samples < 0) { ok = false; break; }
                if (samples == 0) break;
                if (!outputPcm(samples, nextUs)) { ok = false; break; }
            }
            if (ok && current(p, item.epoch)) ok = drainAudio(p, item.epoch);
            if (ok && current(p, item.epoch)) {
                int64_t clock = audioClock(p);
                {
                    Env j(p);
                    if (j.env) {
                        j.env->CallVoidMethod(p->owner, p->audioEndedCallback, static_cast<jlong>(item.epoch));
                        clearException(j.env);
                    }
                }
                {
                    std::lock_guard<std::mutex> lock(p->controlMutex);
                    if (current(p, item.epoch)) {
                        p->timeline.reset(clock >= 0 ? clock : p->clockTargetMs, nowMs());
                        p->timeline.running(p->started.load() && !p->paused.load(), nowMs());
                        p->clockPrimed = true;
                        p->audioDone.store(item.epoch);
                    }
                }
                p->controlChanged.notify_all();
            }
        }
        if (!ok && current(p, item.epoch)) { fail(p, AVERROR_EXTERNAL, "Audio decode or output failed"); break; }
    }
    swr_free(&swr);
    av_channel_layout_uninit(&inputLayout);
    av_frame_free(&frame);
}

void runPlayer(Player *p) {
    AVFormatContext *format = avformat_alloc_context();
    if (!format) { fail(p, AVERROR(ENOMEM), "Unable to allocate format context"); return; }
    format->interrupt_callback = {interruptIo, p};
    AVDictionary *options = nullptr;
    if (!p->headers.empty()) av_dict_set(&options, "headers", p->headers.c_str(), 0);
    av_dict_set(&options, "rw_timeout", "15000000", 0);
    av_dict_set(&options, "reconnect", "1", 0);
    av_dict_set(&options, "reconnect_streamed", "1", 0);
    av_dict_set(&options, "reconnect_delay_max", "2", 0);
    int ret = avformat_open_input(&format, p->source.c_str(), nullptr, &options);
    av_dict_free(&options);
    if (ret >= 0) ret = avformat_find_stream_info(format, nullptr);
    if (ret < 0) {
        fail(p, ret, errorText(ret));
        avformat_close_input(&format);
        return;
    }
    int videoIndex = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    int audioIndex = av_find_best_stream(format, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    AVCodecContext *video = nullptr, *audio = nullptr;
    auto open = [&](int index, AVCodecContext **codec) -> int {
        if (index < 0) return 0;
        AVStream *stream = format->streams[index];
        const AVCodec *decoder = avcodec_find_decoder(stream->codecpar->codec_id);
        if (!decoder) return AVERROR_DECODER_NOT_FOUND;
        *codec = avcodec_alloc_context3(decoder);
        if (!*codec) return AVERROR(ENOMEM);
        int result = avcodec_parameters_to_context(*codec, stream->codecpar);
        if (result < 0) return result;
        (*codec)->pkt_timebase = stream->time_base;
        return avcodec_open2(*codec, decoder, nullptr);
    };
    ret = videoIndex < 0 && audioIndex < 0 ? AVERROR_STREAM_NOT_FOUND : open(videoIndex, &video);
    if (ret >= 0) ret = open(audioIndex, &audio);
    if (ret < 0) {
        fail(p, ret, errorText(ret));
        avcodec_free_context(&video); avcodec_free_context(&audio); avformat_close_input(&format);
        return;
    }
    p->hasVideo = video != nullptr; p->hasAudio = audio != nullptr;
    p->formatStartUs = format->start_time == AV_NOPTS_VALUE ? 0 : format->start_time;
    int64_t duration = format->duration == AV_NOPTS_VALUE ? 0 : format->duration / 1000;
    if (duration <= 0) {
        for (int index : {videoIndex, audioIndex}) if (index >= 0) {
            AVStream *stream = format->streams[index];
            if (stream->duration != AV_NOPTS_VALUE)
                duration = std::max(duration, av_rescale_q(stream->duration, stream->time_base, AVRational{1, 1000}));
        }
    }
    p->durationMs.store(duration);
    p->videoWidth.store(video ? video->width : 0); p->videoHeight.store(video ? video->height : 0);
    bool prepared = false;
    {
        Env j(p);
        if (j.env) {
            jboolean ok = j.env->CallBooleanMethod(p->owner, p->preparedCallback,
                    video ? video->width : 0, video ? video->height : 0, audio ? 48000 : 0, audio ? 2 : 0,
                    static_cast<jlong>(duration));
            prepared = !clearException(j.env) && ok;
        }
    }
    if (!prepared || p->abort.load()) {
        if (!p->abort.load()) fail(p, AVERROR_EXTERNAL, "Unable to prepare output");
        avcodec_free_context(&video); avcodec_free_context(&audio); avformat_close_input(&format);
        return;
    }
    p->opened.store(true);
    p->audioQueue.reset(0, Item::marker(Item::Flush, 0));
    p->videoQueue.reset(0, Item::marker(Item::Flush, 0));
    std::thread audioWorker, videoWorker;
    if (audio) audioWorker = std::thread(audioLoop, p, audio, format->streams[audioIndex]);
    if (video) videoWorker = std::thread(videoLoop, p, video, format->streams[videoIndex]);
    AVPacket *packet = av_packet_alloc();
    if (!packet) fail(p, AVERROR(ENOMEM), "Unable to allocate packet");
    while (!p->abort.load()) {
        int64_t target;
        uint64_t epoch;
        {
            std::lock_guard<std::mutex> lock(p->controlMutex);
            target = p->pendingSeekMs.exchange(-1);
            epoch = p->epoch.load();
        }
        if (target >= 0) {
            // Clear an interrupted AVIO read before issuing the new seek.
            if (format->pb) { format->pb->error = 0; format->pb->eof_reached = 0; }
            ret = avformat_seek_file(format, -1, INT64_MIN, (target * 1000 + p->formatStartUs),
                                     target * 1000 + p->formatStartUs, 0);
            if (!current(p, epoch)) continue;
            if (ret < 0) { fail(p, ret, "Seek failed: " + errorText(ret)); break; }
            if (format->pb) { format->pb->error = 0; format->pb->eof_reached = 0; }
        }
        ret = av_read_frame(format, packet);
        if (!current(p, epoch)) { av_packet_unref(packet); continue; }
        if (ret == AVERROR_EOF) {
            if (audio) p->audioQueue.push(epoch, Item::marker(Item::End, epoch));
            if (video) p->videoQueue.push(epoch, Item::marker(Item::End, epoch));
            std::unique_lock<std::mutex> lock(p->controlMutex);
            p->controlChanged.wait(lock, [&] {
                return !current(p, epoch) || ((!audio || p->audioDone.load() == epoch) && (!video || p->videoDone.load() == epoch));
            });
            if (!current(p, epoch)) continue;
            lock.unlock();
            if (p->looping) { requestSeek(p, 0, false); continue; }
            p->finished.store(true);
            simple(p, p->completionCallback);
            break;
        }
        if (ret == AVERROR(EAGAIN)) {
            std::unique_lock<std::mutex> lock(p->controlMutex);
            p->controlChanged.wait_for(lock, std::chrono::milliseconds(10));
            continue;
        }
        if (ret < 0) {
            if (p->pendingSeekMs.load() >= 0) continue;
            fail(p, ret, errorText(ret)); break;
        }
        playback::EpochQueue<Item> *queue = packet->stream_index == videoIndex ? &p->videoQueue :
                (packet->stream_index == audioIndex ? &p->audioQueue : nullptr);
        if (queue) {
            Item item; item.epoch = epoch; item.packet.reset(av_packet_clone(packet));
            if (!item.packet) { fail(p, AVERROR(ENOMEM), "Unable to queue packet"); break; }
            queue->push(epoch, std::move(item), static_cast<size_t>(packet->size));
        }
        av_packet_unref(packet);
    }
    stop(p);
    if (audioWorker.joinable()) audioWorker.join();
    if (videoWorker.joinable()) videoWorker.join();
    av_packet_free(&packet);
    avcodec_free_context(&video); avcodec_free_context(&audio); avformat_close_input(&format);
    p->finished.store(true);
}
Player *fromHandle(jlong handle) { return reinterpret_cast<Player *>(static_cast<intptr_t>(handle)); }
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeCreate(JNIEnv *env, jobject, jobject callbacks) {
    auto *p = new Player();
    env->GetJavaVM(&p->vm);
    p->owner = env->NewGlobalRef(callbacks);
    jclass type = env->GetObjectClass(callbacks);
    p->preparedCallback = env->GetMethodID(type, "onNativePrepared", "(IIIIJ)Z");
    p->renderedCallback = env->GetMethodID(type, "onNativeRenderingStart", "()V");
    p->audioCallback = env->GetMethodID(type, "onNativeAudioData", "([BJJ)Z");
    p->audioClockCallback = env->GetMethodID(type, "onNativeAudioClockMs", "()J");
    p->audioResetCallback = env->GetMethodID(type, "onNativeAudioReset", "(JJ)Z");
    p->audioDrainCallback = env->GetMethodID(type, "onNativeAudioDrain", "(J)Z");
    p->audioEndedCallback = env->GetMethodID(type, "onNativeAudioEnded", "(J)V");
    p->seekBeginCallback = env->GetMethodID(type, "onNativeSeekBegin", "(J)V");
    p->seekCompleteCallback = env->GetMethodID(type, "onNativeSeekComplete", "(JJ)V");
    p->completionCallback = env->GetMethodID(type, "onNativeCompletion", "()V");
    p->errorCallback = env->GetMethodID(type, "onNativeError", "(ILjava/lang/String;)V");
    env->DeleteLocalRef(type);
    if (env->ExceptionCheck()) { env->DeleteGlobalRef(p->owner); delete p; return 0; }
    av_log_set_level(AV_LOG_ERROR);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(p));
}
extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativePrepare(JNIEnv *env, jobject, jlong handle, jstring source, jstring headers, jboolean loop) {
    Player *p = fromHandle(handle); if (!p || p->worker.joinable()) return;
    const char *url = env->GetStringUTFChars(source, nullptr);
    const char *head = headers ? env->GetStringUTFChars(headers, nullptr) : nullptr;
    p->source = url ? url : ""; p->headers = head ? head : ""; p->looping = loop;
    if (url) env->ReleaseStringUTFChars(source, url);
    if (head) env->ReleaseStringUTFChars(headers, head);
    p->worker = std::thread(runPlayer, p);
}
extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeSetSurface(JNIEnv *env, jobject, jlong handle, jobject surface) {
    Player *p = fromHandle(handle); if (p) setWindow(p, surface ? ANativeWindow_fromSurface(env, surface) : nullptr);
}
extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeStart(JNIEnv *, jobject, jlong handle) {
    Player *p = fromHandle(handle); if (!p) return;
    {
        std::lock_guard<std::mutex> lock(p->controlMutex);
        p->started.store(true); p->paused.store(false); p->timeline.running(p->clockPrimed, nowMs());
        p->observedAudioAtMs = nowMs();
    }
    p->controlChanged.notify_all();
}
extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativePause(JNIEnv *, jobject, jlong handle) {
    Player *p = fromHandle(handle); if (!p) return;
    {
        std::lock_guard<std::mutex> lock(p->controlMutex);
        p->paused.store(true); p->timeline.running(false, nowMs());
    }
    p->controlChanged.notify_all();
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeIsPlaying(JNIEnv *, jobject, jlong handle) {
    Player *p = fromHandle(handle);
    return p && p->started.load() && !p->paused.load() && !p->finished.load() && !p->abort.load();
}
extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeSeek(JNIEnv *, jobject, jlong handle, jlong position) {
    Player *p = fromHandle(handle); if (p) requestSeek(p, position, true);
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeGetPosition(JNIEnv *, jobject, jlong handle) {
    Player *p = fromHandle(handle); return p ? p->positionMs.load() : 0;
}
extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeSetSpeed(JNIEnv *, jobject, jlong handle, jfloat speed) {
    Player *p = fromHandle(handle); if (!p || !std::isfinite(speed)) return;
    {
        std::lock_guard<std::mutex> lock(p->controlMutex);
        p->speed.store(std::max(.25f, std::min(4.f, speed)));
        p->timeline.rate(p->speed.load(), nowMs());
    }
    p->controlChanged.notify_all();
}
extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeAbort(JNIEnv *, jobject, jlong handle) {
    Player *p = fromHandle(handle); if (p) stop(p);
}
extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeRelease(JNIEnv *env, jobject, jlong handle) {
    Player *p = fromHandle(handle); if (!p) return;
    stop(p);
    if (p->worker.joinable()) p->worker.join();
    setWindow(p, nullptr);
    env->DeleteGlobalRef(p->owner);
    delete p;
}

extern "C" JNIEXPORT void JNICALL
Java_com_will_mvvmbase_core_media_internal_FfmpegEngine_nativeSetLooping(JNIEnv *, jobject, jlong handle, jboolean looping) {
    Player *p = fromHandle(handle); if (p) p->looping.store(looping);
}
