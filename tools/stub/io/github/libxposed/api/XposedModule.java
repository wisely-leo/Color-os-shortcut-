package io.github.libxposed.api;

import java.lang.reflect.Executable;

public class XposedModule implements XposedModuleInterface {

    public void log(int p, String t, String m) {}

    public void log(int p, String t, String m, Throwable e) {}

    public int getApiVersion() { return 102; }

    public String getFrameworkName() { return "LSPosed"; }

    public String getFrameworkVersion() { return "1.9.2"; }

    public XposedInterface.HookBuilder hook(Executable executable) { return null; }

    public XposedInterface.HookBuilder hookClassInitializer(Class<?> cls) { return null; }

    public void onModuleLoaded(ModuleLoadedParam param) {}

    public void onPackageLoaded(PackageLoadedParam param) {}

    public void onPackageReady(PackageReadyParam param) {}

    public boolean onHotReloading(HotReloadingParam param) { return true; }

    public void onHotReloaded(HotReloadedParam param) {}
}