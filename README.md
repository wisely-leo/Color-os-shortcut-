# Color OS Shortcut Blur

让你的 ColorOS 快捷菜单（快捷方式弹窗）背景模糊换成**更加美观的动态模糊**。

一个 [LSPosed](https://github.com/LSPosed/LSPosed) 模块。

---

## ✨ 功能

- 快捷菜单弹出时，背景以**动态模糊**渐进淡入，替代原生静态模糊

## 📱 支持环境
| 项目 | 要求 |
|---|---|
| 系统 | **Android 16（API 36）** |
| 框架 | LSPosed（libxposed API 102） |
| 桌面 | ColorOS / OPPO 桌面（`com.android.launcher`） |
> 模块已将 `minSdkVersion` / `targetSdkVersion` 均限定为 **API 36**，
> 以避免低版本系统上的兼容性问题（`maxSdkVersion` 不限）。
> 已在 OnePlus / OPPO PLC110（ColorOS 16.1，Android 16 / API 36）实机验证。

## 🚀 安装

1. 确保设备已安装 **LSPosed** 框架
2. 从 [Releases](../../releases) 下载并安装模块 APK
3. 在 LSPosed 管理器中启用本模块
4. **作用域**勾选桌面（`com.android.launcher`）
5. 重启桌面进程或重启手机生效

## 📦 模块信息

| 项 | 值 |
|---|---|
| 包名 | `com.shortcutblur` |
| 模块入口 | `com.shortcutblur.ShortcutBlurModule` |

## 📁 目录结构

```
Color-os-shortcut-/
├── libs/
│   └── libxposed-api-102.jar               # 编译依赖（LSPosed API 102）
└── src/main/java/com/shortcutblur/
    └── ShortcutBlurModule.java             
```

## 🔧 编译

源码为单文件 LSPosed 模块，依赖：
- `libxposed-api-102.jar`（LSPosed API 102，已含于 `libs/`）
- Android SDK（`android.jar`，例如 `$ANDROID_HOME/platforms/android-36/android.jar`）

将源码根设为 `src/main/java`，两个依赖都加入 classpath：

```bash
javac -encoding UTF-8 -source 8 -target 8 \
      -cp "libs/libxposed-api-102.jar:$ANDROID_HOME/platforms/android-36/android.jar" \
      -d out \
      $(find src/main/java -name '*.java')
```

编译产物为 `com.shortcutblur.ShortcutBlurModule`（及若干匿名内部类）。
用 `d8 --min-api 36` 转为 `classes.dex` 后，替换进一个最小 LSPosed 模块 APK
（需含 `META-INF/xposed/java_init.list` 指向 `com.shortcutblur.ShortcutBlurModule`）
即完成打包。**注意**：模块的 `AndroidManifest.xml` 中 `uses-sdk` 的
`minSdkVersion` / `targetSdkVersion` 均为 `36`，如需适配更低系统，请自行下调。

## 🧠 实现原理

- Hook 桌面 `OplusPopupContainerWithArrow` 的入场 / 退场动画创建入口
  （`onCreateOpenAnimation` / `onCreateCloseAnimation`），
  把「模糊 0→1 / 1→0」的动画直接 `set.play(...)` 并进原生 `AnimatorSet`，
  与原生 alpha / scale 动画。
- 模糊由 `RenderEffect.createBlurEffect(80f, ...)` 实现：
  优先调用 `com.oplus.view.OplusViewBackgroundRenderEffect.setBackgroundRenderEffect(effect, view)`，
  失败则回退标准 `View.setRenderEffect(effect)`。

## 📄 许可证

GPL-3.0，见 [LICENSE](LICENSE)。
