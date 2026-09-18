package android.view;

import android.content.Context;

public class ViewGroup extends View {
    public ViewGroup(Context c) { super(c); }

    public void addView(View v, int idx) {}
    public void addView(View v) {}
    public void addView(View v, LayoutParams lp) {}
    public void addView(View v, int idx, LayoutParams lp) {}
    public void removeView(View v) {}
    public void bringToFront() {}
    public int getChildCount() { return 0; }
    public View getChildAt(int i) { return null; }
    public int indexOfChild(View v) { return -1; }

    public static class LayoutParams {
        public int width = -1, height = -1;
        public LayoutParams() {}
        public LayoutParams(int w, int h) { width = w; height = h; }
    }

    public static class MarginLayoutParams extends LayoutParams {}
}