# 液态玻璃改造 TodoList（交接文档）

> 面向后续接手的代码模型。先读「技术约束」章节，能避免大量重复踩坑。

---

## 一、技术约束（必读，血的教训）

### 1. backdrop 库采用**源码集成**，不要改回 Maven 依赖
预编译 aar 在 AGP 9 下**无法工作**，原因链：
- backdrop 2.0.1 是 KMP 库，由 Kotlin 2.4.10 编译（metadata `mv=[2,4,0]`）。
- **AGP 9.x 全系锁死内置 Kotlin 2.2.10**（9.4.0-rc02 也是），无法解析 2.4 的 metadata。
- 禁用内置 Kotlin（`android.builtInKotlin=false`）又与 AGP 9 新 DSL 冲突，需再关 `android.newDsl=false`，得不偿失。
- 即使换成 metadata 匹配的 backdrop 1.0.0（`mv=[2,2,0]`）**仍然失败**：旧 aar 的变体不提供 `AgpVersionAttr`/`BuildTypeAttr`/`jvm.environment=android`，AGP 9.3.2 解析到 aar 却不提取其 `classes.jar` 进 Kotlin 编译 classpath。

**当前方案**：backdrop **1.0.0 源码**（纯 Android 库、无 KMP `expect/actual`、API 与 2.0.1 一致）已落地在
`app/src/main/java/com/kyant/backdrop/`（26 个文件），随项目编译。

**禁止**：把 `com/kyant/backdrop` 换成 `io.github.kyant0:backdrop:x.y.z` 依赖。

### 2. 版本锁（改动前务必确认）
| 配置 | 值 | 原因 |
|---|---|---|
| AGP | 9.3.2 | 稳定版，配合 Gradle 9.7.1 |
| Gradle | 9.7.1 | wrapper 指向腾讯云镜像 |
| Kotlin（catalog） | **2.2.10** | 必须与 AGP 内置 Kotlin 一致 |
| Compose BOM | 2025.12.01 | backdrop 源码需要 1.9+ 的 `GraphicsLayerScope.blendMode` |
| compileSdk | 36 | backdrop 1.0.0 `minCompileSdk=1`，无需 37 |
| Java | **21** | backdrop 1.0.0 字节码 class major=65 |
| 编译器参数 | `-Xcontext-parameters` | backdrop 源码使用 context parameters |

### 3. 腾讯云镜像必须加内容过滤
`settings.gradle.kts` 中镜像只代理 androidx/Google/Android：
```kotlin
maven {
    url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    content {
        includeGroupByRegex("androidx\\..*")
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
    }
}
```
否则 JetBrains Compose 的 aar 在镜像上只有元数据、没有实体文件，Gradle 命中后锁定该仓库不回退，直接构建失败。

### 4. Shadow / InnerShadow 的 alpha 是"叠加"而非"覆盖"
`Shadow.alpha` 会与 `color` 自带 alpha 相乘。写 `Shadow(radius=12.dp, alpha=0.15f)` 实际只有 0.1×0.15=0.015，阴影几乎不可见。
**统一用 `glassShadow(radius, alpha)` / `glassInnerShadow(radius, alpha)` 辅助函数**（在 `LiquidGlass.kt`，内部走 `color` 控制浓度）。

### 5. 性能红线
- 每个 `drawBackdrop` 会创建 1 个 GraphicsLayer；`shadow`、`highlight`、`innerShadow` **各自再创建 1 个**。
- 一个满配玻璃列表项 = 3~4 个 GraphicsLayer，滚动时会明显掉帧。
- 因此 `LiquidGlassListItem`（列表项）刻意关闭 lens/vibrancy 且不带 shadow，**不要给它加回满配**。
- `lens()` 走 AGSL RuntimeShader（需 Android 13+），`blur()` 需 Android 12+，低版本库内部自动降级。

---

## 二、已完成

- [x] backdrop 源码集成 + 全链路编译（debug / release 均通过，含 R8）
- [x] 11 个页面全部接入 `rememberLayerBackdrop()` 采样源
- [x] 玻璃组件库 `ui/common/LiquidGlass.kt`：`LiquidGlassCard / ListItem / Button / SearchBar / Badge / DialogSurface / Dialog / TopAppBar / BottomBar / Toast / BottomNavigation`
- [x] 修复 Shadow/InnerShadow 的 alpha 误用（阴影此前几乎不可见）
- [x] 修复 `FancyToast` 重复代码 + alpha 误用；未接入 backdrop 时自动降级纯色
- [x] **底部导航栏**（学习 / 成就 / 关于作者），悬浮胶囊玻璃，选中项叠加更厚玻璃胶囊
- [x] 玻璃效果"夸张化"：饱和度 1.8~2.1、blur 提升、启用 `depthEffect` + `chromaticAberration`（色散彩边）
- [x] 夜间模式：加宽提亮边缘高光（Ambient 风格）、白色外发光、表面 alpha 自适应
- [x] AppNav 导航改为「顶层页签 + 二级页面栈」，二级页面自动隐藏底栏
- [x] 全局弹窗（更新/公告）改为玻璃对话框，且仅在弹窗可见时录制背景
- [x] **单词卡片左右滑动切换**：左滑下一个 / 右滑上一个，翻页自动重置释义显示
- [x] **注册时选择词库**：注册改为两步（用户名 → 选词库），含搜索与选中标记
- [x] **主界面简化**：选定词库后首屏只呈现「当前词库」面板，不再罗列全部分类
- [x] **更换词库**：提供「更换词库 / 浏览全部」入口，点击任意词库即切换当前词库
- [x] **老用户兼容**：`getCurrentBook()` 为空时回退完整分类树，升级后不白屏
- [x] **批量修复 36 处 Shadow/InnerShadow 误用**（此前阴影实际透明度仅 0.01~0.025，几乎不可见）

### 手势语义（WordDetailScreen，已重排，勿回退）
| 手势 | 行为 |
|---|---|
| 左滑 | 下一个单词（已是最后一个且处于背诵模式则结束） |
| 右滑 | 上一个单词 |
| 上滑（需已翻面） | 例句面板 |
| 下滑（需已翻面） | 拼写面板 |

非背诵模式（从词库列表进入详情）也会加载整本词库作为滑动队列，因此详情页同样可左右滑动浏览。

---

## 三、待办（按优先级）

### P1 顶部栏玻璃化
已提供 `LiquidGlassTopAppBar`，但各页面仍用 M3 `LargeTopAppBar`/`TopAppBar`。
要点：M3 顶栏承载 `scrollBehavior`（折叠/吸顶）与 insets，直接替换风险高。建议做法是保留 M3 TopAppBar，将其 `containerColor` 设为透明，并在其下方叠一层玻璃背景，逐步替换、逐页验证滚动行为。

### P1 详情页底部操作栏玻璃化
`WordDetailScreen` 底部是 M3 `Surface`（约 645 行），可替换为 `LiquidGlassBottomBar`。

### P2 性能与体验
- [ ] 低端设备降级：根据 `ActivityManager.isLowRamDevice()` 或设备性能分级，关闭 `lens`/`chromaticAberration`。
- [ ] 玻璃效果总开关（设置项），让用户可关。
- [ ] 底栏的 `layerBackdrop` 会录制全屏内容，滚动时持续离屏渲染；若低端机掉帧，可考虑底栏改用静态磨砂（不采样）。
- [ ] `HomeScreen` 分类标题在 `forEach` 中用满配 `LiquidGlassCard`，数量多时可降级为 `LiquidGlassListItem`。

### P2 视觉与一致性
- [ ] 统一各页面背景层（渐变 + 光斑）的配色与布局，目前 11 个页面各自实现，存在细微差异。
- [ ] 校验深色模式下所有玻璃组件的文字对比度（玻璃上的 `onXxxContainer` 颜色可能需要调整）。
- [ ] 补充"关于作者"页内容（当前为原 AboutScreen）。

---

## 四、验证方式

```powershell
.\gradlew.bat :app:compileDebugKotlin            # 快速验证编译
.\gradlew.bat :app:assembleDebug :app:assembleRelease   # 完整验证（含 R8）
```

改动玻璃相关代码后务必跑 release，确认 R8 未误删 backdrop 的类。
