package com.will.mvvmbase.core.media.internal;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;




import java.io.IOException;

/** FFmpeg JNI bridge ported from mvvm-will-base. Each prepare owns its native session. */
public final class FfmpegEngine {
    public interface Listener {
        void onPrepared(int width, int height, long durationMs);
        void onRenderingStart();
        void onSeekComplete();
        void onCompletion();
        void onError(int code, String message);
    }
    public interface SourceResolver {
        String resolve(Context context, String source) throws IOException;
    }
    private final Context context;
    private final Listener listener;
    private volatile Surface outputSurface;
    static {
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
        System.loadLibrary("ffmpeg_player");
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Session session;
    private volatile float speed = 1f;
    private volatile float leftVolume = 1f;
    private volatile float rightVolume = 1f;

    public FfmpegEngine(Context context, Listener listener) { this.context = context.getApplicationContext(); this.listener = listener; }

    public synchronized void prepare(String source, boolean looping, SourceResolver resolver) {
        release();
        Session next = new Session();
        session = next;
        String headers = "";
        next.mediaHandler.post(() -> {
            try {
                if (!next.active()) return;
                String resolved = resolver.resolve(context, source);
                synchronized (next.handleLock) {
                    if (!next.active()) return;
                    next.handle = nativeCreate(next);
                    if (next.handle == 0) throw new IllegalStateException("Native player creation failed");
                    nativeSetSpeed(next.handle, speed);
                    if (outputSurface != null) nativeSetSurface(next.handle, outputSurface);
                    nativePrepare(next.handle, resolved, headers, looping);
                }
            } catch (IOException | RuntimeException e) {
                next.onNativeError(-1, e.toString());
            }
        });
    }

    public void start() {
        Session current = session;
        if (current == null) return;
        current.mediaHandler.post(() -> {
            synchronized (current.handleLock) {
                if (!current.active() || current.handle == 0) return;
                try {
                    synchronized (current.audioLock) {
                        if (current.track != null) current.track.play();
                        current.playing = true;
                    }
                    nativeStart(current.handle);
                } catch (IllegalStateException e) {
                    current.onNativeError(-1, e.toString());
                }
            }
        });
    }

    public void pause() {
        Session current = session;
        if (current == null) return;
        current.mediaHandler.post(() -> {
            synchronized (current.handleLock) {
                if (!current.active() || current.handle == 0) return;
                nativePause(current.handle);
                synchronized (current.audioLock) {
                    current.playing = false;
                    if (current.track != null) current.track.pause();
                }
            }
        });
    }

    public boolean isPlaying() {
        Session current = session;
        if (current == null) return false;
        synchronized (current.handleLock) {
            return current.active() && current.handle != 0 && nativeIsPlaying(current.handle);
        }
    }

    public void seekTo(long time) {
        Session current = session;
        if (current == null) return;
        current.mediaHandler.post(() -> {
            synchronized (current.handleLock) {
                if (current.active() && current.handle != 0) nativeSeek(current.handle, Math.max(0, time));
            }
        });
    }

    public synchronized void release() {
        Session old = session;
        session = null;
        if (old == null) return;
        old.released = true;
        // Abort immediately; a pending source preparation must also observe cancellation.
        synchronized (old.handleLock) {
            if (old.handle != 0) nativeAbort(old.handle);
        }
        old.mediaHandler.post(() -> {
            long handle;
            synchronized (old.handleLock) {
                handle = old.handle;
                old.handle = 0;
            }
            // No Java monitor is held across join: native callbacks can finish freely.
            if (handle != 0) nativeRelease(handle);
            synchronized (old.audioLock) {
                if (old.track != null) {
                    old.track.release();
                    old.track = null;
                }
            }
            old.thread.quitSafely();
        });
    }

    public long getCurrentPosition() {
        Session current = session;
        if (current == null) return 0;
        long clock = current.onNativeAudioClockMs();
        if (clock >= 0) return clock;
        synchronized (current.handleLock) {
            return current.active() && current.handle != 0 ? nativeGetPosition(current.handle) : 0;
        }
    }

    public long getDuration() { Session current = session; return current == null ? 0 : current.duration; }

    public void setVolume(float left, float right) {
        leftVolume = left;
        rightVolume = right;
        Session current = session;
        if (current != null) synchronized (current.audioLock) {
            if (current.track != null) current.track.setStereoVolume(left, right);
        }
    }

    public void setSpeed(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return;
        Session current = session;
        float requested = Math.max(0.25f, Math.min(4f, value));
        if (current == null) { speed = requested; return; }
        current.mediaHandler.post(() -> {
            synchronized (current.handleLock) {
                if (!current.active()) return;
                try {
                    synchronized (current.audioLock) {
                        if (current.track != null) {
                            current.track.setPlaybackParams(new PlaybackParams().setSpeed(requested).setPitch(1f));
                            if (!current.playing) current.track.pause();
                        }
                    }
                    speed = requested;
                    if (current.handle != 0) nativeSetSpeed(current.handle, requested);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    android.util.Log.w("FfmpegPlayer", "Device rejected playback speed " + requested, e);
                }
            }
        });
    }

    public synchronized void setSurface(Surface surface) {
        Surface previous = outputSurface;
        outputSurface = surface;
        Session current = session;
        if (current != null) current.mediaHandler.post(() -> {
            synchronized (current.handleLock) {
                if (current.active() && current.handle != 0) nativeSetSurface(current.handle, outputSurface);
            }
        });
        if (previous != null && previous != surface) previous.release();
    }

    public void setLooping(boolean looping) {
        Session current = session;
        if (current != null) current.mediaHandler.post(() -> {
            synchronized (current.handleLock) {
                if (current.active() && current.handle != 0) nativeSetLooping(current.handle, looping);
            }
        });
    }

    public void close() { release(); setSurface(null); }

    /** JNI owns a global reference to this callback target until its worker threads exit. */
    private final class Session {
        final Object handleLock = new Object();
        final Object audioLock = new Object();
        final HandlerThread thread = new HandlerThread("App-FFmpeg");
        final Handler mediaHandler;
        volatile boolean released;
        volatile boolean playing;
        volatile long duration;
        volatile long requestedEpoch;
        long handle;
        AudioTrack track;
        final AudioOutputState audioState = new AudioOutputState(48000);

        Session() { thread.start(); mediaHandler = new Handler(thread.getLooper()); }
        boolean active() { return !released && session == this; }
        void post(Runnable event) { mainHandler.post(() -> { if (active()) event.run(); }); }

        private boolean onNativePrepared(int width, int height, int sampleRate, int channels, long durationMs) {
            if (!active()) return false;
            duration = Math.max(0, durationMs);
            try {
                synchronized (audioLock) {
                    if (!active()) return false;
                    if (channels > 0) {
                        int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO,
                                AudioFormat.ENCODING_PCM_16BIT);
                        if (min <= 0) throw new IllegalStateException("Unsupported PCM audio output");
                        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
                        AudioFormat format = new AudioFormat.Builder().setSampleRate(sampleRate)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build();
                        track = new AudioTrack(attributes, format, Math.max(min, sampleRate * 4 / 10),
                                AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                        if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("AudioTrack initialization failed");
                        track.setStereoVolume(leftVolume, rightVolume);
                        track.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1f));
                        track.pause();
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                onNativeError(-1, e.toString());
                return false;
            }
            post(() -> {
                listener.onPrepared(width, height, duration);
            });
            return true;
        }

        private void onNativeRenderingStart() {
            post(() -> listener.onRenderingStart());
        }

        private void onNativeSeekBegin(long epoch) {
            synchronized (audioLock) { requestedEpoch = Math.max(requestedEpoch, epoch); }
        }

        private boolean onNativeAudioReset(long epoch, long targetMs) {
            synchronized (audioLock) {
                if (!active() || epoch != requestedEpoch) return false;
                if (track != null) {
                    track.pause();
                    track.flush();
                    audioState.reset(epoch, targetMs, track.getPlaybackHeadPosition());
                    if (playing) track.play();
                } else audioState.reset(epoch, targetMs, 0);
                return true;
            }
        }

        private boolean onNativeAudioData(byte[] pcm, long epoch, long ptsMs) {
            int offset = 0;
            long lastProgress = android.os.SystemClock.elapsedRealtime();
            while (offset < pcm.length && active() && epoch == requestedEpoch) {
                try {
                    int written = 0;
                    synchronized (audioLock) {
                        if (!active() || track == null || !audioState.accepts(epoch) || epoch != requestedEpoch) return false;
                        if (playing) {
                            written = track.write(pcm, offset, pcm.length - offset, AudioTrack.WRITE_NON_BLOCKING);
                            if (written < 0) { onNativeError(written, "AudioTrack write failed"); return false; }
                            audioState.written(written, ptsMs);
                        } else lastProgress = android.os.SystemClock.elapsedRealtime();
                    }
                    if (written > 0) { offset += written; lastProgress = android.os.SystemClock.elapsedRealtime(); }
                    else {
                        if (android.os.SystemClock.elapsedRealtime() - lastProgress > 5000) {
                            onNativeError(-1, "Audio output stalled");
                            return false;
                        }
                        Thread.sleep(4);
                    }
                } catch (IllegalStateException | InterruptedException e) { return false; }
            }
            return offset == pcm.length;
        }

        private boolean onNativeAudioDrain(long epoch) {
            long lastHead = -1;
            long lastProgress = android.os.SystemClock.elapsedRealtime();
            while (active() && epoch == requestedEpoch) {
                synchronized (audioLock) {
                    if (track == null || !audioState.accepts(epoch)) return false;
                    long head = track.getPlaybackHeadPosition() & 0xffffffffL;
                    if (audioState.drained(head)) return true;
                    if (!playing || head != lastHead) lastProgress = android.os.SystemClock.elapsedRealtime();
                    lastHead = head;
                }
                if (android.os.SystemClock.elapsedRealtime() - lastProgress > 5000) {
                    onNativeError(-1, "Audio output did not drain");
                    return false;
                }
                try { Thread.sleep(5); } catch (InterruptedException e) { return false; }
            }
            return false;
        }

        private long onNativeAudioClockMs() {
            synchronized (audioLock) {
                if (!active() || track == null || !audioState.accepts(requestedEpoch)) return -1;
                return audioState.positionMs(track.getPlaybackHeadPosition());
            }
        }

        private void onNativeAudioEnded(long epoch) {
            synchronized (audioLock) { audioState.ended(epoch); }
        }

        private void onNativeSeekComplete(long epoch, long targetMs) {
            if (epoch == requestedEpoch) post(() -> {
                if (epoch == requestedEpoch) listener.onSeekComplete();
            });
        }
        private void onNativeCompletion() { post(() -> listener.onCompletion()); }
        private void onNativeError(int code, String message) {
            android.util.Log.e("FfmpegPlayer", "FFmpeg error " + code + ": " + message);
            post(() -> listener.onError(code, message));
        }
    }

    private native long nativeCreate(Session callbacks);
    private native void nativePrepare(long handle, String source, String headers, boolean loop);
    private native void nativeSetSurface(long handle, Surface surface);
    private native void nativeStart(long handle);
    private native void nativePause(long handle);
    private native boolean nativeIsPlaying(long handle);
    private native void nativeSeek(long handle, long positionMs);
    private native long nativeGetPosition(long handle);
    private native void nativeSetSpeed(long handle, float speed);
    private native void nativeSetLooping(long handle, boolean looping);
    private native void nativeAbort(long handle);
    private native void nativeRelease(long handle);
}
