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
- 网络与时间已跨平台化：延迟测试改用 Ktor 与 `TimeSource.Monotonic`，预设序列化改为纯字符串读写，不再依赖 DataStore 类型。
- 资源流水线已跨平台化：美术资源与字体经由 `composeResources` 提供，`archivePainter` 与 `archiveFontFamily` 已是单一 `commonMain` 实现，不再使用 `expect`/`actual`。
- 已入包资源：12 个图形资源（约 1.5 MB）与 2 个书法字体（约 12.9 MB），打包总量约 14.4 MB。
- 目标平台：`desktop`（本机可验证的 JVM 目标）、`androidTarget`、`iosArm64`、`iosSimulatorArm64`；iOS 产物为名为 `SharedKit` 的 XCFramework。
- iOS 宿主工程：`iosApp/` 提供 SwiftUI 外壳、原生设置页、状态桥接与平台引导代码；持续集成在 macOS 上构建未签名 IPA。

## 尚未完成与已知问题

- iOS 上暂时看不到共享界面：`ComposeHostingViewController` 目前承载的是占位内容，真实的 Compose 入口点尚未接入，因此共享内核里已迁移的界面还无法在 iOS 端显示。
- 书法字体已入包但从未生效：`ProvideImperialFonts` 至今没有任何调用方，`LocalImperialFonts` 始终持有默认的衬线字体族，因此书法排版在包括 Android 在内的所有平台上都没有真正生效过。缺少的是 Compose 根组件上的挂载点，它随真实入口点一并补齐。
- 界面迁移尚未完成：折页式奏折阅读/审阅相关界面、详情页与首页尚未迁入共享内核。详情页包含 `LocalContext`、`BitmapFactory`、`Uri`、`rememberLauncherForActivityResult` 等 Android 专有依赖，需要先补平台抽象再接。
- 奏折美术资源尚未入包：49 个被代码引用的图形资源合计约 44.9 MB，其中 41 个只被尚未迁移的奏折阅读器使用，因此按“随界面迁移一并重采样”的原则暂缓。
- iOS 无法在本机构建验证：iOS 目标需要 macOS 与 Xcode，本地只能验证 `desktop` 目标，因此共享内核的 iOS 产物目前没有本机验证记录。
- JVM 目标命名容易踩坑：本分支的 JVM 目标名为 `desktop`（`shared/build.gradle.kts` 中为 `jvm("desktop")`），对应的任务是 `compileKotlinDesktop` 与 `desktopTest`，不存在 `compileKotlinJvm` 或 `jvmTest`；引用错的名称会让构建在任务解析阶段直接失败。
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
  src/commonMain/    共享内核：状态、数据、领域模型与 Compose 界面
  src/androidMain/   Android 目标实现
  src/iosMain/       iOS 目标实现
  src/desktopMain/   desktop 校验目标实现

iosApp/
  iosApp/            SwiftUI 外壳、原生设置页、状态桥接与平台引导
  iosApp.xcodeproj/  Xcode 工程与共享 Scheme

AI-Design/           设计与迁移文档，含分阶段迁移计划与架构图
docs/                迁移相关的生成文档
.github/workflows/   持续性集成，包含 iOS 未签名 IPA 构建
```

## 开发重点

当前优先级建议如下：

1. 接入真实 Compose 入口点：用共享内核的 Compose 界面替换 `ComposeHostingViewController` 中的占位内容，同时接到 `ArchiveAssistantStateStore`，并在根组件安装 `ProvideImperialFonts`，让字体真正生效。
2. 让 iOS 持续集成跑通：工作流中校验共享内核的一步此前引用了不存在的任务名，已修正为 `compileKotlinDesktop` / `desktopTest`，需要一次真实运行确认后续 Xcode 步骤能够走完。
3. 继续迁移剩余界面：按依赖顺序处理详情页（先补平台抽象）与首页，最后处理奏折阅读/审阅界面（含性能预算）。
4. 完成 AI 三省六部推荐：明确输入、推荐目标、解释信息、失败态和人工确认流程。
5. 优化滑动与翻页性能：重点检查复杂自绘视图、动画、触摸事件处理和 Compose 重组边界。
6. 稳定导入与归纳流程：覆盖网页、Markdown、PDF、本地文件和剪贴板输入的异常处理。
7. 补齐发布前验证：增加真机性能测试、端到端用例、权限说明和发布配置。

## 许可

项目源代码按 GNU General Public License v3.0 or later 授权发布，详见 [LICENSE](LICENSE)。

第三方依赖、字体、图片、PDF 和 mock 数据保留各自原始授权。已知依赖与资源核验状态记录在 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。其中部分打包资源仍缺少完整来源和授权记录，正式发布前需要完成素材来源核验或替换。
