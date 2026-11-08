# 开发文档

面向接手本项目的开发者。项目介绍与功能清单见 [`README.md`](./README.md)，玻璃组件的底层约束见 [`TODO.md`](./TODO.md) 的「技术约束」，需求背景见 [`design.md`](./design.md)。

---

## 1. 分层与依赖方向

```
ui/  quiz/  service/          ← 界面、测验、发音播放
        ↓
data/                         ← 单例数据层，无 UI 依赖
        ↓
android.* / com.kyant.backdrop（源码集成）
```

- `data/` 只被上层引用，不反向依赖 `ui/`。
- `com/kyant/backdrop/` 是第三方源码目录，随项目编译，禁止替换为 Maven 依赖。

### 数据层单例

| 单例 | 职责 | 存储文件 |
|---|---|---|
| `DatabaseManager` | 词库数据库的复制、打开、查询、连接引用计数 | `assets/databases/*.db` → 内部缓存 |
| `UserManager` | 用户名、当前词库、主题模式、刷题记录、公告已读 | `user_manager` |
| `UserLibrary` | 错题本、收藏夹、背诵进度、自动收藏、分类顺序、自动发音 | `user_library` |
| `CheckInManager` | 打卡记录与累计统计 | `check_in` |
| `ReviewScheduler` | 间隔重复的等级与下次到期日 | `review_schedule` |
| `LibraryFileManager` / `LibraryRepository` | 导出序列化、导入校验与冲突合并 | 导出文件 |
| `AppUpdateManager` | 检查更新与更新公告 | 网络 |
| `GlassQuality` | 玻璃特效开关与低端设备档位 | `UserManager` 偏好 |

`UserManager`、`UserLibrary`、`CheckInManager` 在 `MainActivity.onCreate` 中 `init(context)`，其余单例通过 `UserLibrary.applicationContext()` 复用同一个 Context。

---

## 2. 存储结构

### SharedPreferences

| 文件 | 主要内容 |
|---|---|
| `user_manager` | 用户名、注册标记、当前词库、深色模式、刷题记录、公告内容标记 |
| `user_library` | 错题与收藏列表、自动收藏开关、分类顺序、每个词库的背诵下标、自动发音 |
| `check_in` | 每日打卡记录数组与派生统计 |
| `review_schedule` | 每个单词的等级、到期日、上次复习日、复习次数 |

写入统一经过各自的 `write*` / `save` 方法，缓存与文件同步更新；JSON 解析失败在读取处直接抛出，不做静默降级。

### 词库 SQLite

以 `words` 为主表，其余按 `word_id` 关联：

```
words(word_id, head_word, us_phone, uk_phone, star, word_rank, rem_method_val / rem_method)
trans(word_id, pos, tran_cn)
phrases(word_id, content, cn)
sentences(word_id, content, cn)
rel_words(word_id, pos, hwd, tran)
synos(word_id, pos, tran)
antos(word_id, hwd)
```

列名按名读取而非按序号，兼容不同词库的表结构差异（如 `rem_method_val` 与 `rem_method` 并存）。表是否存在通过 `tableExists` 判断，结果按连接缓存。

---

## 3. 核心流程

### 3.1 背诵评分与复习调度

1. 用户点击「不认识 / 模糊 / 认识」，`WordDetailScreen` 的 `onGrade` 先判断卡片是否已翻面，未翻面只翻面。
2. 已翻面后调用 `ReviewScheduler.record(dbName, wordId, grade)`，在 IO 线程写入等级与下次到期日：

| 评分 | 等级变化 | 间隔 |
|---|---|---|
| 不认识 | 归零 | 1 天 |
| 模糊 | 降一级（下限 0） | `2^(level-1)` 天 |
| 认识 | 升一级（上限 8） | `2^level` 天 |

3. 首页通过 `ReviewScheduler.dueWordIds(dbName)` 取今天到期且今天未复习过的单词，点击后以 `WordStudyRoute(reviewQueue = ...)` 只复习这批词。

### 3.2 测验完成上报

`QuizScreen` 监听 `uiState.isFinished`，从 `state.consumeFinishReport()` 取一次性报告，在 IO 线程完成三件事：`CheckInManager.accumulate` 累加打卡、答错单词写入错题本、`UserManager.saveQuizRecord` 保存记录。报告是一次性哨兵，同一轮只上报一次。

### 3.3 查词逐层查找

点击释义或例句中的英文单词时按四级顺序查找：

```
当前词库 → 同子分类 → 同分类的其它子分类 → 其余词库
```

- 当前词库命中：直接切到该单词，不弹窗。
- 未命中：继续逐层扫描，结果按层级顺序在弹窗中列出，最多 20 条。
- 顺序由 `LibraryCatalog.searchOrder` 生成，只包含 assets 中实际存在的词库。
- 同一词库内的排序由 `DatabaseManager.searchWords` 的 SQL 保证：精确匹配 > 前缀匹配 > 包含匹配。

### 3.4 导入导出

`LibraryFileManager.serializeToJson` 写出带 `version` 字段的 JSON，`validateJson` 校验版本与必需数组，`LibraryRepository.importData` 按 `MERGE` / `OVERWRITE` 合并；失败时回滚并重新抛出异常。协程由调用方持有，结果通过 `FancyToast` 或 `SnackbarHost` 呈现。

---

## 4. 开发约定

### 快速失败

- 数据损坏、字段缺失、词库缺表一律在出错位置抛出，不返回空列表或默认值掩盖问题。
- 例外仅限「服务端结构差异」这类真实可选字段，用安全取值并保留空串语义。

### 主线程不做事

以下操作必须在 `Dispatchers.IO` 上执行：SharedPreferences 读写、JSON 解析与序列化、数据库打开与查询、文件读写、导入导出、词库枚举。

页面内通过 `LaunchedEffect` + `withContext` 加载，事件回调通过 `rememberCoroutineScope().launch` 下发。

### Compose 性能

- 列表项使用 `LiquidGlassListItem`（半透明磨砂 + 描边，不采样背景），不要换成满配玻璃。
- 页面级玻璃统一采样 `LocalAppBackdrop`，页面自己不要再 `rememberLayerBackdrop()` 录制全屏。
- `drawBackdrop` 的参数 lambda 需要用 `remember` 稳定，否则每次重组都会重录采样。
- 高频手势事件不要逐个创建协程，改用 `Channel.CONFLATED` 收敛到常驻协程。
- 长列表的 `key` 必须全局唯一，分类名可能重复时拼上序号或子分类。

### 玻璃组件选用

| 场景 | 组件 |
|---|---|
| 整条顶栏 | `Modifier.glassTopBar(backdrop)` + `glassTopBarColors()` |
| 页面级卡片、面板 | `LiquidCard` / `LiquidGlassCard` |
| 可点击按钮 | `LiquidButton` |
| 列表项、分类标题 | `LiquidGlassListItem` |
| 底部导航 | `LiquidBottomTabs` |
| 弹窗表面 | `LiquidGlassDialogSurface` |
| 轻提示 | `FancyToast` |

形状与配色统一取 `GlassShapes`、`LocalGlassPalette`，不要就地新建 `RoundedCornerShape` 或硬编码颜色。

---

## 5. 测试

```powershell
.\gradlew.bat :app:compileDebugKotlin          # 快速编译
.\gradlew.bat :app:testDebugUnitTest           # 单元测试
.\gradlew.bat :app:assembleDebug :app:assembleRelease  # 含 R8 的完整验证
```

当前单元测试：

| 文件 | 覆盖内容 |
|---|---|
| `data/ReviewSchedulerTest` | 评分对应的等级与间隔、等级上下限、非法评分抛出 |
| `data/LibraryCatalogTest` | 查词层级顺序、去重、过滤未打包词库 |
| `data/AppUpdateManagerTest` | 版本比较、公告与版本信息解析 |
| `quiz/QuizEngineTest` | 拼写判定、出题数量与模式不变量 |

改动玻璃相关代码后必须跑 `assembleRelease`，确认 R8 未误删 backdrop 类。

---

## 6. 发布流程

1. 修改 `app/build.gradle.kts` 的 `versionCode` 与 `versionName`。
2. 同步三处更新日志：
   - `log.json`：完整更新日志（保留历史版本条目）
   - `english.json`：`latestVersion`、`downloadUrl`、`changelog`
   - `english.log.json`：单条公告结构，用于应用内公告弹窗
3. 用 release keystore 构建签名包：产物位于 `app/build/outputs/apk/release/`。
4. 在 GitHub 创建 `v<versionName>` Release，上传签名 APK。
5. 把 `log.json` 与 `english.json` 的内容同步到网站仓库 `TBssjq.github.io` 的 `version/english.log.json` 与 `version/english.json`，提交推送后 Pages 生效。

应用实际读取的是 `https://TBssjq.github.io/version/english.json` 与 `.../english.log.json`。`latestVersion` 与 `downloadUrl` 必须指向已存在的 Release，否则客户端会拿到 404。

---

## 7. 排障

| 现象 | 排查方向 |
|---|---|
| release 包玻璃全部失效 | R8 是否删除了 backdrop 类，检查 `proguard-rules.pro` |
| 某页面背景重复或采样错位 | 是否在页面内自建 `rememberLayerBackdrop()` |
| 滚动明显掉帧 | 列表项是否误用满配玻璃；`drawBackdrop` 参数 lambda 是否稳定 |
| 词库打开崩溃 | 词库名是否合法、assets 是否缺失、`words` 表是否存在 |
| 检查更新无反应或下载 404 | `version/english.json` 的版本号与 `downloadUrl` 是否指向已发布资源 |
| 公告不弹 | `english.log.json` 的 `latest` 或条目内容是否变化（按内容判定已读） |
