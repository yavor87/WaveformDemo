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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

import java.util.Arrays;

public class StaticWaveformView extends WaveformView implements SurfaceHolder.Callback, PlaybackListener {
    public StaticWaveformView(Context context) {
        this(context, null, 0);
    }

    public StaticWaveformView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StaticWaveformView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray array = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.StaticWaveformView, 0, 0);

        mHolder = this.getHolder();
        mHolder.addCallback(this);

        mFillPaint = new Paint();
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setAntiAlias(true);
        mFillPaint.setColor(array.getColor(R.styleable.StaticWaveformView_waveformFillColor,
                ContextCompat.getColor(context, R.color.default_waveformFill)));

        mMarkerPaint = new Paint();
        mMarkerPaint.setStyle(Paint.Style.STROKE);
        mMarkerPaint.setStrokeWidth(0);
        mMarkerPaint.setAntiAlias(true);
        mMarkerPaint.setColor(array.getColor(R.styleable.StaticWaveformView_playbackIndicatorColor,
                ContextCompat.getColor(context, R.color.default_playback_indicator)));

        mTimecodePaint = new Paint();
        mTimecodePaint.setTextSize(getResources().getDimension(R.dimen.timecode_text_size));
        mTimecodePaint.setAntiAlias(true);
        mTimecodePaint.setColor(array.getColor(R.styleable.StaticWaveformView_timecodeColor,
                ContextCompat.getColor(context, R.color.default_timecode)));
    }

    private short[] mAudioData;
    private SurfaceHolder mHolder;
    private int mAudioLength, mAudioProgress;
    private Paint mFillPaint, mMarkerPaint, mTimecodePaint;
    private Picture mCache;

    /**
     * Sets the audio samples buffer
     * @param buffer audio samples
     */
    @Override
    public synchronized void updateAudioData(short[] buffer) {
        mAudioData = buffer;
        mAudioLength = buffer.length;
        updateDisplay(mHolder);
    }

    /**
     * Sets the audio file length
     * @param length Audio file length in milliseconds
     */
    public void setAudioLength(int length) {
        mAudioLength = length;
    }

    @Override
    public void onProgress(int progress) {
        setAudioProgress(progress);
    }

    @Override
    public void onCompletion() {
        setAudioProgress(mAudioLength);
    }

    /**
     * Updates audio playback progress
     * @param progress in milliseconds
     */
    public void setAudioProgress(int progress) {
        mAudioProgress = progress;
        Canvas canvas = mHolder.lockCanvas();
        if (canvas != null) {
            if (mCache != null) {
                canvas.drawPicture(mCache);
            }
            drawMarker(canvas);
            mHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void updateDisplay(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();
        if (canvas != null) {
            mCache = createWaveform();
            canvas.drawPicture(mCache);
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private Picture createWaveform() {
        Picture cache = new Picture();
        int width = getWidth();
        int height = getHeight();

        Canvas cacheCanvas = cache.beginRecording(width, height);

        // Clear the screen each time because SurfaceView won't do this for us.
        cacheCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        Path mWaveform = getWaveform(width, height, mAudioData);
        cacheCanvas.drawPath(mWaveform, mFillPaint);
        getStrokePaint().setStrokeWidth(3);
        cacheCanvas.drawPath(mWaveform, getStrokePaint());
        drawAxis(cacheCanvas, width);

        cache.endRecording();
        return cache;
    }

    Path getWaveform(int width, int height, short[] buffer) {
        Path waveformPath = new Path();
        float centerY = height / 2f;
        int pointIndex = 0;
        float max = Short.MAX_VALUE;

        short[][] extremes = getExtremes(buffer, width);

        // draw maximums
        for (int x = 0; x < width; x++) {
            short sample = extremes[x][0];
            float y = centerY - ((sample / max) * centerY);
            waveformPath.lineTo(x, y);
        }

        // draw minimums
        for (int x = width - 1; x >= 0; x--) {
            short sample = extremes[x][1];
            float y = centerY - ((sample / max) * centerY);
            waveformPath.lineTo(x, y);
        }

        return waveformPath;
    }

    private void drawMarker(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        float xStep = width / (mAudioLength * 1.0f);
        if (mAudioProgress < mAudioLength) {
            canvas.drawLine(xStep * mAudioProgress, 0, xStep * mAudioProgress, height, mMarkerPaint);
        }
    }

    private void drawAxis(Canvas canvas, int width) {
        int seconds = mAudioLength / 1000;
        float xStep = width / (mAudioLength / 1000f);
        float textHeight = mTimecodePaint.getTextSize();
        float textWidth = mTimecodePaint.measureText("10.00");
        int secondStep = (int)(textWidth * seconds * 2) / width;
        secondStep = Math.max(secondStep, 1);
        for (float i = 0; i <= seconds; i += secondStep) {
            canvas.drawText(String.format("%.2f", i), i * xStep, textHeight, mTimecodePaint);
        }
    }

    short[][] getExtremes(short[] data, int size) {
        short[][] newData = new short[size][];
        int groupSize = data.length / size;

        for (int i = 0; i < size; i++) {
            short[] group = Arrays.copyOfRange(data, i * groupSize,
                    Math.min((i + 1) * groupSize, data.length));

            // Fin min & max values
            short min = Short.MAX_VALUE, max = Short.MIN_VALUE;
            for (short a : group) {
                min = (short) Math.min(min, a);
                max = (short) Math.max(max, a);
            }
            newData[i] = new short[] { max, min };
        }

        return newData;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mAudioData != null) {
            updateDisplay(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }
}
