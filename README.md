# 聚合拾遗

聚合拾遗是一个面向个人资料归档和知识整理的应用，最初是 Android 应用，使用 Kotlin 与 Jetpack Compose 构建，包名为 `com.lyihub.archiveassistant`。当前以“六部”作为固定分类体系，把网页、文档和手动录入内容整理到不同主题下。

项目正在向 iOS 优先（iPhone 与 iPad）迁移：新增 `:shared` Kotlin Multiplatform 共享内核承载状态、数据与 Compose 界面，Android 部分作为参照实现保留，并在迁移完成后成为同一内核的另一个目标平台。

项目仍处在原型和功能验证阶段，README 中列出的限制不是使用说明的补充，而是当前实现状态的一部分。

## 当前能力

Android 侧（`:app`，本分支内保持冻结，作为参照实现）：

- 固定六部分类：应用内置吏部、户部、礼部、兵部、刑部、工部等主题，当前分类不可新增、重命名或删除。
- 资料归档：支持维护知识条目标题、摘要、正文、来源链接、文档格式和本地文件信息。
- 网页与文档输入：代码中包含网页抓取、文档内容提取、URI 导入、FileProvider 和本地文件处理链路。
- 智能归纳基础链路：支持配置远程 AI 引擎，并包含 OpenAI-compatible、OpenAI Responses、Gemini 等请求形态；也包含本地模型推理服务与 LiteRT LM 适配代码。
- 本地持久化：通过 DataStore Preferences 保存应用数据和 AI 引擎设置。
- Compose 界面：包含首页、分类详情、设置页、条目弹窗和折页式奏折阅读/审阅界面。

共享内核（`:shared`，本分支新增）：

- 状态层已完整迁入 `shared/src/commonMain`：`ArchiveAssistantState` 与 `ArchiveAssistantStateStore` 已不依赖任何 Android API，通过 `platformFileStore`、`BundledAssetReader`、`platformLogger`、`resolveContentSource` 等接缝获取平台能力。
- 设置页已迁入共享内核：`SettingsPane` 及其两个数据依赖（AI 端点延迟测试、AI 引擎预设）、返回按钮组件均为跨平台实现。
- 首页（仪表盘）已迁入共享内核：`shared/src/commonMain/.../ui/screens/HomePane.kt` 逐段对应 Android 版 1493 行，六个 Android 接缝（`R.drawable` 整型 id、`painterResource`、Material 图标、`System.currentTimeMillis()`、`toChineseCount`、两个书法字体常量）已全部替换为跨平台写法；设置按钮与搜索清空图标改为本地 Canvas 绘制（模块刻意不依赖 Material Icons）。
- 网络与时间已跨平台化：延迟测试改用 Ktor 与 `TimeSource.Monotonic`，预设序列化改为纯字符串读写，不再依赖 DataStore 类型。
- 资源流水线已跨平台化：美术资源与字体经由 `composeResources` 提供，`archivePainter` 与 `archiveFontFamily` 已是单一 `commonMain` 实现，不再使用 `expect`/`actual`。
- 已入包资源：28 个图形资源（约 3.2 MB，含首页 5 张 tile 的重编码版本与 10 个 XML vector）与 2 个书法字体（约 12.9 MB），打包总量约 16.1 MB。
- 目标平台：`desktop`（本机可验证的 JVM 目标）、`androidTarget`、`iosArm64`、`iosSimulatorArm64`；iOS 产物为名为 `SharedKit` 的 XCFramework。
- iOS 宿主工程：`iosApp/` 提供 SwiftUI 外壳、原生设置页、状态桥接与平台引导代码；持续集成在 macOS 上构建未签名 IPA。
- Compose 入口点已接通：`ArchiveAssistantRoot` 是首个跨平台组合根，在根组件安装 `ProvideImperialFonts`（两套书法字体自此真正生效）与 `ArchiveAssistantTheme`，并按 `state.selectedPane` 分发到已迁移的首页（`AppPane.TOPICS`）与设置页（`AppPane.SETTINGS`）；`TopicManagementDialogs` 与 Android 宿主一样挂在分发之外，否则首页的「管理 → 改名/删除」链路会断。iOS 侧由 `IosComposeRoot.makeViewController()` 提供 `ComposeUIViewController` 工厂（以 `AppPane.TOPICS` 起步），经 `ArchiveComposeHostingViewController` 承载。
- 主题已迁入共享内核：`ArchiveAssistantTheme` 现位于 `shared/src/commonMain/.../ui/theme/Theme.kt`，把 `MaterialTheme.colorScheme` 映射到皇家配色，并移植了完整 15 级字阶；浅色与深色两套配色都保留，`SettingsPane`、`HomePane` 等界面不再回落到默认紫色强调色。
- iOS 侧有截图级验证：持续集成新增 `Simulator smoke (screenshots)` 作业，在 macOS runner 上构建模拟器版本并真正启动，分别截取原生外壳、共享 Compose 界面（浅色）与共享 Compose 界面（深色）三张截图，连同 App 日志与崩溃报告一起作为构建产物上传。字体与配色是否真的渲染出来，由这三张图回答，而不再只能靠「编译通过」推断。截图文件名随预览起点变化：预览起点自首页迁移完成起为 `AppPane.TOPICS`，故为 `02/03-compose-home-*.png`；早期产物中的 `02/03-compose-settings-*.png` 来自起点仍是设置页的那一轮。

## 尚未完成与已知问题

- 共享界面在 iOS 上仍属预览态：Compose 根组件由工具栏入口以全屏预览的方式承载，尚未挂进左右两栏；两栏目前是明确标注为占位的 `UnmigratedPanePlaceholder`。原因是详情页还没迁入共享内核。
- 界面迁移尚未完成：折页式奏折阅读/审阅相关界面、详情页与奏折简报页尚未迁入共享内核。详情页包含 `LocalContext`、`BitmapFactory`、`Uri`、`rememberLauncherForActivityResult` 等 Android 专有依赖，需要先补平台抽象再接；首页已在 `shared/.../ui/screens/HomePane.kt` 完成迁移。
- 首页迁移后的两个已知限制：设置按钮与搜索清空按钮是本地 Canvas 绘制的近似字形（模块不依赖 Material Icons），不是原始矢量图标；首页只验证了编译与测试，其在 iOS 上的实际渲染仍只有截图冒烟测试覆盖，且截图只捕获首帧，点击 tile 之后的行为不在覆盖范围内。
- 奏折美术资源尚未入包：49 个被代码引用的图形资源合计约 44.9 MB，其中 41 个只被尚未迁移的奏折阅读器使用，因此按“随界面迁移一并重采样”的原则暂缓。
- iOS 无法在本机构建验证，且截图覆盖不完整：iOS 目标需要 macOS 与 Xcode，本地只能验证 `desktop` 目标，iOS 编译、Swift 侧代码与界面渲染目前全部由持续集成验证。截图冒烟测试只在 iPhone 运行时上运行，iPad、横屏以及首次呈现之后的任何交互都不在覆盖范围内；其像素检查也只能发现「画面空白或纯色」，无法判断布局是否正确，因此仍需人工看图。
- JVM 目标命名容易踩坑：本分支的 JVM 目标名为 `desktop`（`shared/build.gradle.kts` 中为 `jvm("desktop")`），对应的任务是 `compileKotlinDesktop` 与 `desktopTest`，不存在 `compileKotlinJvm` 或 `jvmTest`；引用错的名称会让构建在任务解析阶段直接失败。
- 本地模型文件选择尚未接通：设置页的「选择模型文件」按钮已绘出，但在 Compose 根组件中被刻意置空，因为还缺少原生文件选择器；点击不会有任何反应。
- AI 三省六部推荐尚未实现：当前项目有 AI 归纳与分类提示词基础，但还没有完成面向“三省六部”体系的自动推荐、排序或决策流。不要把现有智能归纳视为完整推荐系统。
- 页面滑动响应偏慢：部分页面，尤其是复杂折页阅读/审阅界面，存在滑动、翻页或手势响应不够跟手的问题，还需要继续做渲染、手势处理和重组性能优化。
- 六部分类当前固定：主题管理入口存在，但实际分类体系被固定，不能在应用内自由新增、改名或删除。
- AI 能力依赖配置：远程 AI 需要用户自行配置 Endpoint、模型和 API Key；本地 AI 需要设备、模型文件和推理后端满足运行条件。
- 项目尚未按正式产品标准收尾：仍需要补齐异常态、性能优化、更多真机测试和发布流程。

## 技术栈

- Kotlin
- Kotlin Multiplatform
- Compose Multiplatform（含 `composeResources` 资源管线）
- Jetpack Compose / Material 3
- Android Gradle Plugin
- Android DataStore Preferences
- Ktor
- OkHttp
- Jsoup
- PDFBox Android
- LiteRT LM Android

## 环境要求

Android：

- Android Studio 或可用的 Android SDK/Gradle 环境
- JDK 11 兼容环境
- Android SDK：项目 `compileSdk` 为 36，`minSdk` 为 31

共享内核：

- JDK 17（持续集成使用 Zulu 17）
- Gradle 命令需要能够写入用户主目录下的 Gradle 缓存目录，否则会因无法创建锁文件而启动失败

iOS（仅构建 iOS 产物时需要）：

- macOS 与 Xcode
- 构建产物为未签名 IPA，不涉及签名证书

## 构建与运行

### Android

克隆项目后，在仓库根目录执行：

```bash
./gradlew assembleDebug
```

安装到已连接设备或模拟器：

```bash
./gradlew installDebug
```

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

运行 Android Instrumentation 测试需要连接设备或启动模拟器：

```bash
./gradlew connectedDebugAndroidTest
```

### 共享内核

编译并运行共享内核的校验目标（本机可验证的部分）：

```bash
./gradlew :shared:compileKotlinDesktop :shared:desktopTest
```

注意 JVM 目标在本分支中被命名为 `desktop`，因此任务名是 `compileKotlinDesktop` 与 `desktopTest`，不存在 `compileKotlinJvm` 或 `jvmTest`。

构建 iOS 使用的 XCFramework：

```bash
./gradlew assembleSharedKitXCFramework
```

### iOS

iOS 产物由 `.github/workflows/ios-build.yml` 在 macOS runner 上构建，产出未签名的 IPA 作为构建产物；也可以在装有 Xcode 的 macOS 上打开 `iosApp/iosApp.xcodeproj` 构建。

该工作流包含两个并行作业：`Build unsigned IPA` 产出未签名 IPA，`Simulator smoke (screenshots)` 构建模拟器版本、安装到模拟器并实际启动截图。截图作业由 `.github/scripts/simulator-smoke.sh` 驱动，会用 `.github/scripts/check-screenshot.swift` 检查每张图是否「确实画了东西」——它只能发现空白或纯色画面，不能判断布局对错，所以产物里的截图本身才是要看的东西。在装有 Xcode 的 macOS 上可以手工复现同一路径：

```bash
xcrun simctl launch booted com.lyihub.archiveassistant --compose-preview
```

`--compose-preview` 会让 App 启动时直接进入共享 Compose 预览界面；`simctl` 可以传启动参数但点不了工具栏按钮，因此这条参数只为截图而存在，它改变的是启动位置而非渲染内容。

**`iosApp/iosApp/Info.plist` 里的 `CADisableMinimumFrameDurationOnPhone` 不能删。** Compose Multiplatform 在进程启动时会校验这一项，缺失或为 `false` 就抛 `IllegalStateException` 并让 App 直接崩溃——而且它是在 SwiftUI 外壳启动之后才生效，所以「外壳正常」并不代表共享界面没问题。它也不是纯粹的仪式：没有这一项，iOS 会在 ProMotion 机型上把 App 限制在 60 Hz。这个坑是截图冒烟测试第一次运行时抓到的，详见 `AI-Design/07-ios-migration-plan.md` 的模拟器冒烟测试一节。

**Compose 的 `compose-resources` 必须由 Xcode 构建阶段同步进 App 包，不能只靠 Gradle 编出 framework。** Compose Multiplatform 不把字体和图片放进 framework，而是用 `SyncComposeResourcesForIosTask` 拷进 App 包，插件把这个任务挂在 `embedAndSign<Framework>AppleFrameworkForXcode` 上。本项目在 `iosApp/iosApp.xcodeproj` 的「Build SharedKit.xcframework」阶段手工暂存 XCFramework、从不走那条链，因此该目录一直是空的，App 一读字体就抛 `MissingResourceException` 崩溃——**编译器、链接器与 `codesign` 都看不出这个问题**，只有真正运行才发现。现在该构建阶段会显式运行这个同步任务（任务名按 framework classifier 推导，用 `:shared:tasks --all` 在运行时发现，不硬编码），两个 CI 作业也都在构建产物上断言 `compose-resources` 里至少有 `.ttf`。

最近一次验证过的构建（提交 `4b0547c`，运行 37214783385）两个作业全绿：`Build unsigned IPA` 与 `Simulator smoke (screenshots)` 都通过，模拟器截图里能看到真实的共享 Compose 设置界面，未签名 IPA 为 23,392,233 字节。共享内核的单测报告、`xcodebuild` 日志与三张截图一并作为构建产物上传。

体积变化值得注意，而这里有一条**容易搞反的归因**：

| 提交 | 状态 | IPA 字节数 |
|---|---|---|
| `10a44a8` | Compose 根组件之前 | 5,650,070 |
| `135d2f2` | 接入 Compose 根组件 | 14,170,223 |
| `4b0547c` | 修复 `compose-resources` 同步 | 23,392,233 |

第一段涨幅（+8.52 MB）**不是字体**——那时字体根本不在 App 包里，涨的是把 Compose 运行时（UIKit/Skia 及其资源）链进 App 的开销。第二段（+9.22 MB）才是字体与图片：两个书法字体原始 12,917,432 字节、当时已入包的 12 个 drawable 约 1.48 MB，压缩后落在 `compose-resources` 里。所以「字体占了绝大部分体积」这个结论到这一步才成立，而且它的前提是资源真的被打进了包——在 `4b0547c` 之前，它们既没进包，也没人发现。首页迁移后又补入 16 个资源（28 个图形资源共 3,376,501 字节），下一轮 IPA 会在此基础上再涨。


## 项目结构

```text
app/src/main/java/com/lyihub/archiveassistant/
  app/        应用入口与整体组装
  data/       数据存取、网页抓取、文档提取、AI 请求与模型下载
  domain/     知识条目、六部主题、AI 设置和归纳接口等领域模型
  service/    本地推理前台服务与连接封装
  state/      应用状态管理与业务动作
  ui/         Compose 界面、主题、组件和自定义阅读视图
  util/       通用工具

shared/
  src/commonMain/    共享内核：状态、数据、领域模型与 Compose 界面（含 ArchiveAssistantRoot）
  src/androidMain/   Android 目标实现
  src/iosMain/       iOS 目标实现（含 IosComposeRoot / SharedStateBridge）
  src/desktopMain/   desktop 校验目标实现

iosApp/
  iosApp/            SwiftUI 外壳、原生设置页、状态桥接、Compose 承载与平台引导
  iosApp.xcodeproj/  Xcode 工程与共享 Scheme

AI-Design/           设计与迁移文档，含分阶段迁移计划与架构图
docs/                迁移相关的生成文档
.github/             持续性集成：iOS 未签名 IPA 构建、模拟器截图冒烟测试，以及配套脚本
```

## 开发重点

当前优先级建议如下：

1. 继续迁移剩余界面：按依赖顺序处理详情页（先补四个平台抽象）与奏折简报页，最后处理奏折阅读/审阅界面（含性能预算）；两栏随之从占位视图切换到真实共享界面，工具栏的预览入口届时可以删除。首页已完成迁移（`shared/.../ui/screens/HomePane.kt`）。
2. 完成 AI 三省六部推荐：明确输入、推荐目标、解释信息、失败态和人工确认流程。
3. 优化滑动与翻页性能：重点检查复杂自绘视图、动画、触摸事件处理和 Compose 重组边界。
4. 稳定导入与归纳流程：覆盖网页、Markdown、PDF、本地文件和剪贴板输入的异常处理。
5. 补齐发布前验证：增加真机性能测试、端到端用例、权限说明和发布配置。

## 许可

项目源代码按 GNU General Public License v3.0 or later 授权发布，详见 [LICENSE](LICENSE)。

第三方依赖、字体、图片、PDF 和 mock 数据保留各自原始授权。已知依赖与资源核验状态记录在 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。其中部分打包资源仍缺少完整来源和授权记录，正式发布前需要完成素材来源核验或替换。
