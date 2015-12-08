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
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.newventuresoftware.waveform.utils.*;

import java.util.LinkedList;

/**
 * TODO: document your custom view class.
 */
public class WaveformView extends View {
    public static final int MODE_RECORDING = 1;
    public static final int MODE_PLAYBACK = 2;

    private static final int HISTORY_SIZE = 6;

    private TextPaint mTextPaint;
    private Paint mStrokePaint, mFillPaint, mMarkerPaint;

    // Used in draw
    private int width, height;
    private float xStep, centerY, x, y, lastX, lastY;
    private short sample;
    private int pointIndex;
    private float[] waveformPoints;

    private int mMode, mAudioLength, mMarkerPosition, mSampleRate, mChannels;
    private short[] mSamples;
    private LinkedList<short[]> mHistoricalData;
    private Picture mCachedWaveform;

    public WaveformView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.WaveformView, defStyle, 0);

        mMode = a.getInt(R.styleable.WaveformView_mode, MODE_PLAYBACK);

        int mStrokeColor = a.getColor(R.styleable.WaveformView_waveformColor,
                ContextCompat.getColor(context, R.color.default_waveform));
        int mFillColor = a.getColor(R.styleable.WaveformView_waveformFillColor,
                ContextCompat.getColor(context, R.color.default_waveformFill));
        int mMarkerColor = a.getColor(R.styleable.WaveformView_playbackIndicatorColor,
                ContextCompat.getColor(context, R.color.default_playback_indicator));
        int mTextColor = a.getColor(R.styleable.WaveformView_timecodeColor,
                ContextCompat.getColor(context, R.color.default_timecode));

        a.recycle();

        mTextPaint = new TextPaint();
        mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setColor(mTextColor);

        mStrokePaint = new Paint();
        mStrokePaint.setColor(mStrokeColor);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(1);
        mStrokePaint.setAntiAlias(true);

        mFillPaint = new Paint();
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setAntiAlias(true);
        mFillPaint.setColor(mFillColor);

        mMarkerPaint = new Paint();
        mMarkerPaint.setStyle(Paint.Style.STROKE);
        mMarkerPaint.setStrokeWidth(0);
        mMarkerPaint.setAntiAlias(true);
        mMarkerPaint.setColor(mMarkerColor);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        width = getMeasuredWidth();
        height = getMeasuredHeight();
        xStep = width / (mAudioLength * 1.0f);
        centerY = height / 2f;
        if (mMode == MODE_RECORDING) {
            waveformPoints = new float[width * 4];
        } else if (mMode == MODE_PLAYBACK) {
            createPlaybackWaveform();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mMode == MODE_RECORDING && mHistoricalData != null) {
            for (short[] p : mHistoricalData) {
                canvas.drawLines(drawRecordingWaveform(p), mStrokePaint);
            }
        } else if (mMode == MODE_PLAYBACK && mCachedWaveform != null) {
            canvas.drawPicture(mCachedWaveform);
            if (mMarkerPosition > -1 && mMarkerPosition < mAudioLength)
                canvas.drawLine(xStep * mMarkerPosition, 0, xStep * mMarkerPosition, height, mMarkerPaint);
        }
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int mMode) {
        mMode = mMode;
    }

    public short[] getSamples() {
        return mSamples;
    }

    public void setSamples(short[] samples) {
        mSamples = samples;
        calculateAudioLength();
        onSamplesChanged();
    }

    public int getMarkerPosition() {
        return mMarkerPosition;
    }

    public void setMarkerPosition(int markerPosition) {
        mMarkerPosition = markerPosition;
        postInvalidate();
    }

    public int getAudioLength() {
        return mAudioLength;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public void setSampleRate(int sampleRate) {
        mSampleRate = sampleRate;
        calculateAudioLength();
    }

    public int getChannels() {
        return mChannels;
    }

    public void setChannels(int channels) {
        mChannels = channels;
        calculateAudioLength();
    }

    private void calculateAudioLength() {
        if (mSamples == null || mSampleRate == 0 || mChannels == 0)
            return;

        mAudioLength = AudioUtils.calculateAudioLength(mSamples.length, mSampleRate, mChannels);
    }

    private void onSamplesChanged() {
        if (mMode == MODE_RECORDING) {
            if (mHistoricalData == null)
                mHistoricalData = new LinkedList<>();
            if (mHistoricalData.size() == HISTORY_SIZE)
                mHistoricalData.removeFirst();

            mHistoricalData.addLast(mSamples);
        } else if (mMode == MODE_PLAYBACK) {
            mMarkerPosition = -1;
            createPlaybackWaveform();
        }
    }

    float[] drawRecordingWaveform(short[] buffer) {
        lastX = -1;
        lastY = -1;
        pointIndex = 0;

        // For efficiency, we don't draw all of the samples in the buffer, but only the ones
        // that align with pixel boundaries.
        for (x = 0; x < width; x++) {
            sample = buffer[(int) ((x / width) * buffer.length)];
            y = centerY - ((sample / Short.MAX_VALUE) * centerY);

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

    Path drawPlaybackWaveform(int width, int height, short[] buffer) {
        Path waveformPath = new Path();
        float centerY = height / 2f;
        float max = Short.MAX_VALUE;

        short[][] extremes = SamplingUtils.getExtremes(buffer, width);

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

    private void createPlaybackWaveform() {
        if (width <= 0 || height <= 0 || mSamples == null)
            return;

        mCachedWaveform = new Picture();
        Canvas cacheCanvas = mCachedWaveform.beginRecording(width, height);

        Path mWaveform = drawPlaybackWaveform(width, height, mSamples);
        cacheCanvas.drawPath(mWaveform, mFillPaint);
        cacheCanvas.drawPath(mWaveform, mStrokePaint);
        drawAxis(cacheCanvas, width);

        mCachedWaveform.endRecording();
    }

    private void drawAxis(Canvas canvas, int width) {
        int seconds = mAudioLength / 1000;
        float xStep = width / (mAudioLength / 1000f);
        float textHeight = mTextPaint.getTextSize();
        float textWidth = mTextPaint.measureText("10.00");
        int secondStep = (int)(textWidth * seconds * 2) / width;
        secondStep = Math.max(secondStep, 1);
        for (float i = 0; i <= seconds; i += secondStep) {
            canvas.drawText(String.format("%.2f", i), i * xStep, textHeight, mTextPaint);
        }
    }
}
