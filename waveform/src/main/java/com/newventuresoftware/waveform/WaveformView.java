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
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceView;

public abstract class WaveformView extends SurfaceView {

    // To make quieter sounds still show up well on the display, we use +/- 8192 as the amplitude
    // that reaches the top/bottom of the view instead of +/- 32767. Any samples that have
    // magnitude higher than this limit will simply be clipped during drawing.
    private static final float MAX_AMPLITUDE_TO_DRAW = 8192.0f;

    private final Paint mStrokePaint;

    public WaveformView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mStrokePaint = new Paint();
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(0);
        mStrokePaint.setAntiAlias(true);
    }

    public Paint getStrokePaint() {
        return mStrokePaint;
    }

    /**
     * Updates the waveform view with a new "frame" of samples and renders it. The new frame gets
     * added to the front of the rendering queue, pushing the previous frames back, causing them to
     * be faded out visually.
     *
     * @param buffer the most recent buffer of audio samples
     */
    public abstract void updateAudioData(short[] buffer);

    float[] getWaveform(int width, int height, short[] buffer) {
        float[] waveformPoints = new float[width * 4];
        float centerY = height / 2f;
        float lastX = -1;
        float lastY = -1;
        int pointIndex = 0;

        // For efficiency, we don't draw all of the samples in the buffer, but only the ones
        // that align with pixel boundaries.
        for (int x = 0; x < width; x++) {
            int index = (int) (((x * 1.0f) / width) * buffer.length);
            short sample = buffer[index];
            float y = ((sample / MAX_AMPLITUDE_TO_DRAW) * centerY) + centerY;

            if (lastX != -1) {
                waveformPoints[pointIndex++] = lastX;
                waveformPoints[pointIndex++] = lastY;
                waveformPoints[pointIndex++] = x;
                waveformPoints[pointIndex++] = y;
            }

            lastX = x;
            lastY = y;
        }

        return waveformPoints;
    }
}
