# alphaTab 统一曲谱渲染设计方案

## 1. 文档状态

| 项目 | 内容 |
|---|---|
| 状态 | 已确认方案 |
| 适用平台 | Android、PC/Web、Electron |
| 核心引擎 | alphaTab |
| 统一渲染后端 | SVG |
| 自定义能力 | 数字简谱、中文歌词、多行歌词、播放高亮 |
| 目标谱面 | 吉他六线谱 + 节奏 + 和弦 + 数字简谱 + 中文歌词 |
| 不包含 | 二维码、Logo、水印等图片装饰层 |

本文确定 XStar Guitar 的曲谱渲染实现方式。Android 和 PC 使用同一套 TypeScript/JavaScript 曲谱运行时、同一套 alphaTab 配置和同一套 SVG 渲染逻辑。Android 通过 WebView 承载该运行时，不再单独维护 Android Canvas 渲染器。

---

## 2. 设计目标

### 2.1 必须支持

- 吉他六线谱和品位数字。
- 多根琴弦同时发声。
- 空弦 `0`、闷音 `x`、休止符。
- 4/4、6/8 等拍号和不同音符时值。
- 符干、连梁、小节线、反复记号和结尾记号。
- 和弦名称与和弦指法图。
- 击勾弦、滑音、延音、连音、推弦、泛音等 alphaTab 支持的吉他技巧。
- 数字简谱、附点、高低音点、时值线和连接线。
- 中文歌词、一音一字、延长音和多行歌词。
- 自动换行、响应式宽度、缩放和纵向滚动。
- 播放光标、当前音符/简谱/歌词高亮和自动跟随。
- SVG 导出，并为 PNG/PDF 导出保留扩展能力。

### 2.2 明确不做

- 不把谱面作为静态截图保存或渲染。
- 不把二维码、Logo、水印作为曲谱核心模型或渲染层。
- 不为 Android 和 PC 分别实现一套排版算法。
- 不在业务层直接依赖 alphaTab 的内部 DOM 结构。

---

## 3. 总体架构

```text
Guitar Pro / AlphaTex / MusicXML / Score JSON
                      │
                      ▼
              Score Import Adapter
                      │
                      ▼
        alphaTab Score + Jianpu Metadata
                      │
                      ▼
             Unified Score Runtime
       ┌──────────────┼──────────────┐
       │              │              │
       ▼              ▼              ▼
  alphaTab Core   Jianpu Layout   Lyric Layout
  六线谱/节奏/和弦  数字简谱布局      中文歌词布局
       └──────────────┼──────────────┘
                      ▼
              Extended SVG Renderer
                      │
              ┌───────┴────────┐
              ▼                ▼
          PC/Web          Android WebView
                      │
                      ▼
               alphaSynth 播放
```

### 3.1 核心原则

1. **alphaTab 是六线谱和播放的事实来源**：音符、拍号、时值、和弦、技巧和播放位置不在自定义层重复计算。
2. **自定义层只扩展缺失的表现形式**：数字简谱和中文歌词通过 `measure + tick` 绑定到 alphaTab 的 Beat。
3. **SVG 是唯一渲染输出**：PC 和 Android 加载同一份 SVG 结构，不存在 Canvas 与 SVG 两套视觉实现。
4. **布局与宿主解耦**：排版、SVG 节点、播放高亮和命中测试位于共享包；Android 原生只负责 WebView 容器和系统能力。
5. **alphaTab 版本固定并集中适配**：通过 Adapter 隔离 alphaTab 版本差异，禁止业务代码散落调用内部 API。

---

## 4. alphaTab 的职责边界

| 能力 | 实现方式 |
|---|---|
| Guitar Pro 3-7 导入 | alphaTab 解析器 |
| AlphaTex 导入 | alphaTab 解析器 |
| MusicXML 导入 | alphaTab 解析器，按支持范围验证 |
| 六线谱 | alphaTab |
| 节奏符干、连梁 | alphaTab |
| 空弦、闷音、休止符 | alphaTab |
| 拍号、速度、小节和反复 | alphaTab |
| 和弦名称、基础和弦图 | alphaTab，必要时自定义样式 |
| 吉他演奏技巧 | alphaTab 支持范围内由 alphaTab 渲染 |
| 数字简谱 | `JianpuSvgRenderer` |
| 高低音点、简谱时值线 | `JianpuSvgRenderer` |
| 多行中文歌词 | `LyricSvgRenderer` |
| 简谱/歌词播放高亮 | 共享 `PlaybackEventBus` |
| 页面装饰图片 | 不实现 |

alphaTab 不是国内数字简谱排版引擎，因此不通过伪造五线谱或修改音符含义来表达简谱。简谱是独立的语义扩展，但必须使用 alphaTab 的时间轴和系统布局结果。

---

## 5. 数据模型

### 5.1 外部曲谱格式

输入格式按以下优先级处理：

1. **Guitar Pro**：正式内容的首选格式，保存多声部、节奏、和弦、技巧、歌词和播放信息。
2. **AlphaTex**：程序生成、开发测试和内置简单示例的首选格式。
3. **MusicXML**：兼容性导入格式，导入后必须经过能力校验。
4. **Score JSON**：应用自己的包装格式，用于保存简谱、中文歌词、样式和来源元数据。

仓库当前已有 alphaTab 资源和示例曲谱：

```text
docs/vendor/alphatab/alphaTab.min.js
app/src/main/assets/songs/zui_lang_man_de_shi.alphaTex
```

后续应将 alphaTab 版本、字体、SoundFont 和示例曲谱纳入明确的资源版本清单。

### 5.2 Score JSON

```typescript
interface ScoreDocument {
  version: number;
  source: ScoreSource;
  alphaTabSource: AlphaTabSource;
  jianpu?: JianpuTrack[];
  lyrics?: LyricLine[];
  style: ScoreStyle;
}

interface ScoreSource {
  id: string;
  title: string;
  author?: string;
  licenseId?: string;
}

interface AlphaTabSource {
  type: "guitar-pro" | "alphatex" | "musicxml";
  path?: string;
  text?: string;
  sha256?: string;
}
```

### 5.3 统一时间位置

所有可视元素和播放元素使用同一个位置模型：

```typescript
interface ScorePosition {
  measureIndex: number;
  tick: number;
}
```

简谱事件：

```typescript
interface JianpuEvent {
  position: ScorePosition;
  value: string;
  octaveOffset?: number;
  durationTicks: number;
  dotted?: boolean;
  tie?: boolean;
  lyric?: string;
}
```

歌词事件：

```typescript
interface LyricEvent {
  position: ScorePosition;
  text: string;
  durationTicks?: number;
  melisma?: boolean;
}

interface LyricLine {
  lineIndex: number;
  events: LyricEvent[];
}
```

### 5.4 数据约束

- `measureIndex + tick` 必须可以定位到 alphaTab 的 Beat 或其可接受的时间区间。
- 简谱事件不得自行建立与 alphaTab 不同的 BPM 或拍号系统。
- 同一时间点的多根琴弦由 alphaTab 的 Beat/Voice/Note 表达。
- 歌词换行只改变显示行，不改变事件时间位置。
- 原始导入文件、解析器版本和哈希值由曲谱资源层保存，便于重建和排查。

---

## 6. 统一 SVG 渲染

### 6.1 渲染层

最终 SVG 使用稳定的图层结构：

```xml
<svg class="score-document">
  <g id="score-background" />
  <g id="system-0">
    <g id="chord-layer" />
    <g id="time-signature-layer" />
    <g id="tab-layer" />
    <g id="rhythm-layer" />
    <g id="jianpu-layer" />
    <g id="lyrics-layer" />
    <g id="barline-layer" />
    <g id="interaction-layer" />
  </g>
  <g id="playback-layer" />
</svg>
```

alphaTab 原生元素保留其语义；自定义节点必须增加稳定 ID 和位置属性：

```xml
<text
  id="jianpu-m2-t480"
  class="jianpu-note"
  data-measure="2"
  data-tick="480"
  x="320"
  y="610">6</text>
```

### 6.2 平台无关 SVG 节点模型

自定义渲染器不直接操作浏览器 DOM，而是先生成平台无关的 SVG 节点描述：

```typescript
interface SvgNode {
  type: "group" | "text" | "line" | "path" | "rect" | "circle";
  id?: string;
  attributes: Record<string, string>;
  children?: SvgNode[];
}
```

这样可以：

- 在浏览器和 Android WebView 使用同一套代码。
- 对 SVG 输出做快照测试。
- 在不修改布局算法的情况下增加 PNG/PDF 导出。
- 将 DOM 事件和渲染逻辑隔离。

### 6.3 布局顺序

每次渲染按以下顺序执行：

1. alphaTab 解析输入并生成 Score。
2. alphaTab 计算小节、系统和 Beat 的布局。
3. 读取共享的 Measure/Beat 位置索引。
4. 计算简谱行、时值线、歌词行和碰撞空间。
5. 将简谱与歌词节点添加到对应 System。
6. 添加稳定的交互节点和播放节点。
7. 输出完整 SVG。
8. 建立 `ScorePosition -> SVG Element` 索引。

初期可以使用受控的 alphaTab Layout Adapter 读取布局结果；不得在业务层查询任意 CSS 坐标。长期若需要出版级紧凑排版，再将简谱布局作为 alphaTab 扩展 Renderer 的一部分维护。

---

## 7. 两类目标谱面样式

### 7.1 简谱主导型

```text
和弦/和弦图
扫弦与六线谱
数字简谱
中文歌词
```

### 7.2 六线谱主导型

```text
和弦/和弦图
拍号
节奏六线谱
品位数字、空弦、闷音
数字简谱
多行中文歌词
```

通过样式配置控制，不为两种谱面创建两套引擎：

```typescript
interface ScoreStyle {
  showTab: boolean;
  showRhythm: boolean;
  showJianpu: boolean;
  showLyrics: boolean;
  lyricLineCount: number;
  showChordName: boolean;
  showChordDiagram: boolean;
  tabToJianpuGap: number;
  jianpuToLyricsGap: number;
  minimumBeatWidth: number;
}
```

二维码、Logo、水印不进入该配置，也不进入 SVG 输出。

---

## 8. 播放、光标和自动滚动

### 8.1 播放链路

```text
alphaTab Score
      ▼
alphaTab Playback / alphaSynth
      ▼
Web Audio（PC）或 WebView 音频输出（Android）
```

第一阶段使用 alphaSynth 验证播放、速度、循环和跟随能力。后续如需更真实的吉他音色，可以替换音频输出，但不得重新计算谱面时间轴。

### 8.2 播放事件总线

```typescript
interface PlaybackPosition {
  measureIndex: number;
  tick: number;
  timeMs: number;
}
```

```text
alphaTab 播放位置事件
          ▼
PlaybackEventBus
   ├── 六线谱光标
   ├── 数字简谱高亮
   ├── 歌词高亮
   ├── 和弦提示
   └── 自动滚动
```

播放高亮优先修改已生成 SVG 节点的 class 或属性，不在每个播放帧重排整份曲谱：

```css
.score-note.active,
.jianpu-note.active,
.lyric-syllable.active {
  fill: var(--score-accent);
  font-weight: 700;
}
```

### 8.3 控制接口

```typescript
interface PlaybackController {
  play(): void;
  pause(): void;
  stop(): void;
  seek(position: ScorePosition): void;
  setTempo(bpm: number): void;
  setLoop(start: ScorePosition, end: ScorePosition): void;
}
```

播放高亮和自动滚动全部在共享 Web 运行时中完成。Android 原生不接收高频播放位置事件，避免 WebView 与 Kotlin 之间产生不必要的通信压力。

---

## 9. PC 和 Android 集成

### 9.1 PC/Web

```text
React + TypeScript
        ▼
score-runtime
        ▼
alphaTab + Extended SVG Renderer
        ▼
浏览器 SVG / alphaSynth
```

PC 宿主只负责页面、文件选择、快捷键、窗口和播放控制。

### 9.2 Android

```text
Kotlin + Compose 外层页面
        ▼
WebView
        ▼
与 PC 相同的 score-runtime 资源包
        ▼
与 PC 相同的 alphaTab SVG 和播放逻辑
```

Android 原生职责：

- WebView 生命周期和状态恢复。
- 本地文件选择和 SAF URI 传递。
- 横竖屏、返回键和系统窗口管理。
- 音频焦点、应用后台和资源释放。
- 与共享 JavaScript 运行时的低频命令通信。

Android 不实现：

- 六线谱 Canvas Renderer。
- 数字简谱 Canvas Renderer。
- 独立的小节换行算法。
- 独立的播放高亮算法。

### 9.3 WebView 资源策略

- 曲谱运行时、alphaTab、字体和必要 SoundFont 随 APK 离线打包。
- 默认不依赖远程脚本、远程字体或远程曲谱资源。
- WebView 只允许受控的本地资源访问。
- PC 与 Android 使用相同的资源版本清单。
- 曲谱内容变化只重新生成 SVG，不复制一套 Android UI 资源。

---

## 10. 建议模块结构

```text
score-platform/
├── packages/
│   ├── score-model/
│   ├── score-import/
│   ├── alphatab-adapter/
│   ├── score-layout/
│   ├── score-svg-renderer/
│   ├── score-playback/
│   └── score-runtime/
├── apps/
│   ├── pc/
│   └── android-webview/
└── assets/
    ├── alphatab/
    ├── fonts/
    └── soundfonts/
```

建议职责：

| 包 | 职责 |
|---|---|
| `score-model` | Score JSON、时间位置、简谱和歌词模型 |
| `score-import` | Guitar Pro、AlphaTex、MusicXML 和 JSON 导入 |
| `alphatab-adapter` | alphaTab 初始化、版本适配、布局索引 |
| `score-layout` | 简谱、歌词和系统间距布局 |
| `score-svg-renderer` | SVG 节点生成、稳定 ID、导出 |
| `score-playback` | 播放控制、事件总线、光标和自动滚动 |
| `score-runtime` | 在 PC 和 WebView 中运行的统一入口 |
| `apps/pc` | PC 宿主 UI |
| `apps/android-webview` | Android WebView 容器和桥接 |

在当前 Android 工程中，建议将共享 Web 运行时作为独立前端包构建后复制到：

```text
app/src/main/assets/score-runtime/
```

现有 `app/src/main/assets/songs/` 继续保存内置曲谱资源；曲谱渲染不直接写入 Compose Composable。

---

## 11. 实施路线

### Phase 0：锁定 alphaTab 基线

- 固定 alphaTab 版本、字体和许可文件。
- 以仓库现有 `最浪漫的事` AlphaTex 作为回归样本。
- 验证 Guitar Pro、AlphaTex 和目标 MusicXML 的导入范围。
- 验证六线谱、和弦、节奏、歌词和 alphaSynth 播放。
- 建立 SVG 快照和播放事件日志。

退出标准：PC 和 Android WebView 能加载同一曲谱，SVG 结构和主要坐标一致，播放可用。

### Phase 1：统一 SVG 运行时

- 实现 `alphatab-adapter`。
- 实现 `ScorePosition` 和 Beat 索引。
- 封装 alphaTab 配置和版本相关 API。
- 建立 PC 页面和 Android WebView 页面。
- 实现缩放、滚动、重排和销毁重建。

退出标准：屏幕宽度变化后，alphaTab 六线谱可以稳定重新排版，两个平台不出现分叉逻辑。

### Phase 2：数字简谱和歌词

- 实现简谱事件解析。
- 实现数字、附点、高低音点、时值线和连线。
- 实现一音一字、延长音和多行歌词。
- 将扩展节点写入 alphaTab 生成的同一份 SVG。
- 建立简谱/歌词与 Beat 的位置校验。

退出标准：两类目标截图的核心谱面结构可由结构化数据生成，简谱、歌词和六线谱不发生错位。

### Phase 3：播放跟随

- 接入 alphaTab 播放位置事件。
- 实现六线谱、简谱、歌词统一高亮。
- 实现自动滚动、暂停恢复、调速和小节循环。
- 处理 WebView 后台、音频焦点和页面销毁。

退出标准：播放光标与三类内容同步，连续播放 10 分钟无明显漂移或持续重排。

### Phase 4：质量和导出

- 完成手机、平板、桌面宽度矩阵。
- 完成字体、中文字符、深色模式和缩放测试。
- 实现 SVG 导出，评估 PNG/PDF 导出。
- 建立 alphaTab 升级回归流程。
- 完成许可证、字体和 SoundFont 清单。

---

## 12. 测试方案

### 12.1 单元测试

- Score JSON 解析和版本校验。
- `measure + tick` 映射。
- 简谱高低音点、时值线和延长音。
- 多行歌词和延长音布局。
- 小节换行和最小宽度规则。
- SVG 节点 ID 和数据属性稳定性。
- 播放位置到高亮节点的映射。

### 12.2 SVG 快照测试

固定以下输入并比较 SVG 快照：

- 4/4 简单扫弦。
- 6/8 多弦同时发声。
- 空弦和闷音。
- 和弦变化。
- 多行中文歌词。
- 反复记号和第一/第二结尾。
- 简谱高低音点、附点和时值线。

允许 SVG 中存在 alphaTab 版本导致的元数据差异，但谱面几何结构、稳定 ID 和语义属性必须可验证。

### 12.3 跨平台测试

| 项目 | PC/Web | Android WebView |
|---|---:|---:|
| 同一输入生成同一结构 | 必须 | 必须 |
| SVG 显示清晰度 | 必须 | 必须 |
| 缩放和滚动 | 必须 | 必须 |
| 播放和自动滚动 | 必须 | 必须 |
| 本地离线加载 | 可选 | 必须 |
| 文件导入 | 必须 | SAF |
| 页面销毁后资源释放 | 必须 | 必须 |

---

## 13. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| alphaTab 默认布局与截图样式有差异 | 视觉还原度不足 | 先通过主题和扩展 SVG 解决；必要时维护受控 fork |
| alphaTab 布局索引 API 版本变化 | 简谱定位失效 | 所有调用集中在 Adapter，并锁定版本 |
| 中文歌词字体度量不一致 | 文字碰撞或错位 | 随应用打包字体，建立字体快照测试 |
| WebView 大谱面性能不足 | 滚动和播放卡顿 | 只在播放位置更新 class；避免每帧重排；按系统分页渲染 |
| SoundFont 许可不明确 | 无法发布播放资源 | 仅使用明确可分发资源，SoundFont 可替换 |
| 外部曲谱版权不清晰 | 内容和发布风险 | 只接入有明确许可和来源记录的内容 |
| Android WebView 音频行为差异 | 播放兼容性问题 | 先做设备矩阵；保留后续 Native 音频输出适配点 |

---

## 14. 许可证和资源治理

alphaTab 使用 MPL-2.0。项目集成和发布时必须：

- 保留 alphaTab 版权和许可证文本。
- 记录使用的 alphaTab 版本和构建来源。
- 如果修改 alphaTab 文件，记录修改文件和修改内容。
- 对 alphaTab 的分发方式进行 MPL-2.0 合规审查。
- 单独确认 Bravura 或其他字体的授权范围。
- 单独确认 SoundFont 的商业分发许可。
- 每首内置曲谱保存作者、来源、许可和校对记录。

曲谱数据不可因渲染技术开源而自动获得分发权。内容许可和软件许可必须分开管理。

---

## 15. 最终决策

XStar Guitar 的曲谱系统采用以下最终方案：

```text
alphaTab
  ├── Guitar Pro / AlphaTex / MusicXML 解析
  ├── 六线谱、节奏、和弦和技巧渲染
  ├── 统一时间轴
  └── alphaSynth 播放

自定义扩展
  ├── 数字简谱 SVG 渲染
  ├── 高低音点、附点和时值线
  ├── 中文多行歌词 SVG 渲染
  └── 播放高亮与自动滚动

统一平台方案
  ├── TypeScript/JavaScript score-runtime
  ├── SVG 唯一渲染后端
  ├── PC/Web 直接运行
  └── Android WebView 运行同一份代码
```

该方案的关键收益是：Android 和 PC 共用谱面模型、alphaTab 适配器、布局、SVG、播放和高亮逻辑；平台差异仅限于容器、文件访问、生命周期和系统音频能力。后续增加移调、循环、编辑、导出或更多谱面样式时，不需要重新维护两套渲染器。
