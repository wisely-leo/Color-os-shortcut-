package io.github.libxposed.api;

public interface XposedModuleInterface {
    interface ModuleLoadedParam {
        String getProcessName();
        int getApiVersion();
    }

    interface PackageLoadedParam {
        ClassLoader getClassLoader();
        ClassLoader getDefaultClassLoader();
        String getPackageName();
        String getProcessName();
        boolean isFirstPackage();
    }

    interface PackageReadyParam {
        ClassLoader getClassLoader();
        ClassLoader getDefaultClassLoader();
        String getPackageName();
        String getProcessName();
        boolean isFirstPackage();
    }

    interface HotReloadingParam {}

    interface HotReloadedParam {}
}