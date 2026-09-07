# Android 技术架构设计

## 1. 架构目标

| 目标 | 约束 |
|---|---|
| 本地优先 | 核心功能无网络、无账号可用，Room 是结构化业务数据事实来源 |
| 实时音频稳定 | 调音 DSP 和节拍调度独立于 UI、数据库和网络，不在主线程工作 |
| 可扩展内容 | 工具、课程、练习和曲谱共享稳定领域模型与关联 ID |
| 自适应 Android UI | 全 Compose，Navigation 3 统一手机、平板和折叠屏导航 |
| 隐私最小化 | 麦克风 PCM 默认只在内存中处理，不保存、不上传、不写日志 |
| 可测试 | DSP、节拍调度、进度规则和内容校验尽量为纯 Kotlin |
| 小团队可维护 | 避免过度抽象；模块围绕高风险能力和稳定业务边界拆分 |

## 2. 技术基线

- Kotlin。
- Jetpack Compose + Material 3。
- 单 Activity、edge-to-edge。
- Navigation 3 + Material 3 Adaptive Navigation 3 Scenes。
- ViewModel + StateFlow + 单向数据流。
- Room、Proto DataStore、WorkManager。
- Media3 用于课程、示范和伴奏媒体。
- AudioRecord 用于实时麦克风采集。
- AudioTrack 用于以音频帧为时间基准的节拍器。
- Hilt 用于依赖注入。
- Storage Access Framework 用于导入和导出。

具体 AGP、Kotlin、Compose、Navigation 3 和 Media3 版本在创建工程时从 Android 官方发布渠道确认并锁定，本文不硬编码可能过时的版本号。

## 3. 总体架构

```text
Compose UI
    |
ViewModel / UiState / Navigation
    |
Feature use cases and business rules
    |
Repository interfaces
    |-- Room / DataStore / private files
    |-- Media3
    |-- AudioRecord / AudioTrack engines
    |-- SAF importer
    `-- Authorized resource providers
```

数据流：

```text
User Action
  -> ViewModel
  -> Use case / Repository
  -> Data source or real-time engine
  -> Flow / StateFlow
  -> immutable UiState
  -> Compose
```

原则：

- Composable 只渲染状态并发送 Action。
- 高频 PCM 不进入 Compose State；DSP 只发布降频后的音高估计。
- Feature 不直接访问 DAO、HTTP API、AudioRecord 或文件系统。
- 简单 Repository 转发不机械包装 Use Case；有业务编排和复用价值时才创建。
- 平台异常映射为领域错误，不向 UI 泄露 SQL、HTTP 或解析器异常。

## 4. 模块设计

### 4.1 MVP 推荐模块

```text
:app

:core:model
:core:common
:core:designsystem
:core:navigation
:core:data
:core:database
:core:datastore
:core:network
:core:media
:core:audio-api
:core:audio-dsp
:core:audio-android
:core:import
:core:testing

:feature:home
:feature:onboarding
:feature:learn
:feature:practice
:feature:tuner
:feature:metronome
:feature:chords
:feature:sheets
:feature:settings
```

在线搜索在首版可以作为 `feature:sheets` 的子功能；只有规模或发布节奏证明必要时才拆为 `feature:search`。

### 4.2 职责

| 模块 | 职责 |
|---|---|
| `app` | Application、Activity、DI 入口、顶层导航、Scene 聚合 |
| `core:model` | 纯 Kotlin 领域模型和稳定 ID 类型 |
| `core:common` | Dispatcher、Clock、Result、错误、日志抽象 |
| `core:designsystem` | 主题、通用 Compose 组件、预览和尺寸规范 |
| `core:navigation` | NavKey、顶层目的地、back stack 状态 |
| `core:data` | Repository 契约与默认协调实现 |
| `core:database` | Room entity、DAO、FTS、migration |
| `core:datastore` | 用户偏好和隐私选择 |
| `core:network` | 授权资源 Provider、HTTP 客户端和 DTO |
| `core:media` | Media3 Player、MediaSession、媒体缓存 |
| `core:audio-api` | PitchInput、MetronomeEngine 等稳定接口 |
| `core:audio-dsp` | 音高检测、滤波、平滑和评分算法 |
| `core:audio-android` | AudioRecord、AudioTrack、路由与焦点 |
| `core:import` | SAF、格式识别、安全校验、文件发布 |
| `core:testing` | Fake、fixture、测试 Clock 和 Dispatcher |
| `feature:*` | 页面、ViewModel 和局部业务编排 |

依赖规则：

```text
feature -> core:data/model/designsystem/navigation/audio-api
core:data -> database/datastore/network/import/media
core:audio-android -> core:audio-api + core:audio-dsp
core:audio-dsp -> core:model
app -> feature modules
```

- `core:audio-dsp` 不得引用 `android.*`。
- Feature 之间不直接引用 Composable，跨 Feature 只使用公共 NavKey 或业务接口。
- 不为每个模块预先拆 `api/impl`，出现循环依赖或构建瓶颈后再处理。

## 5. UI 与 Navigation 3

### 5.1 路由

路由只携带稳定 ID 或小型值：

```kotlin
@Serializable data object Home : NavKey
@Serializable data object LearnRoot : NavKey
@Serializable data class LessonDetail(val lessonId: String) : NavKey
@Serializable data class ExerciseSession(val exerciseId: String) : NavKey
@Serializable data object Tuner : NavKey
@Serializable data object Metronome : NavKey
@Serializable data object SheetLibrary : NavKey
@Serializable data class SheetDetail(val sheetId: String) : NavKey
```

不通过路由传 Entity、Bitmap、大型 JSON 或原始文件 URI。详情页根据 ID 重载数据，以支持进程死亡恢复。

### 5.2 自适应导航

- 使用 `NavigationSuiteScaffold` 在紧凑窗口显示底栏，在较宽窗口显示 Navigation Rail。
- 课程、和弦和曲谱采用 `ListDetailSceneStrategy`。
- 课程配套工具、曲谱与节拍控制采用 `SupportingPaneSceneStrategy`。
- 不使用旧式 Pane Scaffold 维护第二套导航状态。
- 每个一级目的地维护独立 back stack。
- 全屏曲谱在手机可隐藏导航；进入多栏 Scene 后恢复导航与正确返回语义。

### 5.3 Compose 状态

```kotlin
data class TunerUiState(
    val permission: PermissionState,
    val status: TunerStatus,
    val targetString: GuitarString?,
    val note: NoteName?,
    val frequencyHz: Double?,
    val cents: Double?,
    val confidence: Float,
)
```

- 页面使用生命周期感知方式收集 StateFlow。
- 系统权限和 SAF 启动通过显式 Effect 通道处理。
- 音频线程不直接写 Compose State，普通协程将结果节流至约 20 至 30 Hz。
- 屏幕旋转和窗口调整不重启正在运行的练习引擎。

## 6. 本地存储

### 6.1 Room

Room 保存课程、能力、练习、进度、和弦、曲谱元数据、资源许可和本地索引。以下内容不进入 Room：

- PDF、图片、音视频等大 BLOB。
- 原始 PCM 和逐采样音高点。
- 纯 UI 临时状态。
- 网络临时响应正文。

所有已发布 Schema 使用显式 Migration，生产版本禁止 destructive migration。

### 6.2 Proto DataStore

保存：主题、动态颜色、A4 参考频率、默认调弦、调音灵敏度、默认 BPM、拍号、预备拍、节拍声音、震动、练习时屏幕常亮、在线搜索开关和隐私说明版本。

业务列表、历史记录和实体关系不得放入 DataStore。

### 6.3 文件存储

```text
files/
  imports/{documentId}/original
  content-packs/{packId}/{version}/
  user-media/{mediaId}/
  licenses/{sourceId}/

cache/
  score-render/{documentId}/{parserVersion}/
  media/
  search/
  import-temp/
```

Room 保存受控文件 ID、相对路径、SHA-256、大小、MIME、解析器版本和状态，不依赖外部真实路径。

### 6.4 WorkManager

用于可延迟、可恢复任务：内容包下载与校验、合法元数据刷新、乐库重建索引、搜索缓存和孤立文件清理。不得用于调音、节拍或用户正在等待的短时导入。

Worker 必须幂等，使用 unique work、版本化输入、指数退避和临时文件原子发布。

## 7. 调音器架构

### 7.1 生命周期

```text
进入页面
  -> 检查麦克风能力和权限
  -> 用户主动开始
  -> 创建 AudioRecord
  -> 预热并丢弃不稳定帧
  -> PCM 环形缓冲
  -> DSP
  -> PitchEstimate Flow
  -> 降频 UI 状态
  -> 离开/后台/撤权时停止释放
```

MVP 不支持后台调音，因此不创建麦克风前台服务。

### 7.2 采集建议

| 项目 | 策略 |
|---|---|
| 通道 | Mono |
| 采样率 | 查询设备支持值，优先验证 48 kHz，不假设固定可用 |
| 编码 | 支持时 PCM float，否则 PCM 16-bit |
| 来源 | 实测 `UNPROCESSED`，不可用时评估 `VOICE_RECOGNITION` 等兼容来源 |
| 缓冲 | 至少为平台最小 buffer 的整数倍并经真机调优 |
| 线程 | 专用高优先级音频线程 |
| 内存 | 预分配环形缓冲和工作区，热路径不分配对象 |

不默认开启自动增益、降噪和回声消除，避免改变吉他泛音与稳定性；最终来源以设备测试为准。

### 7.3 DSP Pipeline

```text
PCM normalization
  -> DC blocker / optional high-pass
  -> RMS and noise gate
  -> overlapping frames + window
  -> YIN or improved autocorrelation
  -> harmonic consistency score
  -> octave-error correction
  -> temporal median + exponential smoothing
  -> hysteresis and confidence gate
  -> note/string mapping
  -> cents calculation
```

初始参数只作为技术尖峰起点：

| 参数 | 起点 |
|---|---|
| 常规窗口 | 4096 samples |
| 低音增强窗口 | 8192 samples，按置信度启用 |
| Hop | 48 kHz 下约 480 至 960 samples |
| 检测范围 | 约 55 至 1760 Hz |
| UI 更新 | 20 至 30 Hz |

音分：

```text
cents = 1200 * log2(measuredFrequency / targetFrequency)
```

内部频率使用 Double。建议“调准”进入阈值为正负 3 cents、退出阈值为正负 5 cents，以迟滞减少闪烁；最终参数需由样本和真机验证。

音频线程禁止 Room、网络、文件日志、阻塞锁、对象频繁分配和无界 Channel。

## 8. 节拍器架构

### 8.1 时间基准

不得使用 `Handler.postDelayed`、`Timer`、Coroutine `delay` 或 Compose 动画作为节拍声音时钟。

MVP 使用流式 AudioTrack：

```text
MetronomeConfig
  -> sample-frame beat timeline
  -> click sample mixer
  -> preallocated PCM blocks
  -> continuous AudioTrack
  -> AudioTimestamp feedback
```

```text
framesPerBeat = sampleRate * 60 / bpm
```

不能整除时使用小数相位/误差累积，以绝对帧位置调度每拍，避免四舍五入误差随时间线性累积。视觉拍点从同一音频帧时间线推导，UI 可以丢帧但不能影响声音。

### 8.2 配置模型

```kotlin
data class MetronomeConfig(
    val bpm: Double,
    val numerator: Int,
    val denominator: Int,
    val subdivisions: Int,
    val accentPattern: List<Accent>,
    val countInBars: Int,
    val tempoRamp: TempoRamp?,
)
```

- 速度变更默认在下一拍或小节边界生效。
- Tap Tempo 使用单调时钟，排除异常间隔后计算稳健平均。
- 蓝牙路由显示延迟提示，不承诺固定常量补偿所有设备。
- 只有实测证明 AudioTrack 无法满足抖动目标时才引入 Oboe，公共接口保持不变。

## 9. Media3

Media3 负责课程音视频、示范、伴奏、音频焦点、耳机拔出、后台媒体会话和合法远程媒体缓存。它不负责麦克风采集、调音 DSP 或精确节拍调度。

- MVP 若无后台媒体，使用页面生命周期 Player。
- 后续支持后台课程音频时使用 MediaSessionService。
- 调音器启动时默认暂停扬声器媒体，避免回灌。
- 播放进度按内容 ID 节流持久化，不能每次回调都写 Room。

## 10. SAF 导入

MVP 支持优先级：PDF、常见图片、纯文本/受控和弦谱；MusicXML/MXL 是否列入首发取决于解析尖峰，不支持的格式不得在 UI 中宣传。

```text
OpenDocument
  -> metadata and stream header
  -> magic/structure detection
  -> size and decompression limits
  -> copy to private temp file
  -> SHA-256 and deduplication
  -> safe parse/preview
  -> atomic move
  -> Room transaction sets READY
```

默认复制到应用私有目录，避免源文件移动或 URI 授权撤销后不可用。链接原文件可作为后续高级选项。

安全要求：不信任扩展名/MIME；禁用 XML 外部实体；防 Zip Slip/Zip Bomb；限制文件、页数、图片尺寸和展开大小；取消或失败必须删除临时文件。

## 11. 在线资源架构

```kotlin
interface AuthorizedResourceProvider {
    val providerId: String
    val capabilities: Set<ResourceCapability>
    suspend fun search(query: ResourceQuery): Page<ResourceHit>
    suspend fun resolve(id: String): ResourceDetails
    suspend fun download(request: DownloadRequest): DownloadResult
}
```

- Provider 必须通过 API/条款、展示、许可、署名、缓存、地域、撤回和隐私审查。
- 未完成审查前使用 Fake Provider 开发，不写生产端点。
- UI 读取统一领域模型，不依赖供应商 DTO。
- 搜索结果可短期缓存允许的元数据；正文仅在许可允许时下载。
- 搜索故障不能污染本地乐库或阻塞启动。

## 12. 安全与隐私

- 只在调音或明确音高练习中申请 `RECORD_AUDIO`。
- 使用 SAF，不申请 `MANAGE_EXTERNAL_STORAGE`。
- 非必要 Activity、Service、Receiver 和 Provider 设置 `exported=false`。
- 外部 Intent 校验 action、scheme、host、MIME 和参数。
- 仅 HTTPS，release 禁止明文和敏感 body logging。
- 不记录 PCM、完整 URI、用户文件名或曲谱正文。
- 用户可删除练习历史、导入文件、缓存及全部数据。
- 大型导入和下载内容默认排除系统自动备份；备份范围在 Manifest 中显式配置。

## 13. 性能与诊断

- 冷启动不初始化音频引擎、Media3 或曲谱解析器。
- 重型组件延迟创建，退出页面后及时释放。
- 曲谱按可见页解码并设置内存缓存上限。
- Room 主查询建立索引并检查 Query Plan。
- 使用 Macrobenchmark 测启动和曲谱滚动，建立 Baseline Profile。
- Debug 本地诊断显示音频设备、采样率、buffer、DSP P50/P95、丢帧、underrun、解析器错误和存储占用。
- 发布日志只保留脱敏错误码；远程诊断必须用户同意。

## 14. 技术尖峰退出标准

- 两台以上代表性真机稳定识别 E2 至 E4，达到既定误差或形成明确降级。
- 合成与真实样本覆盖弱基频、强谐波、噪声、滑音和削波。
- 节拍器运行 30 至 60 分钟无累计漂移，正常前台负载无 underrun。
- Navigation 3 列表详情和 supporting pane 在手机、平板、折叠窗口通过原型。
- SAF 对损坏 PDF、XXE、路径穿越和压缩炸弹 fixture 安全失败且无残留。
- 冻结最低 SDK、依赖版本、首发导入格式和音频参数基线。
