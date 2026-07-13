package com.wall.mob;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

public class WrapContentFrameLayout extends FrameLayout {

    private ViewPager2 attachedPager;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;

    public WrapContentFrameLayout(@NonNull Context context) {
        super(context);
    }

    public WrapContentFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int resolvedHeightSpec = heightMeasureSpec;

        if (attachedPager != null) {
            View currentPage = findCurrentPageView(attachedPager);
            if (currentPage != null) {
                int childWidthSpec = MeasureSpec.makeMeasureSpec(
                        MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
                int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

                // Current page ko yahin, isi measure pass ke andar hi measure karo.
                // Isse ek hi pass mein sahi height mil jaati hai, koi async follow-up
                // requestLayout() ki zaroorat nahi padti.
                currentPage.measure(childWidthSpec, childHeightSpec);

                int measuredHeight = currentPage.getMeasuredHeight();
                if (measuredHeight > 0) {
                    resolvedHeightSpec = MeasureSpec.makeMeasureSpec(
                            measuredHeight + getPaddingTop() + getPaddingBottom(),
                            MeasureSpec.EXACTLY);
                }
            }
        }

        // IMPORTANT: super.onMeasure() ko kabhi skip mat karo. Pehle wala code cache-hit
        // hone par isse skip kar deta tha, jisse ViewPager2 ke andar ka RecyclerView kabhi
        // properly measure hi nahi hota tha us pass mein -> wo khud requestLayout() fire
        // karta -> hamara purana GlobalLayoutListener + debounce bhi apna requestLayout()
        // fire karta -> dono ek dusre ko baar baar retrigger karte, especially tab switch ke
        // time jab RecyclerView page rebind kar raha hota hai. Yahi infinite layout loop
        // (= freeze) ka asli cause tha. Ab child hamesha properly measure hota hai, so
        // RecyclerView/ViewPager2 ko apna internal requestLayout() fire karne ki zaroorat
        // hi nahi padti.
        super.onMeasure(widthMeasureSpec, resolvedHeightSpec);
    }

    @Nullable
    private View findCurrentPageView(ViewPager2 pager) {
        if (pager.getChildCount() == 0) return null;
        View firstChild = pager.getChildAt(0);
        if (!(firstChild instanceof RecyclerView)) return null;

        RecyclerView rv = (RecyclerView) firstChild;
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (lm == null) return null;

        return lm.findViewByPosition(pager.getCurrentItem());
    }

    public void attachToViewPager(ViewPager2 pager) {
        detachFromCurrentPager();
        attachedPager = pager;

        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // Naya tab select hote hi height dobara resolve karwane ke liye ek hi
                // requestLayout() kaafi hai -- baaki sab agla onMeasure() khud sambhal lega.
                requestLayout();
            }
        };
        pager.registerOnPageChangeCallback(pageChangeCallback);
    }

    private void detachFromCurrentPager() {
        if (attachedPager != null && pageChangeCallback != null) {
            attachedPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        attachedPager = null;
        pageChangeCallback = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        detachFromCurrentPager();
    }
}
