# English · 英语词库

一款基于 **Jetpack Compose** 与 **液态玻璃（Liquid Glass）** 视觉风格的 Android 英语单词学习应用。内置 81 个离线词库，提供词卡学习、多种测验模式、疯狂刷题、打卡成就、错题本/收藏夹与数据导入导出。

> 应用名：English ｜ 包名：`com.ssjq.english` ｜ 当前版本：2.1

---

## 功能特性

### 学习
- 词库列表按 List 分组，显示错题 / 收藏数量与背诵进度
- 单词卡片左右滑动切换（左滑下一个 / 右滑上一个），点击翻面查看释义
- 翻面后上滑查看短语例句（「短语例句记忆法」，例句中的英文单词可点击查词）
- 翻面后下滑进入拼写面板
- 一键加入错题本 / 收藏夹

### 测验
- **快速测验**：选择题、听音选词、听音拼写等多种题型
- **疯狂刷题**：可选错题本 / 收藏夹 / 指定词库出题，含评分与等级
- 测验结束后自动打卡并保存刷题记录

### 记录与成就
- 每日打卡（背诵、测验等学习行为自动累加，无需手动点击）
- GitHub 风格贡献热力图
- 累计天数 / 连续天数 / 最长连续 / 学习词数统计
- 成就徽章

### 数据与设置
- 错题本 / 收藏夹管理
- 全部数据导出 / 导入（JSON），支持按范围导出与冲突策略（覆盖 / 合并去重）
- 深色模式（浅色 / 深色 / 跟随系统）
- 应用内「检查更新」与「更新公告」

---

## 技术栈

| 项目 | 版本 / 说明 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose（BOM 2025.12.01）+ Material 3 |
| 构建 | AGP 9.3.2 / Gradle 9.7.1 |
| JDK | Java 21 |
| SDK | compileSdk 36 · targetSdk 36 · minSdk 29 |
| 玻璃效果 | `com.kyant.backdrop` 1.0.0 **源码集成**（`app/src/main/java/com/kyant/backdrop/`） |
| 网络 / 序列化 | OkHttp · Gson · org.json |

---

## 项目结构

```
app/src/main/
├─ assets/                    81 个离线词库（*.db）
├─ java/com/ssjq/english/
│  ├─ MainActivity.kt
│  ├─ data/                   数据层：数据库、用户数据、打卡、导入导出、更新检查
│  ├─ quiz/                   测验引擎与界面（出题 / 答题 / 反馈 / 成绩）
│  └─ ui/
│     ├─ AppNav.kt            导航（顶层页签 + 二级页面栈）
│     ├─ home/                首页
│     ├─ wordlist/            词库列表
│     ├─ worddetail/          单词详情 / 背诵
│     ├─ crazyquiz/           疯狂刷题
│     ├─ checkin/             打卡与成就
│     ├─ library/             错题本 / 收藏夹
│     ├─ search/              搜索
│     ├─ register/            注册（用户名 + 选词库）
│     ├─ about/               关于与检查更新
│     ├─ glass/               玻璃交互组件（按钮 / 卡片 / 开关 / 底栏）
│     ├─ common/              公共组件（LiquidGlass、导入导出对话框等）
│     └─ theme/               主题、配色、字体
└─ res/                       图标与资源
```

---

## 构建

```powershell
# 快速编译
.\gradlew.bat :app:compileDebugKotlin

# Debug 包
.\gradlew.bat :app:assembleDebug

# 完整验证（含 R8 混淆）
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```

> ⚠️ **不要改动的东西**：backdrop 库为**源码集成**，请勿换回 Maven 依赖；相关版本约束与踩坑记录见 [`TODO.md`](./TODO.md) 的「技术约束」章节。改动玻璃相关代码后务必跑 release，确认 R8 未误删 backdrop 类。

---

## 签名发布

release 签名通过项目根目录的 `keystore.properties` 配置，该文件**不纳入版本控制**（已在 `.gitignore` 忽略）。文件不存在时 release 保持未签名，不影响 debug 构建。

```properties
storeFile=D:\\keys\\your-release.jks
storePassword=你的密钥库密码
keyAlias=你的别名
keyPassword=你的别名密码
```

配置后执行：

```powershell
.\gradlew.bat :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

> 请务必妥善备份 keystore 与密码：**签名一旦更换，已安装的旧版本将无法覆盖升级**。

---

## 相关文档

| 文件 | 说明 |
|---|---|
| [`design.md`](./design.md) | 需求描述 |
| [`TODO.md`](./TODO.md) | 开发交接文档（技术约束 / 已完成 / 待办） |
| [`log.json`](./log.json) | 更新日志 |
| `english.json` | 应用内「检查更新」数据（版本 / 下载地址 / 更新说明） |
| `english.log.json` | 应用内「更新公告」数据 |

## 更新日志

见 [`log.json`](./log.json)。
