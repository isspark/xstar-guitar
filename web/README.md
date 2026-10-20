# XStar Guitar Web 曲谱验证原型

这是基于 `alphaTab + SVG` 的第一阶段 Web 实现，用于验证统一曲谱运行时、六线谱、中文歌词、数字简谱扩展和播放跟随。

## 启动

请从项目根目录启动静态服务器，不能直接双击 `index.html`，因为浏览器会限制本地文件加载：

```powershell
python -m http.server 8080
```

然后打开：

```text
http://localhost:8080/web/
```

原型复用项目已有资源：

- `docs/vendor/alphatab/alphaTab.min.js`
- `docs/vendor/alphatab/font/Bravura.woff2`
- `docs/vendor/alphatab/sonivox.sf2`（本地播放音源，附 `sonivox-LICENSE.txt`）
- `docs/vendor/songs/zui_lang_man_de_shi.alphaTex.js`

## 当前验证范围

- alphaTab AlphaTex 加载和 SVG 渲染。
- 六线谱、拍号、和弦、节奏和中文歌词。
- 从 `web/scores/*.json` 读取正式 Score JSON 的数字简谱 SVG 扩展层。
- 数字简谱/歌词播放高亮。
- 播放、暂停、停止、速度调整和自动跟随滚动。
- 工具栏的“播放跟随”开关控制播放时是否自动滚动页面；关闭后保留播放、光标和高亮，但不会抢回用户手动滚动的位置。
- 音源就绪后开放播放；播放位置通过 tickCache 映射到实际小节，使用统一 SVG 光标、简谱/歌词高亮与逐行平滑跟随。图片叉号仅作为显示，播放使用对应和弦音高。
- 响应式缩放和深色/浅色显示。

## 设计边界

当前入口默认读取 `web/scores/hua.json`（《画》三页图片转录，58 小节），选择框也可切换标准示例和 24 小节技巧图鉴。AlphaTex 和简谱/歌词元数据均来自 Score JSON 资源。

## 《画》图片转录

- `scores/hua.json`：三页原图按小节转录的和弦、简谱与歌词。第 1 页为 1–21 小节，第 2 页为 22–41 小节，第 3 页为 42–58 小节。
- `score-image-adapter.js`：展开为 AlphaTex 伴奏与独立的简谱时间线。`s/e/q/h/w` 表示十六/八/四/二分/全音符，`de/dq` 为附点，音级后的逗号表示低音点；歌词 `_` 表示空缺、`~` 表示延长。
- 保留原图的实际和弦名称 C#m/G#m/B/E，伴奏试听按变调夹 4 品的 Am/Em/G/C 指型生成。叉号表示原图的琴弦与节奏位置，扫弦段使用对应和弦。
- 简谱使用按 tick 插值的独立位置映射，支持减时线、低音点、附点、小节内连音线和长音延长线。简谱旋律尚未作为独立音轨播放。
- 原图中的拍弦点已作为每小节的 `tap` 标记保留，渲染在六线谱第一根弦上方留白区域；它与叉号、扫弦方向和和弦音高独立，不会被误显示为品位数字或改变和弦播放。
- 播放时当前拍弦点会变为紫色并放大，和播放光标、简谱及歌词一起跟随当前 tick；原始 JSON 未逐小节显式写出的点由图片转录适配器按原谱节奏补入运行时 `rhythmSheet.bars[].tap`。
- 这是一份图片转录稿：伴奏按可见节奏重建，细小装饰音、跨小节连音、原图个别滑音及和弦转位尚需人工逐音复核；原图无速度标注，暂用 72 BPM。

```powershell
node web/check-hua.cjs
```

简谱和歌词的横坐标取自 alphaTab 实际 Beat bounds 中的音符头位置，而不是简谱数组索引或拍槽中心，因此会与六线谱的拨弦/品位位置保持同一时间点对齐。

扩展层与导出共用一个 SVG，当前配置关闭 `core.enableLazyLoading`。alphaTab 仍可能输出标题及各谱表行的多个 SVG，因此渲染完成后按各分块的偏移合并至统一坐标系，再绘制扩展层。响应式重排完成后重新合并与生成扩展层。

图片转录谱的扫弦保留真实和弦音符以生成符干、连梁和播放数据；合并 SVG 后隐藏对应品位数字、补齐琴弦线断口，并在六线谱范围内绘制方向箭头。

渲染职责固定为：

```text
alphaTab：六线谱/节奏/技巧/反复记号
自定义 SVG：和弦名称/和弦图 → 数字简谱 → 与简谱事件绑定的歌词
```

Score JSON 的 `alphaTabSource.lyricsMode` 设置为 `custom-only` 时，运行时会移除 AlphaTex 中的歌词标注，避免重复。当前样例和弦指法图由扩展层渲染，`chordDiagramsMode` 为 `custom-only`。

标准结构示例见：

```text
web/scores/score-example.json
```

完整吉他谱符号覆盖示例见（节拍、休止、和弦、击勾弦、滑音、推弦、泛音、揉弦、闷音、反复与结尾）：

```text
web/scores/score-complete-example.json
```

页面中的“选择内置示例”可以直接切换两份数据。

技巧图鉴的 AlphaTex 位于 `scores/guitar-techniques.alphaTex.js`，每个小节都有 A–X 标题：

| 小节 | 展示内容 |
|---|---|
| 1–4 | 分解和弦、空弦、多弦齐奏、上/下扫弦、琶音、附点、切分、延音线、十六/三十二分、休止 |
| 5–7 | 三连音、6/8 与 3/4 变拍、二分音符 |
| 8–10 | 击勾弦、连奏/换把滑音、上/下滑入与滑出 |
| 11–13 | 半音/全音推弦、推放/预推、普通/宽揉弦、颤音、轮指、四类泛音 |
| 14–17 | 掌根制音、死音、幽灵音、断奏、持续音、双音、点弦、倚音、左右手指法、拨弦方向、重音 |
| 18–19 | 打弦、弹拨、拍弦、敲板、摇把、渐强/渐弱、力度 |
| 20–24 | 反复、第一/第二房、变速、全音符、延长记号、全小节休止 |

简谱为 C 大调音级，和弦取最高音，技巧音以基础音级表示；歌词用于标记技巧。当前自定义简谱层只绘制文本，不会将六线谱的全部技巧转绘为简谱符号。多声部、特殊调弦/变调夹、D.S./Coda 等独立场景尚未纳入这份单轨图鉴。

使用项目内置 alphaTab 解析器检查实际数据模型、所有小节总时值、简谱 tick/时值、倚音索引及代表性技巧与反复属性：

```powershell
node web/check-score.cjs
```

标准简谱事件使用：

```json
{
  "measureIndex": 0,
  "events": [
    {
      "tick": 0,
      "value": "6",
      "durationTicks": 480,
      "lyric": "到"
    }
  ]
}
```

Android 后续通过 WebView 加载同一套 `app.js`、`styles.css` 和 alphaTab 资源，不新增 Android Canvas 渲染器。
