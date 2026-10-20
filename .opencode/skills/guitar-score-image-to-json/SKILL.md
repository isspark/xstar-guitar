---
name: guitar-score-image-to-json
description: 图片转吉他谱 JSON：当用户提供六线谱图片、扫描谱或截图，要求转录为本项目可导入的吉他谱 JSON，或校正图片转录的弦位、节奏、和弦、歌词和播放音高时使用。
---

# 图片转吉他谱 JSON

将用户提供的谱面按页、按小节转录为 XStar Guitar Web 可以导入、显示和试听的 Score JSON。正确性同时包含原图位置、JSON 结构、实际解析后的音符、播放音高和可见布局；“JSON 能解析”或“小节满拍”不代表转录正确。

## 先读项目实现

开始前读取当前版本，不要假定接口保持不变：

- `web/scores/yu_jian.json`：两页六线谱转录，含 `transcription.bars`、小节内和弦切换、指型变体和仅歌词事件。
- `web/app.js`：重点看 `validateScoreDocument`、`fetchAlphaTex`、`renderJianpu`、`resolveImageCrossPitches`、`restoreImageCrossGlyphs` 和 `renderScore`。
- `web/check-yu-jian.cjs`：实际导入校验、逐小节一致性、弦位和播放音高检查范例。
- `web/scores/score-example.json`：基础 Score JSON。
- 图片确实包含独立数字简谱时，再读 `web/scores/hua.json` 和 `web/score-image-adapter.js`。该适配器目前带有《画》专用和弦及节奏，不能直接拿来转录任意歌曲。

文件默认保存为 `web/scores/<song_slug>.json`，沿用项目文件名风格。保留已有用户修改。

## 1. 建立原图清单

逐页确认：

1. 曲名、词曲作者、编配来源、原调、指型调、变调夹、拍号、调弦、速度。
2. 页码、小节编号及分页边界，不能漏小节或重复跨页小节。
3. 前奏、主歌、副歌、间奏等分段；段落在小节中途开始时记录准确 tick。
4. 和弦名称、变换位置、同名不同指型。
5. 品位数字、叉号、休止、节奏、连音／延音、击勾弦、扫弦、反复等。
6. 歌词和其横向对齐位置；原图没有数字简谱时不要猜造简谱旋律。

未标速度时可选择合理的 `defaultTempo` 用于试听，但必须注明这是默认值。模糊、矛盾或无法识别的内容写入 `transcription.review`，带具体小节号及原因。不能默默用常见指型覆盖原图。

## 2. 弦号与位置：必须区分三个坐标系

| 坐标系 | 顺序 |
| --- | --- |
| 原图六线谱从上向下 | 通常为标准吉他 1、2、3、4、5、6 弦 |
| AlphaTex `fret.string.duration` 的 string | 1 为最上方高音弦，6 为最下方低音弦 |
| 本项目所用 alphaTab 解析后的 `note.string` | 6 为最上方弦，1 为最下方弦，即 `7 - AlphaTex弦号` |
| `chordDiagrams.frets` / `transcription.voicings` 数组 | 从标准 6 弦（低 E）到 1 弦（高 E） |

标准调弦标签／`tuning` 依现有 JSON 写法从高音到低音；指法数组则从低音到高音，两者不可混用。

每条谱行先确定六条线，再把符号归入明确的线。不要凭音高走向、和弦习惯或数字上方空白猜弦号。向用户解释位置时优先用“从上往下第几条线”，避免用户按相反方向数线引起歧义。

**已发生过的错误**：第一小节的 `3、0` 在从上向下第二条线，正确 AlphaTex 是 `3.2.8 0.2.8`，解析后应为 `note.string=5`；写成 `.1` 会跑到最上面一条线。不能为了迎合另一种弦号叫法而把音符移到低音 A 弦。

## 3. JSON 结构约定

使用现有项目格式，主要字段：

- `version: 1`。
- `source`：`id`、`title`、`subtitle`、来源及转录说明。`transcriptionNotes` 优先用字符串，方便页面直接展示。
- `meter`、`defaultTempo`、`capo`、`tuning`、`key`：仅记录原图信息或明确注明默认值。
- `chordDiagrams`：和弦名映射 `{ frets: [低音到高音六个品位], baseFret: 1 }`。`"x"` 表示该弦静音，`0` 表示空弦。
- `transcription.ticksPerQuarter: 960`。
- `transcription.pageRanges`、`voicings`、`review`。
- `transcription.bars`：每小节包含 `number`、必要的 `page`／`section`／`sectionAtTick`、`chords`、`tab`、`lyrics`。
- `alphaTabSource`：`type: "alphatex"`、完整的 `text`、`chordDiagramsMode: "custom-only"`、`lyricsMode: "custom-only"`。
- `jianpu.tracks`：项目导入必需，承载真实数字简谱或仅歌词时间点。
- `style`：与实际内容相符，没有简谱时 `showJianpu: false`。

示例小节：

```json
{
  "number": 2,
  "chords": [
    { "tick": 0, "name": "G", "voicing": "G-open" },
    { "tick": 1920, "name": "D/F#" }
  ],
  "tab": "x.6.8 x.4.8 3.2.8 2.3.8 x.6.8 x.4.8 3.2.8 0.3.8",
  "lyrics": [{ "tick": 960, "text": "某" }]
}
```

仅歌词事件示例，`measureIndex` 从 0 起算：

```json
{
  "measureIndex": 1,
  "events": [
    { "tick": 960, "value": "", "durationTicks": 480, "lyric": "某" }
  ]
}
```

空 `value` 只有在带有效歌词时才允许；不能省略 `value`。`transcription.bars[].lyrics` 和 `jianpu` 歌词必须一致。

## 4. 节奏、技巧和双份数据

960 ticks 为一拍四分音符，常见时值：十六分 240、八分 480、四分 960、二分 1920、全音符 3840；附点乘 1.5，连音按实际分组处理。小节长度按拍号计算，不能一律强制 3840；弱起、变拍和多声部按原图处理，运行时不支持则明确记录并补充支持。

AlphaTex 示例：

- `3.2.8`：第二条线 3 品八分音符。
- `(0.2 0.3).8`：双音。
- `r.4`：四分休止。
- `-.2.4`：延续同弦前音，不是再次拨弦，也不是休止。
- `:16 1.2{h} 3.2`：击弦。优先使用本地解析器验证过的技巧语法；这里不要假定 `1.2.16{h}` 在当前版本同样可用。
- `{ad}`：按项目现有约定的低音到高音琶音扫弦；核对原图方向。

`transcription.bars[].tab` 与 `alphaTabSource.text` 是同一份谱的两种表达，修改其中之一必须同步另一个。通过分别解析并比较每个 beat 的时值、休止、弦号、品位和技巧确保一致。新增生成工具时应复用单一数据来源，避免手工维护产生分叉。

## 5. 叉号显示与播放必须分开

先依据原谱判断叉号语义：

- 和弦分解节奏谱里的叉号常表示“拨奏当前和弦的这一根弦”，应播放该弦对应品位。
- 真正的死音／闷音符号应保留其效果，不能全部变成和弦音。
- 和弦指法图上的 `x` 表示静音弦，与谱中的拨弦叉号不是同一含义。

本项目当前的 `resolveImageCrossPitches` 对 `transcription.bars` 中的死音模型按和弦恢复音高；混有真实死音的曲谱必须先扩展明确标记及解析逻辑，不可直接套用导致真实死音也被改写。

检查恢复音高的处理：

1. 使用当前小节内 tick 之前最近一次和弦变化，必要时延续上小节和弦。
2. 优先选择变化中的 `voicing`，否则使用该和弦 `chordDiagrams.frets`。
3. 对当前模型 `note.string`，低音到高音指型数组索引为 `note.string - 1`。
4. 仅恢复代表拨弦的叉号；原图品位数字不得覆盖。
5. 延音继承原音品位及音高，不因和弦变化重新触发。
6. 变调夹只由播放器计入一次，不能同时加到品位。
7. SVG 渲染后仍显示叉号，不能把恢复的品位数字泄露到原本叉号的位置。

例如 G 低 E 弦 3 品、capo 1：MIDI 应为 `40 + 3 + 1 = 44`；同小节切到 D/F# 2 品应为 43。检查实际解析后的 `realValue`，不要只检查字符串。

## 6. 和弦、歌词与谱行布局

- 和弦必须从 `transcription.bars[].chords` 独立绘制，不能依赖有无歌词／简谱。
- 使用变化的 tick 定位；每小节只放一个和弦会漏掉小节内切换。
- 同名指型变体使用对应 `voicing`，名称和指法不能脱节。
- 检查 AlphaTex 和自定义层是否重复绘制，或双方都移除了名称。
- 自定义 SVG 和弦图与歌词不参与 alphaTab 自动测量。为谱行上、下方都留空间；当前图片转录谱采用 `firstSystemPaddingTop=130`、`systemPaddingTop=130`、`systemPaddingBottom=120`、`lastSystemPaddingBottom=120`、每行 4 小节作为起点。
- 数值不是通用保证：密集一拍一换、多行歌词、不同缩放及换行后都需实际查看。必要时增加空间或调整排版，不要把图形硬挤在已有空白中。

## 7. 验证与交付

每首转录至少完成：

1. 使用页面实际 `validateScoreDocument` 校验，而不只是 `JSON.parse`。
2. 用项目自带 `docs/vendor/alphatab/alphaTab.min.js` 解析完整 AlphaTex。
3. 核对页数、小节数、编号、分页起点和终点。
4. 检查逐小节总时值；与原图核对休止、切分、连音、延音及技巧。
5. 分别解析 `bars[].tab` 和内嵌 AlphaTex，比较音符及节奏。
6. 选取可见数字、双音、击弦、跨小节延音，按原图明确断言弦号与品位。禁止仅比较两份来自同一个错误来源的数据。
7. 运行实际叉号音高恢复函数，检查低音、换和弦前后、指型变体、capo 和显式数字未被覆盖。
8. 检查和弦绘制遍历覆盖所有变化，包括无歌词小节及小节中间的变化。
9. 如有浏览器验证工具，实际导入并检查首次显示、重排、歌词与和弦图的行间重叠、叉号外观和播放。未实际试听或检查截图时，不能声称“音频及视觉完全正确”。

可参照 `web/check-yu-jian.cjs` 建立曲目对应的校验脚本，检查有意义的原图锚点及运行时行为。若修改共享渲染器，再运行受影响的现有检查：

```powershell
node --check web/app.js
node web/check-yu-jian.cjs
node web/check-hua.cjs
node web/check-score.cjs
```

最终告知用户 JSON 路径、已转录范围、验证项目和明确待复核处。若变更了 `web/app.js`，提醒刷新页面再重新导入；不要把数据检查通过描述为人工逐音复核通过。
