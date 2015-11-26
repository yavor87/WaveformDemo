package com.newventuresoftware.waveformdemo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.newventuresoftware.waveform.AudioDataReceivedListener;

/**
 * Created by Yavor on 26.11.2015 г..
 */
public class RecordingThread implements Runnable {
    private static final String LOG_TAG = RecordingThread.class.getSimpleName();
    private static final int SAMPLE_RATE = 44100;

    public RecordingThread(AudioDataReceivedListener listener) {
        mListener = listener;
    }

    private boolean mShouldContinue;
    private AudioDataReceivedListener mListener;

    @Override
    public void run() {
        mShouldContinue = true;

        Log.v(LOG_TAG, "Start");
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // buffer size in bytes
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }

        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }
        record.startRecording();

        Log.v(LOG_TAG, "Start recording");

        long shortsRead = 0;
        while (mShouldContinue) {
            int numberOfShort = record.read(audioBuffer, 0, bufferSize / 2);
            shortsRead += numberOfShort;

            // Notify waveform
            mListener.onAudioDataReceived(audioBuffer);
        }

        record.stop();
        record.release();

        Log.v(LOG_TAG, String.format("Recording stopped. Samples read: %d", shortsRead));
    }

    public void stop() {
        mShouldContinue = false;
    }
}
