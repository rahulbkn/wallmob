package com.wall.mob;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;

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
    protected boolean setFrame(int l, int t, int r, int b) {
        boolean changed = super.setFrame(l, t, r, b);
        if (changed) {
            applyCenterCrop();
        }
        return changed;
    }

    private void applyCenterCrop() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();

        float scale;
        float dx = 0, dy = 0;

        if (drawableWidth * viewHeight > viewWidth * drawableHeight) {
            scale = viewHeight / drawableHeight;
            dx = (viewWidth - drawableWidth * scale) * 0.5f;
        } else {
            scale = viewWidth / drawableWidth;
            dy = (viewHeight - drawableHeight * scale) * 0.5f;
        }

        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
        minScale = scale;
    }

    private RectF getImageBounds() {
        Drawable drawable = getDrawable();
        if (drawable == null) return new RectF();
        RectF bounds = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(bounds);
        bounds.sort();
        return bounds;
    }

    private void clampTranslation() {
        RectF bounds = getImageBounds();
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float dx = 0, dy = 0;

        if (bounds.width() <= viewWidth) {
            dx = (viewWidth - bounds.width()) * 0.5f - bounds.left;
        } else {
            if (bounds.left > 0) dx = -bounds.left;
            else if (bounds.right < viewWidth) dx = viewWidth - bounds.right;
        }

        if (bounds.height() <= viewHeight) {
            dy = (viewHeight - bounds.height()) * 0.5f - bounds.top;
        } else {
            if (bounds.top > 0) dy = -bounds.top;
            else if (bounds.bottom < viewHeight) dy = viewHeight - bounds.bottom;
        }

        if (dx != 0 || dy != 0) {
            matrix.postTranslate(dx, dy);
            setImageMatrix(matrix);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                last.set(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    float dx = event.getX() - last.x;
                    float dy = event.getY() - last.y;
                    if (getCurrentScale() > minScale) {
                        matrix.postTranslate(dx, dy);
                        setImageMatrix(matrix);
                    }
                    last.set(event.getX(), event.getY());
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
            isZoomed = getCurrentScale() > minScale * 1.1f;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            clampTranslation();
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (isZoomed) {
                applyCenterCrop();
                isZoomed = false;
            } else {
                float targetScale = Math.min(minScale * 2.5f, maxScale);
                float scale = targetScale / getCurrentScale();
                matrix.postScale(scale, scale, e.getX(), e.getY());
                setImageMatrix(matrix);
                isZoomed = true;
                clampTranslation();
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
        float currentScale = getCurrentScale();
        if (currentScale < minScale) {
            applyCenterCrop();
        } else {
            clampTranslation();
        }
    }

    public void resetZoom() {
        applyCenterCrop();
        isZoomed = false;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::resetZoom);
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        post(this::resetZoom);
    }
}
