# 数据与内容设计

## 1. 设计目标

- 用统一模型连接音符、和弦、节奏、能力、课程、练习和曲目。
- 业务数据本地可查询、可迁移，媒体和文档不作为数据库 BLOB。
- 内容升级不能因标题或排序变化破坏用户进度。
- 用户导入和在线资源具有明确来源、状态和能力差异。
- 所有分发内容均可追溯许可。

## 2. 核心领域关系

```text
Note <- ChordTone -> Chord <- ChordFingering
  |                    |
  `-> Scale            `-> Exercise

Skill <- Lesson -> Exercise <- PracticeSession
  |          |          |
  `----------+----------+-> SongRequirement <- Song/Score

ResourceProvider -> License -> RemoteResource -> LibraryItem
UserImport ------------------------------------> LibraryItem
```

“能力”是学习与应用之间的核心连接；“乐库项”统一展示来源不同的曲谱，但保留来源类型和可用能力。

## 3. 标识与版本规则

- 所有内容使用不可变稳定字符串 ID，例如 `chord.c.major.open.v1`、`skill.rhythm.quarter-note`。
- 标题、文案和排序不是主键。
- 内容对象包含 `schemaVersion` 与 `contentVersion`。
- 删除内容优先标记 deprecated，并提供替代 ID；不能直接让用户进度变成孤儿。
- 多对多关系使用关联表，不将 ID 数组拼成数据库字符串。
- 时间数据库统一保存 epoch milliseconds，领域层转换为 Instant。
- 网络 remote ID 必须与 Provider ID 组成复合身份。

## 4. Room 实体建议

### 4.1 音乐基础

| Entity | 关键字段 |
|---|---|
| `NoteEntity` | `id`, `pitchClass`, `displayNames` |
| `TuningEntity` | `id`, `name`, `stringCount`, `referencePitchHz` |
| `TuningStringEntity` | `tuningId`, `stringIndex`, `midiNote`, `centOffset` |
| `ChordEntity` | `id`, `canonicalName`, `root`, `quality`, `difficulty` |
| `ChordToneEntity` | `chordId`, `interval`, `role` |
| `ChordFingeringEntity` | `id`, `chordId`, `tuningId`, `baseFret`, `frets`, `fingers` |
| `RhythmPatternEntity` | `id`, `timeSignature`, `subdivision`, `defaultBpm`, `patternVersion` |

`frets` 和 `fingers` 可使用受控小型序列转换器，但领域层必须验证长度与调弦弦数一致。节奏事件建议使用版本化 payload，由构建期内容校验器解析。

### 4.2 学习与练习

| Entity | 关键字段 |
|---|---|
| `SkillEntity` | `id`, `category`, `level`, `title` |
| `SkillPrerequisiteEntity` | `skillId`, `prerequisiteSkillId`, `requiredLevel` |
| `LearningPathEntity` | `id`, `title`, `audience`, `version` |
| `PathNodeEntity` | `pathId`, `contentType`, `contentId`, `orderIndex` |
| `LessonEntity` | `id`, `title`, `objective`, `estimatedMinutes`, `version` |
| `LessonStepEntity` | `id`, `lessonId`, `type`, `payloadVersion`, `orderIndex` |
| `ExerciseEntity` | `id`, `type`, `configVersion`, `difficulty` |
| `ExerciseSkillEntity` | `exerciseId`, `skillId`, `targetLevel` |
| `ContentProgressEntity` | `contentType`, `contentId`, `status`, `position`, `updatedAt` |
| `SkillEvidenceEntity` | `id`, `skillId`, `sourceType`, `sourceId`, `level`, `recordedAt` |
| `PracticeSessionEntity` | `id`, `exerciseId`, `startedAt`, `endedAt`, `status` |
| `PracticeAttemptEntity` | `id`, `sessionId`, `bpm`, `duration`, `rating`, `summary` |

`LessonStep.type` 仅允许受控类型：文本、图片、音视频、和弦图、曲谱片段、调音检查、节拍练习、和弦练习、用户确认。内容不能包含任意可执行脚本。

### 4.3 曲谱与乐库

| Entity | 关键字段 |
|---|---|
| `SongEntity` | `id`, `title`, `creator`, `difficulty`, `bpm`, `tuningId` |
| `SongRequirementEntity` | `songId`, `requirementType`, `targetId`, `requiredLevel` |
| `SongSectionEntity` | `id`, `songId`, `type`, `orderIndex`, `label` |
| `ChordSheetLineEntity` | `id`, `sectionId`, `orderIndex`, `text` |
| `ChordEventEntity` | `lineId`, `offset`, `chordId`, `displayOverride` |
| `ScoreDocumentEntity` | `id`, `title`, `format`, `fileId`, `sha256`, `parseStatus` |
| `LibraryItemEntity` | `id`, `sourceType`, `targetType`, `targetId`, `favorite`, `lastOpenedAt` |
| `LibraryPositionEntity` | `libraryItemId`, `anchorType`, `anchorValue`, `updatedAt` |

结构化歌词和弦谱以文本 offset 或 token anchor 绑定和弦，渲染时按结构布局，不能依赖空格对齐。对于中文、组合字符和 Unicode，offset 方案必须明确以代码点、UTF-16 index 或 token ID 为单位；推荐使用稳定 token anchor 避免字符索引歧义。

### 4.4 资源与许可

| Entity | 关键字段 |
|---|---|
| `ResourceProviderEntity` | `id`, `displayName`, `policyVersion`, `enabled`, `reviewedAt` |
| `LicenseEntity` | `id`, `name`, `url`, `attributionTemplate`, `redistributionAllowed` |
| `RemoteResourceEntity` | `providerId`, `remoteId`, `title`, `licenseId`, `cachedUntil`, `status` |
| `ResourceAttributionEntity` | `resourceId`, `creator`, `sourceUrl`, `requiredText` |
| `ContentPackEntity` | `id`, `version`, `sha256`, `installStatus` |

## 5. 内容包设计

内置内容与后续下载内容使用版本化 manifest：

```json
{
  "id": "starter-acoustic-zh-cn",
  "schemaVersion": 1,
  "contentVersion": 1,
  "locale": "zh-CN",
  "minAppVersion": 1,
  "licenseManifest": "licenses.json",
  "entries": [
    { "type": "lesson", "path": "lessons/lesson-001.json" },
    { "type": "exercise", "path": "exercises/exercise-001.json" },
    { "type": "song", "path": "songs/song-001.json" }
  ]
}
```

安装流程：

1. 校验 manifest Schema 和兼容版本。
2. 校验每个文件摘要、大小和许可记录。
3. 在临时目录解析并执行引用完整性校验。
4. Room 事务导入结构化索引。
5. 原子发布内容目录并标记安装完成。
6. 失败时回滚数据库并清除临时文件。

## 6. 构建期内容校验

以下错误必须阻止构建或内容包发布：

- ID 重复、缺失或不符合命名规则。
- 前置能力图存在环。
- 课程步骤引用不存在的和弦、练习、媒体或曲目。
- 和弦指法弦数错误、品位非法、手指标记非法。
- 节奏事件超出小节或拍号不匹配。
- 结构化和弦事件 anchor 不存在。
- 曲目缺少调弦、难度、能力要求或完成规则。
- 内容文件缺少作者、来源、许可或必要署名。
- 许可禁止分发但文件被打入内容包。
- 本地化关键字段缺失。

人工校验：

- 和弦图与文字说明一致。
- 音频示范与标注和弦/节奏一致。
- 曲谱在目标 BPM 可以实际演奏。
- 课程术语适合零基础且动作安全。
- 所有署名在产品中可见且格式正确。

## 7. 曲谱类型与能力矩阵

| 类型 | 阅读 | 搜索正文 | 和弦点击 | 移调 | 自动滚动 | 分段关联 |
|---|---:|---:|---:|---:|---:|---:|
| 内置结构化和弦谱 | 是 | 是 | 是 | 后续支持 | 后续支持 | 是 |
| 受控文本和弦谱 | 是 | 是 | 可解析后支持 | 视解析质量 | 后续支持 | 可选 |
| PDF | 是 | 视文本层 | 否 | 否 | 后续按页 | 用户手动标记 |
| 图片 | 是 | 否 | 否 | 否 | 后续按页 | 用户手动标记 |
| MusicXML/MXL | 尖峰后决定 | 是 | 视声部语义 | 后续 | 后续 | 后续 |
| 外部网页链接 | 外部打开 | 仅元数据 | 否 | 否 | 否 | 否 |

UI 不得把所有曲谱显示为能力相同。每项操作根据格式和许可 capability 决定是否显示。

## 8. 用户导入

### 8.1 默认策略

- 通过 `ACTION_OPEN_DOCUMENT` 选择文件。
- 默认复制到应用私有目录，完成后不依赖永久 URI 权限。
- 流式计算 SHA-256 并检测重复。
- 用户可修改标题、作者和标签；原文件内容不被修改。
- 删除乐库项时同时删除私有文件、解析缓存和相关索引。

### 8.2 状态机

```text
PENDING -> VALIDATING -> COPYING -> PARSING -> READY
                              |          |
                              `-> FAILED <-'
READY -> DELETING -> DELETED
```

应用启动或周期清理时修复长时间停留在中间状态的记录，并清理孤立临时文件。

### 8.3 限制

具体数值在技术尖峰与设备测试后冻结：

- 单文件大小上限。
- 总导入空间上限。
- PDF 页数和渲染像素上限。
- 图片解码尺寸上限。
- XML 深度和节点数上限。
- ZIP 条目数、展开大小和压缩比上限。

## 9. 在线资源治理

### 9.1 Provider 准入清单

| 项目 | 必须确认 |
|---|---|
| 访问方式 | 官方 API 或条款允许的访问方式；不得绕过限制抓取 |
| 搜索与展示 | 是否允许应用内展示标题、摘要、封面和作者 |
| 内容许可 | 公版、Creative Commons 或其他明确授权范围 |
| 缓存与下载 | 元数据、预览、正文分别允许缓存多久 |
| 署名 | 必须展示的作者、来源、许可文本和链接 |
| 地域 | 是否有限制以及如何执行 |
| 撤回 | 内容删除、许可变化和缓存清理机制 |
| 隐私 | 查询词、IP 和设备信息会发送什么 |
| 稳定性 | 配额、分页、限流、错误码和可用性 |

未完成准入评审前，不在生产配置加入 Provider。

### 9.2 远程状态

- `AVAILABLE`：可打开来源详情。
- `METADATA_CACHED`：只缓存元数据，不代表正文离线。
- `DOWNLOADED`：许可允许且本地文件校验完成。
- `UNAVAILABLE`：来源暂时失效，保留来源信息。
- `WITHDRAWN`：来源撤回，停止分发并按许可处理缓存。

用户通过 SAF 自行导入的文件不因远程撤回自动删除，但远程副本不得继续分发。

## 10. 搜索设计

本地搜索范围：课程、知识、和弦、曲目元数据、导入文件标题和允许索引的文本。使用 Room FTS，并在结果中标明类型。

在线搜索：

- 仅在用户主动进入在线搜索并提交查询时发起。
- 并行 Provider 请求有独立超时和错误状态，一个失败不清空其他结果。
- 聚合结果不能仅按 Provider 排序，应支持相关性、难度和许可筛选。
- 查询缓存使用短 TTL，只保存 Provider 允许的字段。
- 关闭在线搜索后停止刷新并清理可选查询缓存。

## 11. 数据保留、备份与删除

| 数据 | 保留与备份建议 |
|---|---|
| 设置 | 可使用系统备份 |
| 课程进度 | 可备份，隐私说明中披露 |
| 练习历史 | 本地保留，用户可单项或全部删除；备份范围需显式决定 |
| 导入曲谱 | 默认排除系统云备份，避免大型和版权文件外传 |
| 下载内容包 | 排除备份，可重新下载 |
| 解析与搜索缓存 | 排除备份，可重建 |
| 麦克风原始音频 | 默认不存在 |

“清除全部数据”应清理 Room、DataStore、私有导入、下载和缓存，并在完成后回到首次状态。

## 12. 数据迁移策略

- Room 导出 Schema 文件并纳入版本控制。
- 每个已发布版本到当前版本均有 Migration Test。
- 内容 Schema 与数据库 Schema 分开演进。
- 内容 ID 合并或弃用使用映射表迁移进度。
- 文件目录升级先复制/验证/原子切换，不原地破坏唯一副本。
- 曲谱解析缓存包含 parserVersion，升级后可安全重建而不迁移缓存正文。

## 13. 隐私数据清单

| 数据 | 目的 | 上传 | 默认保存 |
|---|---|---:|---:|
| 麦克风 PCM | 实时音高分析 | 否 | 否 |
| 音高估计 | 调音 UI | 否 | 仅短期内存 |
| 练习汇总 | 显示进度和推荐复习 | 否 | 是 |
| 用户导入文件 | 本地阅读与练习 | 否 | 是 |
| 在线查询词 | 向选定 Provider 搜索 | 发送给 Provider | 短期缓存视设置 |
| 崩溃诊断 | 稳定性改进 | 默认不接入；后续需同意 | 本地脱敏日志 |

若未来加入录音回放、账号或云同步，必须重新完成隐私设计和 ADR，不能沿用当前默认假设。
