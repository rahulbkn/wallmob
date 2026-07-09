package com.wall.mob;

import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ViewPager2ConflictResolver {
    
    public static void attach(@NonNull RecyclerView recyclerView) {
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float startX = 0f;
            private float startY = 0f;
            private int touchSlop = -1;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (touchSlop < 0) {
                    touchSlop = ViewConfiguration.get(rv.getContext()).getScaledTouchSlop();
                }

                int action = e.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        startY = e.getY();
                        // Prevent parent from stealing the initial touch down down-stream
                        rv.getParent().requestDisallowInterceptTouchEvent(true);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(e.getX() - startX);
                        float dy = Math.abs(e.getY() - startY);

                        // Check if the user has moved their finger far enough to qualify as a scroll
                        if (dx > touchSlop || dy > touchSlop) {
                            if (dy > dx) {
                                // User is scrolling vertically! Release control so NestedScrollView can scroll up/down
                                rv.getParent().requestDisallowInterceptTouchEvent(false);
                            } else {
                                // User is scrolling horizontally! Keep holding control so ViewPager2 doesn't flip pages
                                rv.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        rv.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            }
        });
    }
}
