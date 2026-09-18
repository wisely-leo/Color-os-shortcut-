package android.view;

import android.content.Context;
import android.graphics.RenderEffect;

public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;

    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View v);
        void onViewDetachedFromWindow(View v);
    }

    public View(Context c) {}

    public void setRenderEffect(RenderEffect e) {}
    public RenderEffect getRenderEffect() { return null; }
    public int getVisibility() { return 0; }
    public void setVisibility(int v) {}
    public Context getContext() { return null; }
    public ViewParent getParent() { return null; }
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener l) {}
    public void setAlpha(float a) {}
    public float getAlpha() { return 0f; }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public void addOnLayoutChangeListener(Object l) {}
    public void requestLayout() {}
    public void setTag(Object t) {}
    public Object getTag() { return null; }
    public void setBackgroundColor(int c) {}
    public void setClickable(boolean b) {}
    public void setFocusable(boolean b) {}
    public void setImportantForAccessibility(int m) {}
    public void postOnAnimation(Runnable r) {}
    public void postInvalidateOnAnimation() {}
}
