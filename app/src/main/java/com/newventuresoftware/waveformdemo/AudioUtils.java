package com.newventuresoftware.waveformdemo;

public final class AudioUtils {
    public static final int calculateAudioLength(int samplesCount, int sampleRate, int channelCount) {
        return (int) ((samplesCount / channelCount) * 1000) / sampleRate;
    }
}
