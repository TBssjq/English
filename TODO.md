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
| AGP | 9.4.1 | 配合 Gradle 9.7.1 |
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

### 5. 顶栏玻璃只能通过 Material3 顶栏的 `modifier` 叠加
`TopAppBar` / `LargeTopAppBar` 把调用方传入的 `modifier` 放在最外层 `Box` 上，状态栏 `windowInsetsPadding`
与折叠偏移都在内层处理。因此 `Modifier.glassTopBar(backdrop)` 能覆盖整条顶栏（含状态栏区域与折叠动画），
同时保留滚动折叠、insets 与无障碍语义。

**禁止**：① 用自绘顶栏替换 M3 顶栏；② 在外面套 `Box` 再叠玻璃（会与 M3 内部 insets 重复计算）；
③ 去掉 `glassTopBar` 里的 `remember`——页面重组会新建 modifier，`DrawBackdropNode` 判定变更后
会重新录制背景采样，输入框逐字输入这类高频重组会明显掉帧。

顶栏容器色必须配套 `glassTopBarColors()`（透明），否则 M3 默认的 `surface` 会把玻璃盖住。

### 6. 玻璃特效开关与低端设备降级
`GlassQuality`（`ui/glass/GlassSpec.kt`）里 `effectsOn` 是 **Compose 状态**，不是普通字段：
`drawBackdrop` 在 `observeReads` 内执行 effects 块，状态变化会直接触发对应节点重建效果链，
不需要重组整棵页面树。关掉开关后 `lensEnabled` / `vibrancyEnabled` 为 false，模糊降到一半。

`lowRam`（`ActivityManager.isLowRamDevice` 或堆上限 < 256 MB）在 `MainActivity` 里配置一次；
用户偏好存在 `UserManager`，`MainActivity` 启动时同步给 `GlassQuality`，首页设置条切换时两处一起写。

### 7. 性能红线
- 每个 `drawBackdrop` 会创建 1 个 GraphicsLayer；`shadow`、`highlight`、`innerShadow` **各自再创建 1 个**。
- 一个满配玻璃列表项 = 3~4 个 GraphicsLayer，滚动时会明显掉帧。
- 因此列表项与分类标题必须用 `LiquidGlassListItem`（半透明磨砂 + 描边，完全不走 backdrop 采样），
  **不要给它加回满配**。
- 页面级玻璃（卡片、按钮、顶栏、底栏）统一采样 `LocalAppBackdrop`，全应用只有一份全屏采样层；
  各页面不要自己 `rememberLayerBackdrop()` 再录制全屏（会导致嵌套采样递归，栈溢出崩溃）。
- `lens()` 走 AGSL RuntimeShader（需 Android 13+），`blur()` 需 Android 12+，低版本库内部自动降级。

---

## 二、已完成

- [x] backdrop 源码集成 + 全链路编译（debug / release 均通过，含 R8）
- [x] 玻璃组件库 `ui/common/LiquidGlass.kt`：`LiquidGlassCard / ListItem / SearchBar / Badge / DialogSurface / Dialog / Toast / BottomBar / BottomNavigation`
- [x] 新玻璃组件 `ui/glass/`：`LiquidCard / LiquidButton / LiquidToggle / LiquidBottomTabs`，配色与形状集中在 `GlassSpec.kt`
- [x] 修复 Shadow/InnerShadow 的 alpha 误用（阴影此前几乎不可见，共 36 处）
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
- [x] 全局背景层统一：`AppNav` 提供唯一采样源（渐变 + 三个光斑），光斑参数集中在 `GlassBackdropSpec`
- [x] **顶部栏玻璃化**：9 个页面（首页 / 词库 / 详情 / 搜索 / 测验 / 疯狂刷题 / 打卡 / 错题本 / 关于）统一 `Modifier.glassTopBar`
- [x] **详情页底部操作栏玻璃化**：不透明 `Surface` 换成 `LiquidGlassBottomBar`（悬浮圆角）
- [x] **玻璃特效总开关**：首页设置条可关闭折射与色散，配合低端设备自动降级
- [x] **列表项轻量化**：分类标题、子分类、词库、搜索结果改用 `LiquidGlassListItem`
- [x] **间隔重复复习**：`ReviewScheduler`（答对升级 / 模糊降级 / 不认识归零，间隔 `2^level` 天），首页「今日待复习 N 词」进入到期单词
- [x] 发音播放抽为 `service/PronunciationPlayer`，播放结束/出错清空引用
- [x] 词库读取改为快速失败（`check` 断言），不再静默返回 0 条 / 空列表
- [x] **测验答错即时写入错题本**（`QuizScreen` 监听 `answerResult`），中途退出不再丢题；答错反馈卡新增「查看单词」
- [x] **拼写纠错对齐**：输入框与纠错文本放大；改为最长公共子序列对齐，多写/写错一个字母只标那一处，「差一点」也标红
- [x] **自动发音开关跨轮次保留**（`UserLibrary.autoPlayAudioEnabled`）
- [x] 查词弹窗在没有音标数据时同样提供英音 / 美音
- [x] 单元测试：`ReviewSchedulerTest`、`LibraryCatalogTest`、`AppUpdateManagerTest`、`QuizEngineTest`

### 手势语义（WordDetailScreen，已重排，勿回退）
| 手势 | 行为 |
|---|---|
| 左滑 | 下一个单词（已是最后一个且处于背诵模式则结束） |
| 右滑 | 上一个单词 |
| 上滑（需已翻面） | 例句面板 |
| 下滑（需已翻面） | 拼写面板 |

非背诵模式（从词库列表进入详情）也会加载整本词库作为滑动队列，因此详情页同样可左右滑动浏览。

---

## 三、待办

### P2 视觉与一致性
- [x] 已核对 `Color.kt` 里明暗两套配色的文字对比度：正文与容器组合全部 ≥ 4.5:1（最低 5.17，亮色 `onPrimary/primary`），`outline` 只用于描边，4.36 ≥ 3:1 的图形组件要求
- [x] 「关于作者」页包含用户名、作者介绍、版本信息与检查更新、最新公告、QQ / GitHub / 邮箱联系方式

代码与文档层面无未完成的必须项。剩余事项依赖外部凭据：v2.2 签名 APK 需用 release keystore 生成后上传 GitHub Release，再把网站 `version/english.json` 的版本号与下载地址切换过去，否则客户端下载会得到 404。

---

## 四、验证方式

```powershell
.\gradlew.bat :app:compileDebugKotlin            # 快速验证编译
.\gradlew.bat :app:testDebugUnitTest             # 全部单元测试
.\gradlew.bat :app:assembleDebug :app:assembleRelease   # 完整验证（含 R8）
```

改动玻璃相关代码后务必跑 release，确认 R8 未误删 backdrop 的类。分层结构、核心流程与发布步骤见 [`DEVELOPMENT.md`](./DEVELOPMENT.md)。
