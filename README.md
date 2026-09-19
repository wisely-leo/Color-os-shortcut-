# Color OS Shortcut Blur

为 **ColorOS 桌面**提供动态模糊效果的 [LSPosed](https://github.com/LSPosed/LSPosed) 模块。

---

## ✨ 功能

- **图标模糊**：长按 / 拖拽图标时，按需为图标叠加动态模糊
- **文件夹模糊**：打开文件夹时，内部图标模糊并带有渐进动画；关闭时平滑还原
- **壁纸深度模糊**：接入桌面 depth controller，随桌面状态联动，让模糊层次更自然
- **后处理采样适配**：统一后处理采样率，改善模糊边缘的马赛克 / 颗粒感

## 📱 支持环境

| 项目 | 要求 |
|---|---|
| 系统 | **Android 16（API 36）** |
| 框架 | LSPosed（libxposed API 102） |
| 桌面 | **仅 ColorOS / OPPO 系统桌面** |

本模块声明 **4 个作用域包**，分两类：

- **桌面进程**：`com.android.launcher`、`com.oplus.launcher`、`com.coloros.launcher`
  （代码中以 `isTargetLauncher` 统一匹配）。ColorOS 桌面内部复用 AOSP launcher3 的类路径，
  模块对 `PopupBlurView`、`ArrowPopup`、`OplusPopupContainerWithArrow` 等挂载 Hook，
  实现图标 / 文件夹 / 壁纸深度模糊。
- **后处理进程**：`com.oplus.blur`（独立进程，非桌面本身）。模块对类 `e.a` 的
  `c` / `e` / `d` / `f` 四个方法挂载 Hook，将后处理模糊采样率由系统原生 `0.25`
  提升至 `0.5`。

> ⚠️ 因此本模块**并非只作用于桌面**：必须同时覆盖 `com.oplus.blur` 进程，
> 否则后处理采样适配不会生效。

已在 OnePlus / OPPO PLC110（ColorOS 16.1，Android 16 / API 36）实机验证。

## 🚀 安装

1. 确保设备已安装 **LSPosed** 框架
2. 从 [Releases](../../releases) 下载并安装模块 APK
3. 在 LSPosed 管理器中启用本模块
4. **作用域**保持默认（模块已声明，全选即可）
5. 重启相应作用域（桌面进程与 `com.oplus.blur` 进程）生效

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

后处理进程的日志会按进程 UID 分流到独立的 `PostEffectBlur.log`。

## 🧠 实现原理

- Hook 桌面弹窗容器的入场 / 退场动画创建入口
  （`onCreateOpenAnimation` / `onCreateCloseAnimation`），
  把「模糊 0→1 / 1→0」的动画直接 `set.play(...)` 并进原生 `AnimatorSet`，
  与原生 alpha / scale 动画同步。
- 按视图所处状态分流处理：在文件夹内时走图标模糊路径，其余走壁纸深度模糊路径；
  判定结果在单次弹窗流程内缓存，流程结束时失效。
- 模糊由 `RenderEffect.createBlurEffect(64f, ...)` 实现：
  优先调用 `com.oplus.view.OplusViewBackgroundRenderEffect.setBackgroundRenderEffect(effect, view)`，
  失败则回退标准 `View.setRenderEffect(effect)`。
- 图标模糊动画在收尾与逐帧更新时校验有效性，中途状态变化时立即取消，避免闪回。

## 📄 许可证

GPL-3.0，见 [LICENSE](LICENSE)。
