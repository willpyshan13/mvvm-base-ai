# Will MVVM Base

基于 Kotlin、Jetpack Compose 和多模块架构的 Android 开发基础工程，用于复用登录、网络、导航、持久化、图片、文件传输和视频等通用能力。页面通过状态和事件与 ViewModel 交互，业务通过 Repository 接口访问数据。

适合以原生 Android 为主的业务 App，也可以作为大型 App 的架构起点。当前工程提供基础设施和可运行示例；生产接入仍需要适配真实后端、业务权限、数据隔离和质量要求。它没有经过大型线上 App 的规模和流量验证。

## 技术栈与环境

| 领域 | 技术 |
| --- | --- |
| UI | Jetpack Compose、Material 3、窗口宽高自适应、AppCompat 应用内语言切换 |
| 架构 | MVVM、按功能分模块、Repository、Coroutines / Flow |
| 依赖注入 / 导航 | Hilt、Navigation 3、可序列化导航 Key |
| 网络 | Retrofit、OkHttp、统一请求结果与认证刷新 |
| 存储 | Room、DataStore、Android Keystore |
| 列表 / 后台任务 | Paging 3、WorkManager |
| 图片 | Coil 3，共享 ImageLoader、内存和磁盘缓存 |
| 视频 | 自研 FFmpeg JNI、ANativeWindow、AudioTrack |
| 构建 | Gradle Kotlin DSL、版本目录、共享 convention plugins |

开发环境：JDK 17、Android SDK API 36；最低支持 Android API 23。项目提供中文和英文静态 UI 资源，并可在设置页切换应用语言。

## 已实现的功能

### 实例应用：设备巡检

主导航新增“巡检”入口，作为基于此框架实现独立业务功能的完整本地实例。可以新建巡检记录，填写设备与位置、备注，勾选外观、运行和安全检查项，选择现场照片；记录可保存为草稿、继续编辑、完成或删除。列表可按草稿和已完成筛选。照片通过系统文档选择器取得持久读取授权，由共享图片组件显示。巡检内容由独立 Room 数据库保存，关闭应用后仍可恢复；页面由 Navigation 3 Key 和 Hilt 多绑定注册，数据访问通过 domain Repository 接口完成。可从“巡检”标签进入，也可使用 `wbase://open/inspection` 深链接。

代码按 `feature/inspection/{api,domain,data,impl}` 分层，适合作为新增业务模块的参考。巡检记录是设备本地公开示例数据，已从系统备份和设备迁移中排除；当前没有业务后端、账号归属、上传同步、审批或工单派发，退出登录也不会删除记录。现场照片引用由系统文档提供方管理，原图被移走或访问权限失效时需要重新选择。接入真实业务时须先确定账号和租户作用域，再设计同步协议及迁移，不应把本地演示数据直接视为线上巡检结果。

### 导航与页面状态

- 类型化路由，功能通过 API Key 与 Hilt 注册目的地。
- 任务、示例、设置拥有独立返回栈和可保存的页面状态。
- 登录拦截、会话恢复、退出登录后的路由更新。
- 深链接与通知跳转、可恢复的页面结果回传及消费。
- 紧凑窗口底部导航，较宽且高度足够的窗口使用侧边导航栏；页面随分屏和窗口尺寸变化重新布局。

### 登录与网络

- 登录和 Token 刷新参考适配器，可替换后端协议。
- Keystore AES/GCM 加密保存凭据，并发刷新协调，以及登录/退出的竞态保护。
- 区分 HTTP、网络、业务和非预期错误；支持无响应体成功结果。
- 公共请求头 Provider：逐次读取版本、平台、系统 API、语言、环境；业务、登录和刷新接口统一接入，接口显式请求头优先。
- 调试请求摘要有数量上限，避免记录 Token、请求体和完整 URL。
- development、staging、production 构建环境，以及显式启用的演示登录。

真实登录需要配置后端；演示登录只用于展示交互。
设置页提供个人中心、登录状态和退出入口；昵称可本机编辑并持久化，但不会同步到账号。远端用户资料同步、注册、找回密码、修改密码和注销账号需要接入实际账号服务后实现。

公共请求头由 `app/AppCommonHeadersProvider.kt` 定义：`Accept`、`Accept-Language`、`X-App-Version`、`X-App-Version-Code`、`X-App-Platform`、`X-Android-Api`、`X-App-Environment`。新增渠道、租户等参数时在 Provider 中扩展，后端需约定对应字段。Provider 每次请求调用，不能在其中执行阻塞 IO。

请求头优先级：接口显式值优先于公共默认值，名称不区分大小写。`Authorization` 仍由认证模块负责，`Content-Type` 由具体请求体决定；公共 Provider 禁止设置认证、Cookie、传输长度等保留字段。公共头只发送至 API 同源地址（协议、主机、端口相同），跨源重定向会移除这些字段。图片、视频和文件传输使用独立客户端，不自动携带业务公共头或 Token。

### 本地数据、分页与离线同步

- Room 本地任务列表与 Paging 3 分页。
- 任务示例支持新增、编辑、删除确认、完成状态、分页搜索和状态筛选；完成状态通过 Room 迁移保留。
- 独立 Feed 示例：本地缓存、持久化远端游标、离线新增/编辑、待提交队列。
- WorkManager 后台同步、临时错误退避重试、版本冲突保留并交由用户选择。
- DataStore 偏好设置、Room schema 导出。

Feed 是参考业务实现。删除同步、多账号分库和复杂多端合并需要按业务补充。未配置 API 时使用明确标识的本地演示数据源。

### UI 与系统能力

- 跟随系统、浅色、深色显示模式，以及蓝、绿、紫三套配色；选择保存到 DataStore，重启后保留。
- 应用语言可选跟随系统、中文、英文；Android 13 及以上向系统声明支持语言，旧版本由 AppCompat 保存选择。公共 API 请求头的 `Accept-Language` 跟随当前选择。
- 共享窗口宽高类别：宽度低于 600 dp 为紧凑窗口、600–839 dp 为中等窗口、840 dp 起为展开窗口；导航在空间充足时改用侧栏，列表可增加列数，主要内容在大窗口居中并限制宽度。输入页面可滚动并避开键盘；启用边到边绘制并处理系统栏图标。
- 加载、空态、错误、重试、表单、提交状态、Snackbar，以及共用的确认、信息、输入、选择和不可取消进度弹框。业务页面已经复用确认、输入和信息弹框。
- 设置页包含可编辑的本机昵称、账户状态、退出确认、帮助/反馈、隐私概要、用户协议和关于入口；反馈草稿通过系统分享面板发起，应用版本从安装包读取。隐私概要和协议说明仍需按实际产品服务审核替换。
- 运行时权限、拒绝说明、系统设置引导。
- 系统文件/图片选择、持久读取授权、FileProvider 相机拍摄。
- 通知与深链接示例。

适配依据当前应用窗口的可用尺寸，不依赖设备型号或固定屏幕像素。折叠屏铰链分隔、全部字体缩放档位、横屏极矮窗口、各种系统导航方式和无障碍体验仍需在目标设备上逐项检查；没有一种尺寸换算方式能保证所有页面自动适配。页面新增内容应使用可滚动布局、`WindowInsets`、可换行操作区和尺寸类别，不要给整屏强制固定宽高。示例数据、用户输入和后端返回文本不会随语言设置自动翻译。

公共弹框位于 `core/designsystem/components`：`AppConfirmDialog` 用于危险操作或二次确认，`AppInfoDialog` 展示可滚动说明，`AppInputDialog` 管理单行输入与错误提示，`AppChoiceDialog` 展示单选项，`AppProgressDialog` 用于必须等待的短操作。长时间任务仍应使用页面进度或通知，避免用阻塞弹框遮住整个应用。

### 图片加载与缓存

现有图片入口统一使用 Coil 3，应用启动时注册共享 `ImageLoader`，由 `AppNetworkImage` 提供页面组件。

- 内存缓存：最大堆内存的 20%，上限 64 MiB。
- 磁盘缓存：应用 `cache/image_cache`，上限 128 MiB；缓存可淘汰，也可能被系统清理。
- 缓存命中复用、按布局约束解码、渐入显示、加载状态和失败点击重试。
- 监听互联网可用状态，断网后恢复连接时自动重试失败图片。
- 支持网络地址和 Coil 支持的本地 URI；支持配置裁剪方式及关闭单次请求缓存。
- 工具箱展示内存/磁盘缓存使用量并提供清理入口；正在执行的请求可能再次写入缓存。
- 图片网络客户端独立配置连接、读取和整次调用超时，不附带业务登录凭据。

默认策略适用于公开图片。图片内容变化时应使用带版本的 URL；默认不根据 HTTP Cache-Control 做业务失效控制。敏感图片可使用 `cacheEnabled = false`，鉴权图片、账号隔离、退出清理和精确缓存失效需要按业务协议扩展。图片缓存与下载任务、视频缓存分别管理，图片没有接入下载流程的弱网速度提示。

```kotlin
AppNetworkImage(
    url = imageUrl,
    description = "商品封面",
    modifier = Modifier.fillMaxWidth().height(180.dp),
)

// AppImages 位于 core/designsystem/image。
// 以下为挂起函数，应从协程调用。
val info = AppImages.inspect(context)
AppImages.clear(context)
```

### 文件传输与网络恢复

- WorkManager 持久上传/下载任务、进度、取消和退避重试。
- 下载支持 Range + If-Range 续传，使用强 ETag 或 Last-Modified 校验远端版本。
- 服务器返回完整响应时从头下载；缺少版本校验信息时从头下载以避免错误拼接。
- 临时失败或取消保留下载片段；最终文件仅在下载完整后提交。
- 网络恢复后由 WorkManager 调度重试，受退避时间影响；自动重试耗尽后可以手动继续。
- 视频下载示例展示断网、恢复、重试、进度和弱网提示。
- 弱网提示依据低于 50 KiB/s 的采样速度或 8 秒无进度，属于提示性判断。

当前限制：HTTPS、单文件 100 MiB、8 分钟调用超时；文件保存在应用私有目录。上传是原始二进制 POST，带重试复用的幂等键，需要服务器配合。分片上传、上传续传、Multipart、鉴权下载、公开目录导出和长期前台大文件下载需要扩展。保留片段尚无自动过期清理，网络恢复也不保证立即执行。

### 视频播放

- 从 `mvvm-will-base` 迁移的自研 FFmpeg 9.0.2 解复用/软件解码内核，支持本地与 MP4/HLS/DASH 网络视频；Surface 视频输出、AudioTrack 音频输出。
- 播放进度、跳转、倍速、音频焦点、生命周期处理和位置恢复。
- 全屏与设备支持时的画中画。
- 独立 OkHttp HTTPS 本地转发与 HTTP 磁盘缓存，可设置容量，工具箱展示用量并清理；遵守服务端缓存策略，完整缓存响应支持字节区间回放，未完整读取的流不保证离线复用。
- 直接 MP4 下载后本地播放示例。

当前提供 arm64-v8a / armeabi-v7a 的软件解码，未提供硬件解码、x86 库、HLS/DASH 离线包、DRM、后台音乐播放和媒体通知会话。实现与 FFmpeg 重编方式见 [播放器说明](core/media/src/main/cpp/README.md)。

### 工程与诊断

- Gradle convention plugins、统一依赖版本、模块分层。
- Release 代码/资源压缩、环境区分、签名环境变量和发布构建工作流。
- 凭据、下载文件和崩溃记录的备份排除。
- 可替换崩溃报告接口、本地最小崩溃记录和启动耗时诊断。
- 已有单元测试、设备集成测试及可选启动性能/基线配置生成工程。

客户端 APM 已接入；线上 Collector、告警和看板需要部署并配置。测试命令与测试源码的存在，不代表所有新功能和设备场景均已验证；实际记录见 [验证记录](docs/verification.md)。

## 适合开发什么类型的 App

| App 类型 | 可以复用的能力 | 通常需要新增的业务能力 |
| --- | --- | --- |
| 企业办公、巡检、CRM、库存管理 | 登录、表单、权限、拍照、离线编辑同步 | 组织角色、审批流、业务数据库与冲突规则 |
| 内容资讯、社区、知识库 | 分页列表、图片缓存、详情导航、视频 | 评论、搜索、推送服务、内容审核 |
| 电商、商品目录、会员服务 | 图片列表、登录、网络、持久化 | 购物车、订单、支付 SDK、交易状态一致性 |
| 工具、任务、个人效率 | 本地列表、设置、文件选择、通知 | 提醒调度、导入导出、云同步规则 |
| 培训、课程、轻量视频业务 | 播放器、缓存、MP4 离线下载 | 课程体系、学习进度、版权鉴权、DRM |
| 现场作业、间歇联网业务 | Room、待提交队列、网络恢复下载 | 账号隔离、长期同步策略、增量协议、片段清理 |

即时通信、实时音视频会议、重度直播、地图导航、复杂图像编辑和游戏需要独立的专业 SDK/引擎与业务架构。本工程可以承担其登录、导航、设置等外围业务，但核心能力尚未提供。本项目是原生 Android 工程，不直接产出 iOS App。

## 是否适合大型 App

**适合作为大型业务 App 的基础工程，生产使用前需要继续建设。** 多模块、功能 API、领域接口、共享基础设施和依赖注入有助于团队并行开发和控制模块耦合。能否承载大型 App，还取决于实际模块依赖、数据量、页面性能、团队规范和发布治理。

大型项目仍需结合真实业务和发布平台补齐：

1. **业务和数据边界**：已有统一会话清理契约；真实多账号/租户数据仍需在各业务表和缓存中引入账号作用域、删除规则、增量同步、幂等和冲突协议。当前任务和 Feed 示例没有账号字段，属于公开本地数据，退出登录不会误删。
2. **模块治理**：构建时已有依赖方向和循环检查；仍需明确模块所有权，并按规模拆分共享模块。当前图片管理暂与 designsystem 同模块，规模增长时可独立为图片基础设施模块。
3. **线上观测**：崩溃、ANR、内存、卡顿、网络成功率、埋点与隐私策略，形成可追踪的发布质量指标。
4. **性能和质量**：用真实长列表、图片、数据库和弱网负载测量；补足关键链路自动化、迁移、进程重建、兼容性和无障碍覆盖。
5. **交付治理**：已有 PR 依赖审查、Release 依赖锁、运行时功能开关和产物大小预算；灰度发布、实际回滚操作、线上指标准入和性能设备基线需要接入真实发布平台与质量数据源。
6. **专项基础设施**：按产品需要接入推送、支付、地图、实时连接、前台传输服务、下载任务列表和配额管理。

仓库尚无大型生产环境的规模实测，因此不承诺特定用户数、页面数或并发容量。网络并发容量还依赖后端；Android 客户端应重点评估启动、帧率、内存、电量、存储和任务可靠性。

### 大型 App 基础架构治理

- **组件通信**：页面由 feature 的 `api` 模块暴露 `NavKey`，`impl` 通过 Hilt 注册入口；返回值使用 `NavigationResultKey<T>` 声明稳定、带命名空间的 ID 和序列化类型。可参考 `ToolboxResult`，调用 `navigator.result(ToolboxResult)`、`returnResult(ToolboxResult, value)` 和 `consumeResult(ToolboxResult)`。结果保存在当前导航宿主的可恢复状态中，登出时清空；跨功能业务调用继续使用 domain 接口和 Hilt 实现。
- **依赖边界**：`checkModuleBoundaries` 在各模块的 `check` 阶段运行，拒绝 core 反向引用 feature、跨 feature 引用对方 data/impl、逆向层级依赖及项目依赖环。新增模块仍需在 `settings.gradle.kts` 注册并遵守 `api/domain/data/impl` 方向。
- **账号生命周期**：`feature:auth:domain` 的 `SessionDataCleaner` 是 feature 私有数据的清理契约。实现类用 Hilt `@IntoSet` 注册后，登出和会话失效会触发清理；新的登录会等待清理完成。当前公开的任务/Feed 示例不会清理。将来接入账号私有表时，应先增加账号键和迁移，再注册对应清理器。
- **运行时开关**：独立的 `core:config` 从 `-PfeatureFlagsUrl=https://...` 指定的公开 HTTPS 地址每 15 分钟拉取一次，离线使用同一 URL 最近一次有效配置；未配置地址时使用默认值。当前支持 `feed.sync` 与 `media.video_player`，分别控制 Feed 后续同步执行和播放器页面。响应格式为 `{"features":{"feed.sync":false,"media.video_player":false}}`。只用于非敏感功能降级，不能代替服务端权限校验。
- **依赖与发布**：应用的 Release 运行时依赖记录在 `app/gradle.lockfile`，升级依赖后需有意识地用 Gradle `--write-locks` 更新并审查差异；PR 使用 GitHub Dependency Review 阻止新增高危依赖，Dependabot 每周提出更新。Release 工作流要求生产 API/APM 地址，并对既有 AAB 执行 80 MiB 大小预算检查；`tools/quality/release_gate.py --metrics report.json` 还能对外部采集的崩溃率、ANR 率、启动/帧耗时和样本数做阈值检查。仓库不能自行读取应用商店灰度状态或线上指标，发布与回滚需要连接相应平台。

更新 Release 运行时依赖锁的命令是 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath --write-locks`；完成后审查 `app/gradle.lockfile` 的版本变化。该命令只解析依赖，不生成安装包。

质量报告示例（比率按 0 到 1 填写，耗时为毫秒）：

```json
{
  "sample_count": 1000,
  "crash_rate": 0.002,
  "anr_rate": 0.001,
  "startup_p95_ms": 1800,
  "frame_p95_ms": 24
}
```

对已有发布文件执行 `python3 tools/quality/release_gate.py path/to/app-release.aab --metrics report.json`。当前阈值位于该脚本中，接入真实设备性能实验和线上指标后应按产品质量目标调整。Release 工作流只生成并校验签名产物，不自动上架；灰度发布、监控和回滚仍由发布平台执行。

## APM 与线上监控

线上观测集中在独立的 `core:apm` 模块，基于与当前构建工具链兼容的 OpenTelemetry Android Agent 1.4.0（BOM 1.4.0-alpha）和 OTLP/HTTP，不绑定 Firebase 或特定商业平台。AGP ASM 方法插桩插件由 `build-logic` 的 Android application/library convention plugins 自动注册。当前客户端功能包括：

- OpenTelemetry Android Agent 默认提供 Java/Kotlin 未处理崩溃、ANR、应用启动、Activity/Fragment 生命周期、慢帧/冻结帧、设备网络变化、屏幕方向和会话采集。上游 View 点击采集默认未启用，Compose 点击采集也未接入；业务操作可通过 `AppApm.recordEvent` 手动记录。
- 在主导航记录页面访问事件，供页面访问量和会话路径分析。
- 统一 Retrofit/OkHttp、断点文件传输、Coil 图片客户端和 FFmpeg HTTPS 代理的上游请求的时长、请求量、错误量、传输体积、客户端类型、主机和可用的 HTTP 状态，并为第一方 API 传递 W3C `traceparent`，便于和已接入 OpenTelemetry 的服务端链路关联。通过 `traceSuspend` 发起的 API 请求也会作为业务 trace 的子 span。为减少隐私暴露，APM 不记录请求路径、查询参数、请求头或请求体内容；认证刷新请求也通过相同网络客户端纳入观测。图片客户端只请求公开资源，不附带应用凭据；图片 CDN、文件主机和视频 CDN 只会记录 HTTPS 请求性能，不会收到 trace header。FFmpeg 代理的 HTTPS 上游请求复用公共 HTTP 观测接口，客户端类型为 `media`，缓存命中不会记作网络传输。
- 提供手动异常、结构化日志、事件、页面访问、计数器、数值直方图和业务 trace API；trace 可附带经过隐私策略过滤的属性，同步与挂起函数均能记录耗时和异常，协程切换时会保留 trace 上下文。最多保留 50 条内存 breadcrumbs，网络请求结果和页面访问会进入轨迹，并附加到手动上报的异常。
- 前台低频采集进程 PSS、Java/Native 堆、系统可用/总内存、进程 CPU 占比、电池电量/状态、可用/总存储以及节电和温控状态；默认每分钟采样一次，切到后台时停止，可通过 `ApmConfiguration` 关闭或调整间隔。温控状态仅在 Android 10 及以上采样，电池状态仅在 Android 8 及以上采样。
- 自有诊断监控补充 Activity 首帧耗时、前台帧间隔/慢帧/冻结帧窗口事件和主线程无响应疑似 ANR 堆栈；ANR 阈值默认为 5 秒。Debug 变体接入 LeakCanary 检查 Activity、Fragment、View 和 ViewModel 保留对象并分析堆引用链；该工具不会进入 Release 变体。
- `AppApm.trackIo` / `trackFileIo` 可包裹文件或其他受控 I/O 操作，记录耗时、主线程调用和慢操作；Room 数据库已安装 SQL 回调，可检测 `SELECT *`、无 WHERE 的 UPDATE/DELETE、AUTOINCREMENT、`INSERT OR REPLACE` 和前置通配符 LIKE，只上报规则 ID，不上传 SQL 或绑定参数。
- **自动插桩范围**：构建时 AGP 会自动处理项目 class，但只有显式标注 `@ApmMonitoredMethod` 的同步方法会插入监控逻辑；未标注的方法不会采集。当前接入示例是认证 token 存取。可为方法设置慢方法阈值，默认 16 毫秒。插桩记录方法耗时、慢方法和抛出的异常。构造器、静态初始化器、native 方法、抽象方法和 Kotlin `suspend` 方法会跳过；挂起函数请用 `traceSuspend`。这不是全量方法自动追踪。
- `AppApm.inspectBitmap(bitmap, owner)` 可在调用点显式检测相同像素内容的存活 Bitmap，限制扫描到 262,144 像素并只保留弱引用；它不会自动拦截 Coil，硬件 Bitmap 和超大图片会跳过。Debug 启动时启用 StrictMode 的 Closeable/Cursor 泄漏检查，保留原有 VM policy。
- 前台运行快照另含进程线程数与 Native 堆用量；可通过 `-PapmGwpAsanMode=always` 在 Android 支持的系统启用 GWP-ASan 抽样检测 Native 堆内存越界/释放后使用。默认沿用系统策略；该能力不是 Native 泄漏归因或调用栈 hook。
- `tools/apm/apk_inspector.py` 可分析一个已经存在的 APK，输出体积较大的文件、DEX 方法/类计数、多 ABI、未压缩资源、无透明通道 PNG、重复文件内容和 DEX 字符串资源可达性候选；可用 `--html report.html` 输出 HTML。资源候选只是启发式，不取代 Android Lint、R8 或资源表分析。工具不会生成 APK。
- 启用 SDK 默认本地遥测磁盘缓冲，网络恢复后周期性发送；后台会话超时为 15 分钟，单个会话最长 4 天。网络离线时暂存不代表永久不丢，超过缓存上限、用户清数据或卸载应用时旧遥测可能被清理。
- 给遥测附上应用版本和运行环境。自定义属性最多保留 16 个字段、每个值不超过 96 个字符，并丢弃包含邮箱、URL、Bearer token 和长数字的值。结构化日志消息限 512 字符并做相同敏感值脱敏。

实现按职责拆分在 `core/apm`：`ApmConfiguration` 管采集配置，`ApmRuntime` 管理 SDK 初始化，`ApmRuntimeMetrics` 管前台设备/进程指标，`ApmRecorder` 管业务埋点与结构化日志，`ApmNetworkInstrumentation` 管 API 和图片 OkHttp 客户端，`ApmPrivacy` 统一处理自定义数据策略，`OtelContextElement` 维持协程 trace 上下文，`AppApm` 提供稳定的调用入口。`core:network` 只保留通用网络扩展点，APM 在 `app` 组装时接入。

通过 `-PapmEndpoint=https://your-otel-collector.example:4318` 配置 OTLP Collector 地址。配置 endpoint 后默认启用 APM；可通过 `-PapmEnabled=false` 在某个构建中关闭，设置 `-PapmEnabled=true` 时必须同时配置 endpoint。GitHub Release workflow 从仓库变量 `APM_ENDPOINT` 读取地址。未配置时不初始化远程采集，应用仍可启动，本地崩溃诊断照常保留。服务器需自行部署兼容 OTLP 的 Collector 和可观测性后端；客户端 SDK 不提供接收服务器或仪表盘。Release workflow 会把 R8 映射文件与 AAB 一起归档 90 天；后端解混淆需另行集成。

GWP-ASan 使用 `-PapmGwpAsanMode=default|always|never` 选择系统默认、启用或关闭；默认是 `default`。仅在支持的 Android 版本生效，属于系统抽样诊断，开启后命中内存错误时可能导致进程退出。Bitmap 内容检查会计算像素摘要，可能消耗 CPU，因此只应在适合的解码/缓存调用点使用；最大检查尺寸为 262,144 像素。

接入前应按产品隐私政策完成用户告知、授权和数据保留策略。不要把长期服务端密钥嵌入 APK；公网 Collector 应经网关限流。异常堆栈可能包含敏感上下文，自定义 trace 名称和属性也不要放用户输入、账号标识或业务主键。

```kotlin
AppApm.recordException(error)
AppApm.recordLog(ApmLogLevel.ERROR, "checkout failed", mapOf("feature" to "checkout"))
AppApm.recordEvent("checkout.completed", mapOf("payment.method" to "card"))
AppApm.recordBreadcrumb("checkout.submit", mapOf("step" to "confirm"))
AppApm.incrementCounter("app.checkout.completed")
AppApm.recordMeasurement("app.image.decode.duration", 0.042, "s")
AppApm.trace("checkout.submit", mapOf("checkout.flow" to "express")) { submitOrder() }
AppApm.traceSuspend("feed.refresh", mapOf("feed.type" to "recommended")) { repository.refresh() }
AppApm.inspectBitmap(bitmap, owner = "profile.avatar")
```

标注需要观测的同步方法：

```kotlin
@ApmMonitoredMethod(slowThresholdMillis = 25)
fun readSession(): AuthSessionTokens? {
    // method body
}
```

这是一套客户端 APM 接入层，不等于包含所有 APM 产品能力。方法插桩只作用于显式标注的方法；Room 规则是静态 SQL 启发式检查，不提供 EXPLAIN/索引质量；Bitmap 检查需调用方显式接入且会跳过硬件/超大图片；Closeable/Cursor 检测仅 Debug StrictMode；GWP-ASan 是系统抽样内存安全检查，不是 Native 泄漏调用栈 hook。APK 资源可达性报告也只是 DEX 字符串候选。ANR watchdog 不能替代系统 ANR trace，帧指标不提供精确卡顿调用栈。IO 监控需调用方用 `trackIo` / `trackFileIo` 包裹操作。默认不自动桥接 Logcat、不做连续 profiler 或 Session Replay，也没有自动收集每次 Compose 点击。CPU/内存是低频前台快照；breadcrumbs 只附加到手动调用 `recordException` 的异常。采样策略、告警规则、仪表盘、数据保留、R8 符号化和后端 trace 关联由后端配置。

实现参考：[OpenTelemetry Android 官方文档](https://opentelemetry.io/docs/platforms/client-apps/android/)、[Android Agent 源码与说明](https://github.com/open-telemetry/opentelemetry-android)。

## 模块结构

| 模块 | 职责 |
| --- | --- |
| `app` | 应用启动、Hilt 组装、环境配置、根导航、认证后端选择 |
| `core/common` | Android、网络连接状态、格式化、文本与日志辅助 |
| `core/network` | HTTP、认证刷新、统一结果和网络诊断 |
| `core/designsystem` | 主题、共享 Compose UI、权限组件、图片加载与缓存管理 |
| `core/navigation` | Navigator 与功能入口契约 |
| `core/media` | 播放器、生命周期、播放状态和视频缓存 |
| `core/platform` | 文件传输、通知、本地崩溃诊断 |
| `core/apm` | OpenTelemetry 移动端性能、崩溃与网络观测 |
| `core/config` | 公开运行时功能开关、HTTPS 拉取和离线缓存 |
| `feature/*/api` | 可序列化导航 Key |
| `feature/*/domain` | 不依赖 Android 的业务模型和 Repository 契约 |
| `feature/*/data` | 数据库、网络适配与依赖注入 |
| `feature/*/impl` | Screen、ViewModel、导航入口 |
| `feature/toolbox` | 能力示例和调试工具 |
| `feature/inspection` | 设备巡检业务实例：导航、领域模型、Room 仓库、Compose 页面 |
| `benchmark` | 可选性能测量和 baseline profile 生成 |
| `build-logic` | 共享 Gradle 构建约定 |

新增功能建议遵循 `api/domain/data/impl` 分层，按实际需要创建模块；跨功能通过 API 契约或导航 Key 通信。业务操作放入 ViewModel / Repository，页面负责状态展示和事件传递。Room schema 变化需要添加迁移与对应验证。

## 运行与验证

在仓库根目录执行：

```sh
./gradlew :app:installDebug
# 使用演示登录：demo / demo1234
./gradlew :app:installDebug -PdemoAuth=true

# 配置测试环境后端；Retrofit base URL 需以 / 结尾
./gradlew :app:assembleDebug -PappEnvironment=staging -PapiBaseUrl=https://api.example.com/

# 构建、单元测试与 lint
./gradlew build
./gradlew :core:network:test :feature:status:data:testDebugUnitTest
./gradlew lint
```

演示登录使用独立 `.development.demo` 应用标识，只在显式启用的 debug 场景使用；production 禁止启用，演示 Token 不发送到实际 API。

设备测试需连接设备或模拟器：

```sh
./gradlew :app:connectedDebugAndroidTest \
  :feature:home:data:connectedDebugAndroidTest \
  :feature:settings:data:connectedDebugAndroidTest \
  :feature:feed:data:connectedDebugAndroidTest \
  :feature:auth:data:connectedDebugAndroidTest \
  :core:media:connectedDebugAndroidTest \
  :core:platform:connectedDebugAndroidTest

./gradlew :app:connectedDebugAndroidTest -PdemoAuth=true --no-parallel
```

演示登录端到端用例需要显式 demo 标志，否则跳过。新增图片缓存与网络恢复逻辑仍需实际设备验证离线命中、恢复重试、缓存淘汰和清理行为。

生产构建：

```sh
./gradlew :app:bundleRelease -PappEnvironment=production -PversionCode=2 -PversionName=1.0.1
```

签名通过 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 注入，不将凭据提交到仓库。生产 API、签名和业务服务需要自行配置。

后端协议、扩展点、签名和性能命令见 [框架接入指南](docs/framework-guide.md)；实际完成的检查见 [验证记录](docs/verification.md)。
