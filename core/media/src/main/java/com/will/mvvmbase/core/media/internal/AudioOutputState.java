package com.will.mvvmbase.core.media.internal;

/** PCM accounting for one seek/loop epoch. Caller holds the session audio lock. */
final class AudioOutputState {
    private final int sampleRate;
    private long epoch = -1;
    private long baseMs;
    private long headBase;
    private long writtenBytes;
    private boolean ended;

    AudioOutputState(int sampleRate) { this.sampleRate = sampleRate; }

    void reset(long epoch, long targetMs, long playbackHead) {
        this.epoch = epoch;
        baseMs = targetMs;
        headBase = playbackHead & 0xffffffffL;
        writtenBytes = 0;
        ended = false;
    }

    boolean accepts(long epoch) { return this.epoch == epoch; }

    void written(int bytes, long ptsMs) {
        if (bytes <= 0) return;
        if (writtenBytes == 0) baseMs = ptsMs;
        writtenBytes += bytes;
    }

    private long playedFrames(long playbackHead) {
        return ((playbackHead & 0xffffffffL) - headBase) & 0xffffffffL;
    }

    boolean drained(long playbackHead) { return playedFrames(playbackHead) >= writtenBytes / 4; }

    long positionMs(long playbackHead) {
        if (ended || writtenBytes == 0) return -1;
        return baseMs + Math.min(playedFrames(playbackHead), writtenBytes / 4) * 1000 / sampleRate;
    }

    void ended(long epoch) { if (accepts(epoch)) ended = true; }
}
