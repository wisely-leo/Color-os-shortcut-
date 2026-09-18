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
| 桌面 | **仅 ColorOS / OPPO 系统桌面** |

> 模块作用域仅限 ColorOS 桌面。ColorOS 桌面内部会复用 AOSP launcher3
> 的能力（类路径），但作用域只声明 ColorOS 桌面包名：
>
> `com.android.launcher`、`com.oplus.launcher`、`com.coloros.launcher`

已在 OnePlus / OPPO PLC110（ColorOS 16.1，Android 16 / API 36）实机验证。

## 🚀 安装

1. 确保设备已安装 **LSPosed** 框架
2. 从 [Releases](../../releases) 下载并安装模块 APK
3. 在 LSPosed 管理器中启用本模块
4. **作用域**勾选 ColorOS 桌面
5. 重启桌面进程或重启手机生效

## 📦 模块信息

| 项 | 值 |
|---|---|
| 包名 | `com.shortcutblur` |
| 模块入口 | `com.shortcutblur.ShortcutBlurModule` |
| 最低 / 目标 SDK | 36 / 36 |

## 📁 目录结构

```
Color-os-shortcut-/
├── assets/icons/                     # 应用图标（各密度）
├── libs/
│   └── libxposed-api-102.jar         # 编译依赖（LSPosed API 102）
└── src/main/java/com/shortcutblur/
    ├── ShortcutBlurModule.java       # 模块主源码
    └── SBLog.java                    # 可选探针日志（默认关闭）
```

## 📝 探针日志（可选）

`SBLog` 提供调试日志，**默认关闭**（编译期常量，零开销）。

开启方式：将 `src/main/java/com/shortcutblur/SBLog.java` 中的

```java
public static final boolean ENABLED = false;
```

改为 `true` 后重新构建。日志固定输出到：

```
/storage/emulated/0/Download/ShortcutBlur.log
```

## 🧠 实现原理

- Hook 桌面 `OplusPopupContainerWithArrow` 的入场 / 退场动画创建入口
  （`onCreateOpenAnimation` / `onCreateCloseAnimation`），
  把「模糊 0→1 / 1→0」的动画直接 `set.play(...)` 并进原生 `AnimatorSet`，
  与原生 alpha / scale 动画同步。
- 模糊由 `RenderEffect.createBlurEffect(80f, ...)` 实现：
  优先调用 `com.oplus.view.OplusViewBackgroundRenderEffect.setBackgroundRenderEffect(effect, view)`，
  失败则回退标准 `View.setRenderEffect(effect)`。

## 📄 许可证

GPL-3.0，见 [LICENSE](LICENSE)。