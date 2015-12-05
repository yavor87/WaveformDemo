/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.newventuresoftware.waveform;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;

import java.util.LinkedList;

public class RealtimeWaveformView extends WaveformView implements AudioDataReceivedListener {
    public RealtimeWaveformView(Context context) {
        this(context, null, 0);
    }

    public RealtimeWaveformView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RealtimeWaveformView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        getStrokePaint().setColor(getResources().getColor(R.color.waveform_realtime));

        mWaveformData = new LinkedList<>();
    }

    // The number of buffer frames to keep around (for a nice fade-out visualization).
    private static final int HISTORY_SIZE = 6;
    private static final int UPDATE_INTERVAL = 1000 / 25;

    // The queue that will hold historical audio data.
    private final LinkedList<float[]> mWaveformData;
    private long lastUpdate;
    private boolean mIsUpdating = false;

    /**
     * Sets the audio samples buffer
     * @param buffer audio samples
     */
    @Override
    public void updateAudioData(short[] buffer) {
        if (System.nanoTime() - lastUpdate < UPDATE_INTERVAL || mIsUpdating)
            return;

        synchronized (mWaveformData) {
            // We want to keep a small amount of history in the view to provide a nice fading effect.
            // We use a linked list that we treat as a queue for this.
            if (mWaveformData.size() == HISTORY_SIZE) {
                mWaveformData.removeFirst();
            }

            float[] waveformPoints = getWaveform(getWidth(), getHeight(), buffer);
            mWaveformData.addLast(waveformPoints);
        }

        // Update the display.
        post(new Runnable() {
            @Override
            public void run() {
                mIsUpdating = true;
                Canvas canvas = getHolder().lockCanvas();
                if (canvas != null) {
                    drawWaveform(canvas);
                    getHolder().unlockCanvasAndPost(canvas);
                }
                mIsUpdating = false;
            }
        });
        lastUpdate = System.nanoTime();
    }

    /**
     * Repaints the view's surface.
     *
     * @param canvas the {@link Canvas} object on which to draw
     */
    private void drawWaveform(Canvas canvas) {
        // Clear the screen each time because SurfaceView won't do this for us.
        canvas.drawColor(Color.BLACK);

        // We draw the history from oldest to newest so that the older audio data is further back
        // and darker than the most recent data.
        int colorDelta = 255 / (HISTORY_SIZE + 1);
        int brightness = colorDelta;

        Paint strokePaint = getStrokePaint();

        synchronized (mWaveformData) {
            for (float[] waveformPoints : mWaveformData) {
                strokePaint.setAlpha(brightness);
                canvas.drawLines(waveformPoints, strokePaint);
                brightness += colorDelta;
            }
        }
    }

    @Override
    public void onAudioDataReceived(short[] data) {
        updateAudioData(data);
    }
}
