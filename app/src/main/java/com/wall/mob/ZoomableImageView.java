package com.wall.mob;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;
import android.widget.ImageView;

public class ZoomableImageView extends androidx.appcompat.widget.AppCompatImageView {

    private Matrix matrix;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float minScale = 1f;
    private float maxScale = 5f;
    private float[] m;
    private PointF last = new PointF();
    private boolean isZoomed = false;

    private OnDoubleTapListener onDoubleTapListener;

    public interface OnDoubleTapListener {
        void onDoubleTap();
    }

    public void setOnDoubleTapListener(OnDoubleTapListener listener) {
        this.onDoubleTapListener = listener;
    }

    public ZoomableImageView(Context context) {
        super(context);
        init();
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        matrix = new Matrix();
        m = new float[9];
        setScaleType(ScaleType.MATRIX);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) return true;
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                last.set(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    float dx = event.getX() - last.x;
                    float dy = event.getY() - last.y;
                    matrix.postTranslate(dx, dy);
                    last.set(event.getX(), event.getY());
                    setImageMatrix(matrix);
                }
                return true;
            case MotionEvent.ACTION_UP:
                clampToBounds();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            float currentScale = getCurrentScale();
            if (currentScale * scale < minScale) scale = minScale / currentScale;
            if (currentScale * scale > maxScale) scale = maxScale / currentScale;
            matrix.postScale(scale, scale, detector.getFocusX(), detector.getFocusY());
            setImageMatrix(matrix);
            isZoomed = getCurrentScale() > 1.1f;
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (isZoomed) {
                matrix.reset();
                setImageMatrix(matrix);
                isZoomed = false;
            } else {
                matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                setImageMatrix(matrix);
                isZoomed = true;
            }
            if (onDoubleTapListener != null) onDoubleTapListener.onDoubleTap();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            performClick();
            return false;
        }
    }

    private float getCurrentScale() {
        matrix.getValues(m);
        return m[Matrix.MSCALE_X];
    }

    private void clampToBounds() {
        matrix.getValues(m);
        float currentScale = m[Matrix.MSCALE_X];
        if (currentScale < minScale) {
            float scale = minScale / currentScale;
            matrix.postScale(scale, scale, getWidth() / 2f, getHeight() / 2f);
            setImageMatrix(matrix);
        }
    }

    public void resetZoom() {
        matrix.reset();
        setImageMatrix(matrix);
        isZoomed = false;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        resetZoom();
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        resetZoom();
    }
}
