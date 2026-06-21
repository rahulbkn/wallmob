package com.wall.mob;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * SimpleBlurLayout
 *
 * API 31+ → RenderEffect (GPU blur, auto-updates with content)
 * API 23–30 → Stack Blur on a Bitmap supplied via setSourceBitmap()
 *
 * Usage in Fragment/Activity:
 *
 *   heroCarousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
 *       @Override
 *       public void onPageSelected(int position) {
 *           Bitmap bmp = getCurrentHeroBitmap(position); // get from Glide target/cache
 *           simpleBlurLayout.setSourceBitmap(bmp);
 *       }
 *   });
 */
public class SimpleBlurLayout extends LinearLayout {

    private static final int   BLUR_RADIUS   = 6;    // Stack Blur radius on downscaled bitmap
    private static final int   COLOR_TOP     = Color.argb(0,   0, 0, 0);
    private static final int   COLOR_BOTTOM  = Color.argb(190, 0, 0, 0);

    private final boolean useRenderEffect =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S; // API 31+

    // API 23-30 fields
    private Bitmap sourceBitmap;   // original bitmap from caller
    private Bitmap blurBitmap;     // downscaled + blurred
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient scrimGradient;
    private int lastW, lastH;

    public SimpleBlurLayout(Context context) {
        super(context);
        init();
    }

    public SimpleBlurLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        setWillNotDraw(false);

        if (useRenderEffect) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
            // RenderEffect on self blurs this view's own background.
            // We'll set a semi-transparent black background so blur has content.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(
                        40f, 40f, Shader.TileMode.CLAMP));
            }
        } else {
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }
    }

    /**
     * Call this every time the hero image changes (from OnPageChangeCallback).
     * Pass the Bitmap that Glide loaded into the ImageView.
     */
    public void setSourceBitmap(Bitmap bitmap) {
        if (useRenderEffect) return; // RenderEffect doesn't need this

        this.sourceBitmap = bitmap;
        rebuildBlurBitmap();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == lastW && h == lastH) return;
        lastW = w;
        lastH = h;
        scrimGradient = new LinearGradient(
                0, 0, 0, h,
                COLOR_TOP, COLOR_BOTTOM,
                Shader.TileMode.CLAMP);
        scrimPaint.setShader(scrimGradient);

        if (!useRenderEffect && sourceBitmap != null) {
            rebuildBlurBitmap();
        }
    }

    private void rebuildBlurBitmap() {
        if (sourceBitmap == null || lastW == 0 || lastH == 0) return;

        // Recycle old
        if (blurBitmap != null && !blurBitmap.isRecycled()) {
            blurBitmap.recycle();
        }

        // Downscale 4x for speed
        int bw = Math.max(1, lastW / 4);
        int bh = Math.max(1, lastH / 4);

        // Scale source to match our view size first, then downscale
        Bitmap scaled = Bitmap.createScaledBitmap(sourceBitmap, bw, bh, true);
        blurBitmap = scaled.copy(Bitmap.Config.ARGB_8888, true);
        if (scaled != blurBitmap) scaled.recycle();

        stackBlur(blurBitmap, BLUR_RADIUS);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!useRenderEffect) {
            // Draw blurred bitmap
            if (blurBitmap != null && !blurBitmap.isRecycled()) {
                canvas.drawBitmap(blurBitmap,
                        null,
                        new android.graphics.RectF(0, 0, lastW, lastH),
                        bitmapPaint);
            }
            // Gradient scrim over blur
            if (scrimGradient != null) {
                canvas.drawRect(0, 0, lastW, lastH, scrimPaint);
            }
        } else {
            // API 31+: RenderEffect blurs this view itself.
            // Draw scrim manually (gradient doesn't get blurred, intentional)
            if (scrimGradient != null) {
                canvas.drawRect(0, 0, lastW, lastH, scrimPaint);
            }
        }

        // Children (TextViews) on top — sharp, not blurred
        super.dispatchDraw(canvas);
    }

    // ── Stack Blur (pure Java, Mario Klingemann) ─────────────────────────────
    private static void stackBlur(Bitmap bmp, int radius) {
        if (radius < 1) return;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] pix = new int[w * h];
        bmp.getPixels(pix, 0, w, 0, 0, w, h);
        int wm = w-1, hm = h-1, div = radius+radius+1;
        int[] r = new int[w*h], g = new int[w*h], b = new int[w*h];
        int rsum,gsum,bsum,x,y,i,p;
        int[] vmin = new int[Math.max(w,h)];
        int divsum=(div+1)>>1; divsum*=divsum;
        int[] dv = new int[256*divsum];
        for(i=0;i<256*divsum;i++) dv[i]=(i/divsum);
        int yw=0,yi=0;
        int[][] stack=new int[div][3];
        int stackpointer,stackstart,rbs;
        int[] sir;
        int routsum,goutsum,boutsum,rinsum,ginsum,binsum;
        for(y=0;y<h;y++){
            rinsum=ginsum=binsum=routsum=goutsum=boutsum=rsum=gsum=bsum=0;
            for(i=-radius;i<=radius;i++){
                p=pix[yi+Math.min(wm,Math.max(i,0))];
                sir=stack[i+radius];
                sir[0]=(p&0xff0000)>>16; sir[1]=(p&0x00ff00)>>8; sir[2]=(p&0x0000ff);
                rbs=radius+1-Math.abs(i);
                rsum+=sir[0]*rbs; gsum+=sir[1]*rbs; bsum+=sir[2]*rbs;
                if(i>0){rinsum+=sir[0];ginsum+=sir[1];binsum+=sir[2];}
                else   {routsum+=sir[0];goutsum+=sir[1];boutsum+=sir[2];}
            }
            stackpointer=radius;
            for(x=0;x<w;x++){
                r[yi]=dv[rsum]; g[yi]=dv[gsum]; b[yi]=dv[bsum];
                rsum-=routsum; gsum-=goutsum; bsum-=boutsum;
                stackstart=stackpointer-radius+div;
                sir=stack[stackstart%div];
                routsum-=sir[0]; goutsum-=sir[1]; boutsum-=sir[2];
                if(y==0) vmin[x]=Math.min(x+radius+1,wm);
                p=pix[yw+vmin[x]];
                sir[0]=(p&0xff0000)>>16; sir[1]=(p&0x00ff00)>>8; sir[2]=(p&0x0000ff);
                rinsum+=sir[0]; ginsum+=sir[1]; binsum+=sir[2];
                rsum+=rinsum; gsum+=ginsum; bsum+=binsum;
                stackpointer=(stackpointer+1)%div; sir=stack[stackpointer];
                routsum+=sir[0]; goutsum+=sir[1]; boutsum+=sir[2];
                rinsum-=sir[0]; ginsum-=sir[1]; binsum-=sir[2];
                yi++;
            }
            yw+=w;
        }
        for(x=0;x<w;x++){
            rinsum=ginsum=binsum=routsum=goutsum=boutsum=rsum=gsum=bsum=0;
            int yp=-radius*w;
            for(i=-radius;i<=radius;i++){
                yi=Math.max(0,yp)+x; sir=stack[i+radius];
                sir[0]=r[yi]; sir[1]=g[yi]; sir[2]=b[yi];
                rbs=radius+1-Math.abs(i);
                rsum+=r[yi]*rbs; gsum+=g[yi]*rbs; bsum+=b[yi]*rbs;
                if(i>0){rinsum+=sir[0];ginsum+=sir[1];binsum+=sir[2];}
                else   {routsum+=sir[0];goutsum+=sir[1];boutsum+=sir[2];}
                if(i<hm) yp+=w;
            }
            yi=x; stackpointer=radius;
            for(y=0;y<h;y++){
                pix[yi]=(0xff000000&pix[yi])|(dv[rsum]<<16)|(dv[gsum]<<8)|dv[bsum];
                rsum-=routsum; gsum-=goutsum; bsum-=boutsum;
                stackstart=stackpointer-radius+div;
                sir=stack[stackstart%div];
                routsum-=sir[0]; goutsum-=sir[1]; boutsum-=sir[2];
                if(x==0) vmin[y]=Math.min(y+radius+1,hm)*w;
                p=x+vmin[y];
                sir[0]=r[p]; sir[1]=g[p]; sir[2]=b[p];
                rinsum+=sir[0]; ginsum+=sir[1]; binsum+=sir[2];
                rsum+=rinsum; gsum+=ginsum; bsum+=binsum;
                stackpointer=(stackpointer+1)%div; sir=stack[stackpointer];
                routsum+=sir[0]; goutsum+=sir[1]; boutsum+=sir[2];
                rinsum-=sir[0]; ginsum-=sir[1]; binsum-=sir[2];
                yi+=w;
            }
        }
        bmp.setPixels(pix,0,w,0,0,w,h);
    }
}
