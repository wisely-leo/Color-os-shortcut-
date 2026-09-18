package com.shortcutblur;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class ShortcutBlurModule extends XposedModule {

    private static final String CLS_POPUP_BLUR_VIEW = "com.android.launcher3.popup.PopupBlurView";

    private static final String CLS_OPLUS_POPUP = "com.android.launcher3.popup.OplusPopupContainerWithArrow";
    private static final String CLS_ARROW_POPUP = "com.android.launcher3.popup.ArrowPopup";
    private static final String M_GET_POP_BLUR_VIEW = "getPopBlurView";

    private ClassLoader cl;
    private boolean installed = false;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        try {
        } catch (Throwable ignored) {}
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        try {
            if (param == null || installed) return;
            String pkg = param.getPackageName();
            SBLog.i("onPackageReady pkg=" + pkg);
            if (!isTargetLauncher(pkg)) return;
            ClassLoader loader = param.getClassLoader();
            if (loader == null) return;
            this.cl = loader;
            installHooks(loader);
            installed = true;
        } catch (Throwable t) {
            SBLog.e("READY", "onPackageReady failed", t);
        }
    }

    private static boolean isTargetLauncher(String p) {
        return "com.android.launcher".equals(p)
                || "com.oplus.launcher".equals(p)
                || "com.coloros.launcher".equals(p);
    }

    private void installHooks(ClassLoader loader) {
        try {
            int nFinish;
            int nAnim = 0;
            Class<?> cls = Class.forName(CLS_POPUP_BLUR_VIEW, false, loader);
            int nReturn = hookReturnView(cls, M_GET_POP_BLUR_VIEW, "pbv");
            try {
                Class<?> comp = Class.forName(CLS_POPUP_BLUR_VIEW + "$Companion", false, loader);
                nReturn += hookReturnView(comp, M_GET_POP_BLUR_VIEW, "pbv_companion");
            } catch (Throwable ignore) {}

            nFinish = hookFinish(cls);

            for (String cn : new String[]{CLS_OPLUS_POPUP, CLS_ARROW_POPUP, CLS_POPUP_BLUR_VIEW}) {
                try {
                    Class<?> ac = Class.forName(cn, false, loader);
                    nAnim += hookOpenCloseAnim(ac, "onCreateOpenAnimation", true);
                    nAnim += hookOpenCloseAnim(ac, "onCreateCloseAnimation", false);
                } catch (Throwable ignore) {}
            }
            SBLog.d("INSTALL", "hooks return=" + nReturn + " finish=" + nFinish + " anim=" + nAnim);
        } catch (Throwable t) {
            SBLog.e("INSTALL", "installHooks failed", t);
        }
    }

    private int hookFinish(Class<?> cls) {
        int n = 0;
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("finish")) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 2) continue;
                if (!"android.view.ViewGroup".equals(pt[0].getName())) continue;
                if (pt[1] != boolean.class) continue;
                try { m.setAccessible(true); } catch (Throwable ignore) {}
                hook((Executable) m)
                        .setId("iconblur.finish")
                        .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                        .intercept(new XposedInterface.Hooker() {
                            @Override
                            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                Object self = chain.getThisObject();
                                try {
                                    if (self instanceof View) {
                                        View v = (View) self;

                                        animateDepthBlur(v, 1.0f, 0.0f, 330L);

                                        clearIconBlur(v);
                                    }
                                } catch (Throwable t) {
                                }
                                return chain.proceed();
                            }
                        });
                n++;
            }
        } catch (Throwable t) {
        }
        return n;
    }

    private int hookOpenCloseAnim(Class<?> cls, final String methodName, final boolean opening) {
        int n = 0;
        try {

            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 1) continue;
                if (!"android.animation.AnimatorSet".equals(pt[0].getName())) continue;
                try { m.setAccessible(true); } catch (Throwable ignore) {}
                hook((Executable) m)
                        .setId("iconblur.opa." + methodName)
                        .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                        .intercept(new XposedInterface.Hooker() {
                            @Override
                            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                Object self = chain.getThisObject();
                                Object set = chain.getArg(0);
                                Object result = chain.proceed();
                                try {

                                    View anchor = null;
                                    if (self != null) {
                                        Object pbv = getFieldQuietlyAny(self, "mPopBlurView");
                                        if (pbv instanceof View) anchor = (View) pbv;
                                        if (anchor == null && self instanceof View) anchor = (View) self;
                                    }
                                    if (anchor != null && set != null) {
                                        float from = opening ? 0.0f : 1.0f;
                                        float to = opening ? 1.0f : 0.0f;
                                        Object anim = buildDepthBlurAnim(anchor, from, to, 330L);
                                        if (anim != null) {
                                            animatorSetPlay(set, anim);
                                        }
                                        if (!opening) {

                                            clearIconBlur(anchor);
                                        }
                                    } else {
                                    }
                                } catch (Throwable t) {
                                }
                                return result;
                            }
                        });
                n++;
            }
            }
        } catch (Throwable t) {
        }
        return n;
    }

    private int hookReturnView(Class<?> cls, String methodName, String id) {
        int n = 0;
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                try { m.setAccessible(true); } catch (Throwable ignore) {}
                final String mid = id + "#" + m.getParameterTypes().length;
                hook((Executable) m)
                        .setId("hook." + mid)
                        .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                        .intercept(new XposedInterface.Hooker() {
                            @Override
                            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                Object result = chain.proceed();
                                try {
                                    if (result instanceof View) {
                                        makeBlurLive((View) result, mid);
                                    }
                                } catch (Throwable t) {
                                }
                                return result;
                            }
                        });
                n++;
            }
        } catch (Throwable t) {
        }
        return n;
    }

    private void makeBlurLive(View view, String mid) {
        if (view == null) return;

        try {
            SBLog.d("LIVE", "makeBlurLive id=" + mid);
            clearStaticLayers(view);

            final View fv = view;
            try {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(120L);
                            applyIconBlur(fv);
                        } catch (Throwable t) {
                            SBLog.e("ICONBLUR", "delayed apply failed", t);
                        }
                    }
                }).start();
            } catch (Throwable ignore) {}

            setDepthBlur(view, 0.0f);

            try {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(500L);
                            Object launcher = getLauncherQuietly(fv);
                            if (launcher == null) {
                                SBLog.d("DEPTH", "launcher null, skip retry");
                                return;
                            }
                            Object dc = invokeNoArgQuietly(launcher, "getDepthController");
                            if (dc == null) {
                                SBLog.d("DEPTH", "depthController null");
                                return;
                            }
                            Object g = invokeNoArgQuietly(dc, "getCurrentBlur");
                            float v = (g instanceof Float) ? (Float) g : -1f;
                            SBLog.d("DEPTH", "currentBlur=" + v);
                            if (v <= 0.05f) {
                                boolean ok = setDepthBlur(fv, 1.0f);
                                SBLog.d("DEPTH", "fallback setBlur=1 ok=" + ok);
                            }
                        } catch (Throwable t) {
                            SBLog.e("DEPTH", "retry thread failed", t);
                        }
                    }
                }).start();
            } catch (Throwable ignore) {}

        } catch (Throwable t) {
            SBLog.e("LIVE", "makeBlurLive failed", t);
        }
    }

    private void applyIconBlur(View view) {
        try {
            Object effect = android.graphics.RenderEffect.createBlurEffect(
                    80.0f, 80.0f, android.graphics.Shader.TileMode.MIRROR);

            boolean oplusOk = false;
            try {
                Class<?> cls = Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect", false, loader());
                Method m = cls.getMethod("setBackgroundRenderEffect",
                        android.graphics.RenderEffect.class, View.class);
                m.setAccessible(true);
                m.invoke(null, effect, view);
                oplusOk = true;
            } catch (Throwable t) {
                SBLog.d("ICONBLUR", "oplus path failed: " + t);
            }

            if (!oplusOk) {
                String err = tryViewSetRenderEffect(view);
                SBLog.d("ICONBLUR", "fallback setRenderEffect err=" + err);
            } else {
                SBLog.d("ICONBLUR", "oplus path ok");
            }
        } catch (Throwable t) {
            SBLog.e("ICONBLUR", "applyIconBlur failed", t);
        }
    }

    private void clearIconBlur(View view) {
        try {
            Class<?> cls = Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect", false, loader());
            Method m = cls.getMethod("setBackgroundRenderEffect",
                    android.graphics.RenderEffect.class, View.class);
            m.setAccessible(true);
            m.invoke(null, null, view);
            SBLog.d("CLEAR", "icon blur cleared via oplus");
            return;
        } catch (Throwable t) {
        }
        try {
            Method m = View.class.getMethod("setRenderEffect", android.graphics.RenderEffect.class);
            m.invoke(view, (Object) null);
            SBLog.d("CLEAR", "icon blur cleared via View");
        } catch (Throwable t) {
            SBLog.d("CLEAR", "icon blur clear failed: " + t);
        }
    }

    private void clearStaticLayers(View view) {
        try {
            Class<?> drawableCls = Class.forName("android.graphics.drawable.Drawable", false, loader());
            boolean wall = false;
            boolean drag = false;
            try {
                Method m = view.getClass().getMethod("setWallpaperDrawable", drawableCls);
                m.setAccessible(true);
                m.invoke(view, (Object) null);
                wall = true;
            } catch (Throwable t) {
            }
            try {
                Method m = view.getClass().getMethod("setDragLayerDrawable", drawableCls);
                m.setAccessible(true);
                m.invoke(view, (Object) null);
                drag = true;
            } catch (Throwable t) {
            }
            try {
                Field f = view.getClass().getDeclaredField("mIsBlurUnavailable");
                f.setAccessible(true);
                f.setBoolean(view, true);
            } catch (Throwable ignore) {}
            try { invokeNoArgQuietly(view, "invalidate"); } catch (Throwable ignore) {}
            SBLog.d("CLEAR", "staticLayers wall=" + wall + " drag=" + drag);
        } catch (Throwable t) {
            SBLog.e("CLEAR", "clearStaticLayers failed", t);
        }
    }

    private String tryViewSetRenderEffect(View view) {
        try {
            Object effect = android.graphics.RenderEffect.createBlurEffect(
                    80.0f, 80.0f, android.graphics.Shader.TileMode.MIRROR);
            Method m = View.class.getMethod("setRenderEffect", android.graphics.RenderEffect.class);
            m.invoke(view, effect);
            return null;
        } catch (Throwable t) {
            return String.valueOf(t);
        }
    }

    private Object getStaticFloatProperty(Object dc, String name) {
        Class<?> c = dc.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(null);
            } catch (NoSuchFieldException nsf) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
    private Object buildDepthBlurAnim(View view, float from, float to, long duration) {
        try {
            Object launcher = getLauncherQuietly(view);
            if (launcher == null) { return null; }
            Object dc = invokeNoArgQuietly(launcher, "getDepthController");
            if (dc == null) { return null; }
            Object prop = getStaticFloatProperty(dc, "BLUR");
            if (prop == null) { return null; }

            float cur = from;
            try {
                if (to < from) {
                    Object g = invokeNoArgQuietly(dc, "getCurrentBlur");
                    if (g instanceof Float) {
                        float v = (Float) g;
                        if (v >= 0.0f && v <= from) cur = v;
                    }
                }
            } catch (Throwable ignore) {}

            Class<?> oaCls = Class.forName("android.animation.ObjectAnimator", false, loader());
            Class<?> propCls = Class.forName("android.util.Property", false, loader());
            Method ofFloat = oaCls.getMethod("ofFloat", Object.class, propCls, float[].class);
            Object anim = ofFloat.invoke(null, dc, prop, new float[]{cur, to});
            if (anim == null) return null;

            Method setDur = anim.getClass().getMethod("setDuration", long.class);
            setDur.invoke(anim, Long.valueOf(duration));
            try {
                Class<?> ip = Class.forName("android.animation.TimeInterpolator", false, loader());
                Class<?> dec = Class.forName("android.view.animation.DecelerateInterpolator", false, loader());
                Object decObj = dec.getDeclaredConstructor().newInstance();
                Method setI = anim.getClass().getMethod("setInterpolator", ip);
                setI.invoke(anim, decObj);
            } catch (Throwable ignore) {}

            return anim;
        } catch (Throwable t) {
            return null;
        }
    }

    private void animatorSetPlay(Object set, Object anim) {
        try {
            Class<?> setCls = Class.forName("android.animation.AnimatorSet", false, loader());
            Class<?> animCls = Class.forName("android.animation.Animator", false, loader());
            Method play = setCls.getMethod("play", animCls);
            play.invoke(set, anim);
        } catch (Throwable t) {
        }
    }

    private boolean animateDepthBlur(View view, float from, final float to, long duration) {
        try {
            Object launcher = getLauncherQuietly(view);
            if (launcher == null) { return false; }
            Object dc = invokeNoArgQuietly(launcher, "getDepthController");
            if (dc == null) { return false; }

            Object prop = getStaticFloatProperty(dc, "BLUR");
            if (prop == null) { return false; }
            if (prop != null) {

                try { invokeNoArgQuietly(dc, "cancelBlurAnimation"); } catch (Throwable ignore) {}

                float cur = from;
                try {
                    Object g = invokeNoArgQuietly(dc, "getCurrentBlur");
                    if (g instanceof Float) {
                        float v = (Float) g;
                        if (v >= 0.0f) cur = v;
                    }
                } catch (Throwable ignore) {}

                Class<?> oaCls = Class.forName("android.animation.ObjectAnimator", false, loader());
                Class<?> propCls = Class.forName("android.util.Property", false, loader());
                Method ofFloat = oaCls.getMethod("ofFloat", Object.class, propCls, float[].class);
                Object anim = ofFloat.invoke(null, dc, prop, new float[]{cur, to});
                if (anim != null) {

                    Method setDur = anim.getClass().getMethod("setDuration", long.class);
                    setDur.invoke(anim, Long.valueOf(duration));

                    try {
                        Class<?> ip = Class.forName("android.animation.TimeInterpolator", false, loader());
                        Class<?> dec = Class.forName("android.view.animation.DecelerateInterpolator", false, loader());
                        Object decObj = dec.getDeclaredConstructor().newInstance();
                        Method setI = anim.getClass().getMethod("setInterpolator", ip);
                        setI.invoke(anim, decObj);
                    } catch (Throwable ignore) {}
                    Method start = anim.getClass().getMethod("start");
                    start.invoke(anim);
                    return true;
                }
            }

            Method m = dc.getClass().getMethod("setBlurWithoutAnim", float.class);
            m.setAccessible(true);
            m.invoke(dc, Float.valueOf(to));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean setDepthBlur(View view, float value) {
        try {
            Object launcher = getLauncherQuietly(view);
            if (launcher == null) return false;
            Object dc = invokeNoArgQuietly(launcher, "getDepthController");
            if (dc == null) return false;
            Method m = dc.getClass().getMethod("setBlurWithoutAnim", float.class);
            m.setAccessible(true);
            m.invoke(dc, Float.valueOf(value));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private Object getLauncherQuietly(View view) {
        try {
            Context ctx = view.getContext();
            if (ctx == null) return null;

            try {
                Class<?> lc = Class.forName("com.android.launcher.Launcher", false, loader());
                try {
                    Method g = lc.getMethod("getLauncher", Context.class);
                    g.setAccessible(true);
                    Object r = g.invoke(null, ctx);
                    if (r != null) return r;
                } catch (Throwable ignore) {}
                try {
                    Method g2 = lc.getMethod("getLauncherOrNull", Context.class);
                    g2.setAccessible(true);
                    Object r2 = g2.invoke(null, ctx);
                    if (r2 != null) return r2;
                } catch (Throwable ignore) {}
            } catch (Throwable ignore) {}
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private Object getFieldQuietlyAny(Object obj, String name) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignore) {}
            c = c.getSuperclass();
        }
        return null;
    }

    private Object invokeNoArgQuietly(Object target, String name) {
        try {
            Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private ClassLoader loader() {
        return cl != null ? cl : ShortcutBlurModule.class.getClassLoader();
    }

}
