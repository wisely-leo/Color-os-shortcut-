package io.github.libxposed.api;

public interface XposedInterface {
    int PRIORITY_DEFAULT = 50;

    enum ExceptionMode {
        DEFAULT,
        PASSTHROUGH
    }

    interface Chain {
        Object proceed() throws Throwable;
        Object proceed(Object[] args) throws Throwable;
        Object proceedWith(Object newThis) throws Throwable;
        Object proceedWith(Object newThis, Object[] args) throws Throwable;
        Object getThisObject();
        java.util.List<Object> getArgs();
        Object getArg(int i);
        java.lang.reflect.Executable getExecutable();
    }

    interface Hooker {
        Object intercept(Chain chain) throws Throwable;
    }

    interface HookHandle {
        HookHandle intercept(Hooker h);
        void unhook();
        String getId();
        java.lang.reflect.Executable getExecutable();
        void replaceHook(Hooker h);
    }

    interface HookBuilder {
        HookBuilder setId(String id);
        HookBuilder setPriority(int priority);
        HookBuilder setExceptionMode(ExceptionMode mode);
        HookHandle intercept(Hooker h);
    }
}