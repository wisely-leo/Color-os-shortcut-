package com.shortcutblur;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class ShortcutBlurModule extends XposedModule {

    private static final String CLS_POPUP_BLUR_VIEW = "com.android.launcher3.popup.PopupBlurView";
    private static final String CLS_OPLUS_POPUP = "com.android.launcher3.popup.OplusPopupContainerWithArrow";
    private static final String CLS_ARROW_POPUP = "com.android.launcher3.popup.ArrowPopup";
    private static final String CLS_LAUNCHER = "com.android.launcher.Launcher";

    private static final String CLS_OPLUS_EFFECT = "com.oplus.view.OplusViewBackgroundRenderEffect";
    private static final String CLS_DRAWABLE = "android.graphics.drawable.Drawable";
    private static final String CLS_OBJECT_ANIMATOR = "android.animation.ObjectAnimator";
    private static final String CLS_PROPERTY = "android.util.Property";
    private static final String CLS_ANIMATOR_SET = "android.animation.AnimatorSet";
    private static final String CLS_ANIMATOR = "android.animation.Animator";
    private static final String CLS_TIME_INTERPOLATOR = "android.animation.TimeInterpolator";
    private static final String CLS_DECELERATE = "android.view.animation.DecelerateInterpolator";

    private static final String M_GET_POP_BLUR_VIEW = "getPopBlurView";

    private static final float BLUR_RADIUS = 80.0f;
    private static final long BLUR_DURATION = 330L;

    private static final long ICON_BLUR_DELAY = 120L;
    private static final long DEPTH_FALLBACK_DELAY = 500L;

    private ClassLoader cl;

    private volatile boolean installed = false;

    private final Map<View, Boolean> armed = new WeakHashMap<>();

    private volatile RenderEffect blurEffect;

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Constructor<?>> CTOR_CACHE = new ConcurrentHashMap<>();

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        try {
            if (param == null) return;
            String pkg = param.getPackageName();
            SBLog.i("onPackageReady pkg=" + pkg);
            if (!isTargetLauncher(pkg)) return;
            ClassLoader loader = param.getClassLoader();
            if (loader == null) return;
            this.cl = loader;

            if (installed) {
                SBLog.d("READY", "already installed, skip");
                return;
            }

            InstallResult r = installHooks(loader);
            if (r.critical > 0) {
                installed = true;
                SBLog.d("READY", "installed critical=" + r.critical + " total=" + r.total);
            } else {
                SBLog.d("READY", "no critical hook, will retry (total=" + r.total + ")");
            }
        } catch (Throwable t) {
            SBLog.e("READY", "onPackageReady failed", t);
        }
    }

    private static boolean isTargetLauncher(String p) {
        return "com.android.launcher".equals(p)
                || "com.oplus.launcher".equals(p)
                || "com.coloros.launcher".equals(p);
    }

    private static final class InstallResult {
        int critical;
        int total;
    }

    private InstallResult installHooks(ClassLoader loader) {
        InstallResult r = new InstallResult();

        Set<Method> hooked = new HashSet<>();
        try {
            Class<?> cls = forName(CLS_POPUP_BLUR_VIEW, loader);
            if (cls != null) {
                r.total += hookReturnView(cls, M_GET_POP_BLUR_VIEW, "pbv");
                r.total += hookFinish(cls);
            }
            Class<?> comp = forName(CLS_POPUP_BLUR_VIEW + "$Companion", loader);
            if (comp != null) {
                r.total += hookReturnView(comp, M_GET_POP_BLUR_VIEW, "pbv_companion");
            }

            for (String cn : new String[]{CLS_OPLUS_POPUP, CLS_ARROW_POPUP, CLS_POPUP_BLUR_VIEW}) {
                Class<?> ac = forName(cn, loader);
                if (ac == null) continue;
                r.critical += hookOpenCloseAnim(ac, "onCreateOpenAnimation", true, hooked);
                r.critical += hookOpenCloseAnim(ac, "onCreateCloseAnimation", false, hooked);
            }
            r.total += r.critical;
            SBLog.d("INSTALL", "critical=" + r.critical + " total=" + r.total);
        } catch (Throwable t) {
            SBLog.e("INSTALL", "installHooks failed", t);
        }
        return r;
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
                                        animateDepthBlur(v, 1.0f, 0.0f, BLUR_DURATION);
                                        clearIconBlur(v);
                                    }
                                } catch (Throwable t) {
                                    SBLog.e("FINISH", "hook body failed", t);
                                }
                                return chain.proceed();
                            }
                        });
                n++;
            }
        } catch (Throwable t) {
            SBLog.e("INSTALL", "hookFinish failed", t);
        }
        return n;
    }

    private int hookOpenCloseAnim(Class<?> cls, final String methodName, final boolean opening,
                                  Set<Method> hooked) {
        int n = 0;
        try {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals(methodName)) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length != 1) continue;
                    if (!"android.animation.AnimatorSet".equals(pt[0].getName())) continue;

                    if (!hooked.add(m)) {
                        SBLog.d("INSTALL", "skip dup hook " + c.getName() + "." + methodName);
                        continue;
                    }
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
                                            Object anim = newDepthBlurAnim(anchor, from, to, BLUR_DURATION);
                                            if (anim != null) {
                                                animatorSetPlay(set, anim);
                                            }
                                            if (!opening) {
                                                clearIconBlur(anchor);
                                            }
                                        }
                                    } catch (Throwable t) {
                                        SBLog.e("ANIM", "hook body failed (" + methodName + ")", t);
                                    }
                                    return result;
                                }
                            });
                    n++;
                }
            }
        } catch (Throwable t) {
            SBLog.e("INSTALL", "hookOpenCloseAnim(" + methodName + ") failed", t);
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
                                    SBLog.e("LIVE", "hook body failed", t);
                                }
                                return result;
                            }
                        });
                n++;
            }
        } catch (Throwable t) {
            SBLog.e("INSTALL", "hookReturnView(" + id + ") failed", t);
        }
        return n;
    }

    private void makeBlurLive(View view, String mid) {
        if (view == null) return;
        try {
            SBLog.d("LIVE", "makeBlurLive id=" + mid);

            armIconBlur(view, true);
            clearStaticLayers(view);

            final View fv = view;

            fv.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        applyIconBlur(fv);
                    } catch (Throwable t) {
                        SBLog.e("ICONBLUR", "delayed apply failed", t);
                    }
                }
            }, ICON_BLUR_DELAY);

            setDepthBlur(view, 0.0f);

            fv.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
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
                        SBLog.e("DEPTH", "retry failed", t);
                    }
                }
            }, DEPTH_FALLBACK_DELAY);

        } catch (Throwable t) {
            SBLog.e("LIVE", "makeBlurLive failed", t);
        }
    }

    private void applyIconBlur(View view) {

        if (!isIconBlurArmed(view)) {
            SBLog.d("ICONBLUR", "skipped: disarmed");
            return;
        }
        try {
            RenderEffect effect = getBlurEffect();

            boolean oplusOk = false;
            try {
                Class<?> cls = forName(CLS_OPLUS_EFFECT, loader());
                if (cls != null) {
                    Method m = getMethodCached(cls, "setBackgroundRenderEffect", RenderEffect.class, View.class);
                    if (m != null) {
                        m.invoke(null, effect, view);
                        oplusOk = true;
                    }
                }
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

    private void armIconBlur(View view, boolean value) {
        if (view == null) return;
        synchronized (armed) {
            if (value) {
                armed.put(view, Boolean.TRUE);
            } else {
                armed.remove(view);
            }
        }
    }

    private boolean isIconBlurArmed(View view) {
        if (view == null) return false;
        synchronized (armed) {
            return armed.containsKey(view);
        }
    }

    private RenderEffect getBlurEffect() {
        RenderEffect e = blurEffect;
        if (e == null) {
            e = RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.MIRROR);
            blurEffect = e;
        }
        return e;
    }

    private void clearIconBlur(View view) {

        armIconBlur(view, false);
        try {
            Class<?> cls = forName(CLS_OPLUS_EFFECT, loader());
            if (cls != null) {
                Method m = getMethodCached(cls, "setBackgroundRenderEffect", RenderEffect.class, View.class);
                if (m != null) {
                    m.invoke(null, null, view);
                    SBLog.d("CLEAR", "icon blur cleared via oplus");
                    return;
                }
            }
        } catch (Throwable t) {
            SBLog.e("CLEAR", "oplus clear failed", t);
        }
        try {
            Method m = getMethodCached(View.class, "setRenderEffect", RenderEffect.class);
            if (m != null) {
                m.invoke(view, (Object) null);
                SBLog.d("CLEAR", "icon blur cleared via View");
            }
        } catch (Throwable t) {
            SBLog.d("CLEAR", "icon blur clear failed: " + t);
        }
    }

    private void clearStaticLayers(View view) {
        try {
            Class<?> drawableCls = forName(CLS_DRAWABLE, loader());
            boolean wall = false;
            boolean drag = false;

            if (drawableCls != null) {
                Method mw = getMethodCached(view.getClass(), "setWallpaperDrawable", drawableCls);
                if (mw != null) {
                    try { mw.invoke(view, (Object) null); wall = true; } catch (Throwable ignore) {}
                }
                Method md = getMethodCached(view.getClass(), "setDragLayerDrawable", drawableCls);
                if (md != null) {
                    try { md.invoke(view, (Object) null); drag = true; } catch (Throwable ignore) {}
                }
            }

            Field f = findField(view.getClass(), "mIsBlurUnavailable");
            if (f != null) {
                try { f.setBoolean(view, true); } catch (Throwable ignore) {}
            }
            invokeNoArgQuietly(view, "invalidate");
            SBLog.d("CLEAR", "staticLayers wall=" + wall + " drag=" + drag);
        } catch (Throwable t) {
            SBLog.e("CLEAR", "clearStaticLayers failed", t);
        }
    }

    private String tryViewSetRenderEffect(View view) {
        try {
            Method m = getMethodCached(View.class, "setRenderEffect", RenderEffect.class);
            if (m == null) return "setRenderEffect not found";
            m.invoke(view, getBlurEffect());
            return null;
        } catch (Throwable t) {
            return String.valueOf(t);
        }
    }

    private Object newDepthBlurAnim(View view, float from, float to, long duration) {
        try {
            Object launcher = getLauncherQuietly(view);
            if (launcher == null) return null;
            Object dc = invokeNoArgQuietly(launcher, "getDepthController");
            if (dc == null) return null;
            Object prop = getStaticFloatProperty(dc, "BLUR");
            if (prop == null) return null;

            float cur = from;
            try {
                Object g = invokeNoArgQuietly(dc, "getCurrentBlur");
                if (g instanceof Float) {
                    float v = (Float) g;
                    if (v >= 0.0f) cur = v;
                }
            } catch (Throwable ignore) {}

            Class<?> oaCls = forName(CLS_OBJECT_ANIMATOR, loader());
            Class<?> propCls = forName(CLS_PROPERTY, loader());
            if (oaCls == null || propCls == null) return null;

            Method ofFloat = getMethodCached(oaCls, "ofFloat", Object.class, propCls, float[].class);
            if (ofFloat == null) return null;
            Object anim = ofFloat.invoke(null, dc, prop, new float[]{cur, to});
            if (anim == null) return null;

            Class<?> animCls = forName(CLS_ANIMATOR, loader());
            if (animCls == null) return null;

            Method setDur = getMethodCached(animCls, "setDuration", long.class);
            if (setDur != null) setDur.invoke(anim, duration);

            Class<?> ipCls = forName(CLS_TIME_INTERPOLATOR, loader());
            Class<?> decCls = forName(CLS_DECELERATE, loader());
            if (ipCls != null && decCls != null) {
                Object decObj = newInstanceCached(decCls);
                Method setI = getMethodCached(animCls, "setInterpolator", ipCls);
                if (setI != null && decObj != null) setI.invoke(anim, decObj);
            }
            return anim;
        } catch (Throwable t) {
            SBLog.e("ANIM", "newDepthBlurAnim failed", t);
            return null;
        }
    }

    private boolean animateDepthBlur(View view, float from, float to, long duration) {
        try {
            Object anim = newDepthBlurAnim(view, from, to, duration);
            if (anim != null) {
                Class<?> animCls = forName(CLS_ANIMATOR, loader());
                if (animCls != null) {
                    Method start = getMethodCached(animCls, "start");
                    if (start != null) {
                        start.invoke(anim);
                        return true;
                    }
                }
            }
            return setDepthBlur(view, to);
        } catch (Throwable t) {
            return false;
        }
    }

    private void animatorSetPlay(Object set, Object anim) {
        try {
            Class<?> setCls = forName(CLS_ANIMATOR_SET, loader());
            Class<?> animCls = forName(CLS_ANIMATOR, loader());
            if (setCls == null || animCls == null) return;
            Method play = getMethodCached(setCls, "play", animCls);
            if (play != null) play.invoke(set, anim);
        } catch (Throwable t) {
        }
    }

    private boolean setDepthBlur(View view, float value) {
        try {
            Object launcher = getLauncherQuietly(view);
            if (launcher == null) return false;
            Object dc = invokeNoArgQuietly(launcher, "getDepthController");
            if (dc == null) return false;
            Method m = getMethodCached(dc.getClass(), "setBlurWithoutAnim", float.class);
            if (m == null) return false;
            m.invoke(dc, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private Class<?> forName(String name, ClassLoader loader) {
        if (loader == null) return null;
        String key = name + "@" + System.identityHashCode(loader);
        Class<?> cached = CLASS_CACHE.get(key);
        if (cached != null) return cached;
        try {
            Class<?> c = Class.forName(name, false, loader);
            CLASS_CACHE.put(key, c);
            return c;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method getMethodCached(Class<?> cls, String name, Class<?>... paramTypes) {
        StringBuilder sb = new StringBuilder(cls.getName()).append('#').append(name).append('(');
        for (Class<?> p : paramTypes) sb.append(p.getName()).append(',');
        sb.append(')');
        String key = sb.toString();
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;
        try {
            Method m = cls.getMethod(name, paramTypes);
            m.setAccessible(true);
            METHOD_CACHE.put(key, m);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String name) {
        String key = cls.getName() + "#" + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                FIELD_CACHE.put(key, f);
                return f;
            } catch (NoSuchFieldException nsf) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Object newInstanceCached(Class<?> cls) {
        String key = cls.getName();
        Constructor<?> cached = CTOR_CACHE.get(key);
        try {
            if (cached == null) {
                cached = cls.getDeclaredConstructor();
                cached.setAccessible(true);
                CTOR_CACHE.put(key, cached);
            }
            return cached.newInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    private Object getStaticFloatProperty(Object dc, String name) {
        Field f = findField(dc.getClass(), name);
        if (f == null) return null;
        try {
            return f.get(null);
        } catch (Throwable ignore) {
        }
        try {
            return f.get(dc);
        } catch (Throwable t) {
            SBLog.d("DEPTH", "BLUR field not static nor instance-accessible: " + t);
            return null;
        }
    }

    private Object getLauncherQuietly(View view) {
        try {
            Context ctx = view.getContext();
            if (ctx == null) return null;
            Class<?> lc = forName(CLS_LAUNCHER, loader());
            if (lc == null) return null;

            Method g = getMethodCached(lc, "getLauncher", Context.class);
            if (g != null) {
                try {
                    Object r = g.invoke(null, ctx);
                    if (r != null) return r;
                } catch (Throwable ignore) {}
            }
            Method g2 = getMethodCached(lc, "getLauncherOrNull", Context.class);
            if (g2 != null) {
                try {
                    Object r2 = g2.invoke(null, ctx);
                    if (r2 != null) return r2;
                } catch (Throwable ignore) {}
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private Object invokeNoArgQuietly(Object target, String name) {
        try {
            Method m = getMethodCached(target.getClass(), name);
            if (m == null) return null;
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private Object getFieldQuietlyAny(Object obj, String name) {
        Field f = findField(obj.getClass(), name);
        if (f == null) return null;
        try {
            return f.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    private ClassLoader loader() {
        return cl;
    }
}
