# XStar Guitar 产品与工程文档

## 1. 文档目的

本目录是 XStar Guitar 的产品、内容、设计和工程基线。产品首先服务中文市场的零基础民谣吉他用户，同时通过统一能力模型逐步覆盖入门、进阶、演奏和创作用户。

首版不是一个功能堆叠的“吉他工具箱”，而是一条可完成的学习闭环：

> 调好吉他 -> 理解必要知识 -> 完成针对性练习 -> 在完整练习曲中应用 -> 获得下一步建议

## 2. 已确认决策

| 项目 | 决策 |
|---|---|
| 首发平台 | Android |
| 客户端技术 | Kotlin + Jetpack Compose |
| 首批用户 | 完全零基础用户 |
| 首版方向 | 民谣吉他弹唱 |
| 首发市场 | 中文市场 |
| 账户策略 | 不登录、无账户体系 |
| 数据策略 | 本地优先，核心功能离线可用 |
| 在线能力 | 用户主动搜索明确授权的免费曲谱资源 |
| 曲谱来源 | 内置原创/公版/明确授权内容、用户本地导入、合规在线来源 |
| 核心隐私原则 | 麦克风音频只在设备内实时分析，默认不保存、不上传 |

## 3. 文档索引

| 文档 | 用途 | 主要读者 |
|---|---|---|
| [01-product-requirements.md](01-product-requirements.md) | 产品定位、范围、需求与验收 | 全员 |
| [02-user-learning-system.md](02-user-learning-system.md) | 用户分层、能力模型和教学闭环 | 产品、内容、设计 |
| [03-information-architecture.md](03-information-architecture.md) | 导航、页面、状态和关键流程 | 产品、设计、客户端 |
| [04-technical-architecture.md](04-technical-architecture.md) | Android 架构、模块和实时音频方案 | 客户端、测试 |
| [05-data-and-content-design.md](05-data-and-content-design.md) | 数据模型、内容格式、导入和版权治理 | 客户端、内容、法务 |
| [06-implementation-roadmap.md](06-implementation-roadmap.md) | 阶段、依赖、里程碑和退出标准 | 产品、开发、测试 |
| [07-testing-and-quality.md](07-testing-and-quality.md) | 测试矩阵和质量门禁 | 开发、测试 |
| [08-risks-and-decisions.md](08-risks-and-decisions.md) | 风险、假设及架构决策记录 | 全员 |
| [09-backlog.md](09-backlog.md) | Epic、用户故事、优先级和验收 | 产品、开发、测试 |
| [10-ui-design.md](10-ui-design.md) | 视觉方向、页面规范、组件与交互基线 | 产品、设计、客户端 |
| [11-alphatab-score-rendering-design.md](11-alphatab-score-rendering-design.md) | alphaTab 统一 SVG 曲谱渲染、简谱扩展、播放与 Android/PC 复用方案 | 客户端、前端、内容 |
| [12-practice-feature-requirements.md](12-practice-feature-requirements.md) | 从入门到高级的吉他能力体系、练习分类、指导模式、数据与验收要求 | 产品、内容、设计、客户端、测试 |

首轮可交互视觉稿见 [ui-prototype.html](ui-prototype.html)，可直接在桌面或移动浏览器中打开评审。

移动端曲谱学习原型见 [guitar-sheet-mobile-prototype.html](guitar-sheet-mobile-prototype.html)，以手机窄屏为主要画布，包含曲谱首页、曲谱库、曲谱详情、专注跟练、速度控制、循环练习和弦教学与导入入口。


节拍器移动端专项设计见 [metronome-design.md](metronome-design.md)，可交互原型见 [metronome-prototype.html](metronome-prototype.html)，支持真实节拍声音、Tap 测速、拍号与重音配置。

练习模块移动端视觉原型：

- [practice-ui-aurora.html](practice-ui-aurora.html)：极光深色，强调沉浸、层次和练习主行动。
- [practice-ui-editorial.html](practice-ui-editorial.html)：黑白编辑风，强调内容秩序、文字指导和长期学习感。
- [practice-ui-neon.html](practice-ui-neon.html)：霓虹节拍风，强调 BPM、节奏、状态和训练氛围。

独立概念探索原型（不受现有导航和主题约束）：

- [practice-ui-pulse-os.html](practice-ui-pulse-os.html)：把练习设计成个人训练操作系统，用“今日任务”和能力模块替代传统首页卡片。
- [practice-ui-orbit.html](practice-ui-orbit.html)：能力星轨，把技能之间的前置关系和成长路径可视化。
- [practice-ui-stage.html](practice-ui-stage.html)：舞台排练模式，把每次练习包装成一场演出前的场景排练。

当前 Android UI 已在根目录 `app` 模块落地。使用 JDK 17 与已安装的 Android SDK，可执行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

建议首次按表格顺序阅读。实施过程中以 PRD 的范围为准，以 Backlog 的验收标准为交付依据。

## 4. 产品结构

产品能力分为四层，但用户界面不把四层做成彼此隔离的四个系统。

| 层级 | 解决的问题 | 代表能力 |
|---|---|---|
| 工具层 | 我现在需要完成一个动作 | 调音、节拍、和弦查询、移调、Capo |
| 知识层 | 为什么这样弹 | 持琴、品格、节奏、音程、音阶、和弦、和声 |
| 练习层 | 怎样稳定掌握 | 和弦按法、转换、扫弦、跟拍、指板、技巧 |
| 应用层 | 怎样在真实音乐中使用 | 曲谱、分段练习、整曲演奏、曲目管理 |

统一领域关联是产品核心。例如用户在曲谱中点击 `C` 和弦，可以查看指法、理解组成、进入 `Am -> C` 转换练习，完成后返回原曲原位置。

## 5. MVP 定义

MVP 的产品承诺是：

> 帮助一个没有吉他和乐理基础的中文用户，在无真人指导的情况下，从完成标准调弦开始，循序学习必要和弦与节奏，并完成第一首合法授权的民谣练习曲。

MVP 必须包含：

- 标准六弦调音器。
- 稳定、可练习的基础节拍器。
- 5 至 8 个开放和弦及可访问的指法说明。
- 8 至 12 节零基础微课程。
- 和弦转换与基础节奏练习。
- 1 条完整新手路径和 3 至 5 首合法练习曲。
- 本地进度、练习记录、收藏和曲谱导入。
- 合规免费资源的搜索接口及来源标识；具体供应源须在上线前单独评审。

## 6. 术语

| 术语 | 含义 |
|---|---|
| 内容 | 课程、知识点、和弦、节奏型、练习定义、曲谱及媒体素材 |
| 能力 | 用户可以理解或完成的最小技能，如“识别六根弦”“完成 G-D 转换” |
| 学习路径 | 按前置关系组织的一系列知识、练习和曲目应用 |
| 练习会话 | 用户一次开始至结束的结构化练习过程 |
| 曲谱资源 | 应用内置、用户导入或在线检索到的曲谱及其元数据 |
| 本地优先 | 本地数据是使用体验的主要依据，断网不阻塞核心功能 |
| 授权来源 | 访问方式、展示范围、许可、署名和缓存规则均已验证的资源提供方 |

## 7. 文档维护

- 功能范围变化先更新 PRD，再更新设计、Roadmap 和 Backlog。
- 所有内容对象使用稳定 ID，标题变化不能导致用户进度丢失。
- 技术决策变化应在 `08-risks-and-decisions.md` 增加或替代 ADR，不直接删除历史决策。
- 每个上线曲目必须有可追溯的许可和来源记录。
- 未验证的第三方曲谱站点不得写成既定依赖或生产端点。
