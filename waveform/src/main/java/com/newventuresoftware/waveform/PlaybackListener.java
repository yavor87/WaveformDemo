package com.newventuresoftware.waveform;

public interface PlaybackListener {
    void onProgress(int progress);
    void onCompletion();
}
