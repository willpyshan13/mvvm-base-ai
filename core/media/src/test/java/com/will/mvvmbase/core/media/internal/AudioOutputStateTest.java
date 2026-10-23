package com.will.mvvmbase.core.media.internal;

/** Host regression checks; run with java -ea. Requires no Android runtime. */
public final class AudioOutputStateTest {
    public static void main(String[] args) {
        AudioOutputState state = new AudioOutputState(48000);
        state.reset(0, 0, 0);
        state.written(48000 * 4, 0);
        assert !state.drained(47999);
        assert state.drained(48000);
        assert state.positionMs(24000) == 500;
        // Repeated automatic loops must only wait for the current epoch's samples.
        for (int epoch = 1; epoch <= 1000; epoch++) {
            state.reset(epoch, 0, 0);
            assert !state.accepts(epoch - 1);
            assert state.positionMs(0) == -1;
            state.written(48000 * 4, 0);
            assert state.drained(48000);
        }
        // Both devices which reset the head on flush and devices retaining a nonzero head.
        state.reset(1001, 5000, 90000);
        state.written(96000, 5000);
        assert state.positionMs(102000) == 5250;
        assert state.drained(114000);
        // Android exposes an unsigned 32-bit playback head.
        state.reset(1002, 0, 0xfffffff0L);
        state.written(32 * 4, 0);
        assert state.drained(16);
        state.ended(1001);
        assert state.positionMs(16) >= 0;
        state.ended(1002);
        assert state.positionMs(16) == -1;
        System.out.println("PASS: 1000 loops, seek accounting, head wraparound, stale epoch rejection");
    }
}
