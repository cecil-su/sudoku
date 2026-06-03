# 任务计划：安卓数独游戏

## 目标
开发一个功能完整、体验流畅的 Android 数独游戏，支持多难度级别、笔记标记、提示、计时等核心功能。

## 当前阶段
阶段 10 进行中：**10.1 演示引擎 + 10.2 演示播放器 UI + 10.3 多 Provider 设置后台 + 10.4 DeepSeek 接入 + 语音 已完成**（10.1/10.2：2026-06-02；10.3/10.4：2026-06-03）。10.1：三方检测统一到 `stepForward` 共享内核 + `DemoStep.eliminatedCells` + `demoTrajectory`（修复链式断点）。10.2：离线演示播放器（`DemoController` 纯状态机 + 棋盘分阶段高亮叠加层 + 字幕/播放控制 + 默认静音 TTS + 「轮到你」复现校验 + 会话埋点起步）。10.3：`AiProvider`/`AiSettings` 不可变模型 + `ProviderRepository`（DataStore/org.json）+ 设置入口/路由 + Provider 列表/编辑/增删改/切换 + 预置&手动模型；55 单测全过 + `assembleDebug` 出 APK。**范围调整（个人自用）**：砍掉 10.4「首次启用全屏知情同意」P0，连带「🔌测试连接 / 🔄刷新 `/models` / 推理模型请求容错」（均需出站网络）下移 10.4（本会话已随 10.4 一并落地）。10.4：`AiClient`（非流式 HTTP）+ `AiCoach`（function-calling 第二控制器）+ `VoiceInput`（中文 STT）+ TTS 朗读 + 资源绑 onStop + 接通测试连接/`/models`；66 单测全过 + APK，**设备级未测**。**10.4 收口（2026-06-03 续）**：AI 层静态复审（**无崩溃类 bug**；5 项较低级遗留）+ **设备冒烟清单已出** + **基线复验** + **修复 S1–S3**：S1 https 强制（抽纯函数 `isSecureUrl` 设 `request()` 单 chokepoint + 编辑页同函数闸/红框，https-only）、S2 无模型 provider 门控（`AiProvider.isUsable` → `aiAvailable`）、S3 翻页 vs AI 竞态（`!aiBusy` 守卫）；新增 `AiClientTest`/`AiProviderTest`，**75 单测全绿 + 10.28MB APK**。S4（畸形 200）/S5（备份带 key）留作可选未做（详见 findings/progress 同日"收口"条）。**下一步：真机跑冒烟清单（唯一未闭环面）/ 10.5 复盘飞轮（P1）/ 发版 v1.10.0（建议真机后）**。v1.x 主体（阶段 1-9）已全部完成。

## 各阶段

### 阶段 1：需求确认与架构设计
- [x] 确认核心功能需求（难度、提示、笔记、撤销等）
- [x] 确定技术栈 → Kotlin + Jetpack Compose
- [x] 设计整体架构 → MVVM（ViewModel + StateFlow）
- [x] 设计数据模型（棋盘、单元格、游戏状态）
- [x] 将决策记录到 findings.md
- **状态：** complete

### 阶段 2：项目搭建
- [x] 创建 Android 项目基础结构
- [x] 配置 Gradle 依赖（Compose、ViewModel、Room 等）+ Version Catalog
- [x] 建立包结构（model / viewmodel / ui / engine）
- [x] 初始化 Git 仓库 + .gitignore
- [x] 配置 GitHub Actions CI/CD（ci.yml + release.yml）
- [x] 配置 APK 签名（release 通过环境变量 + GitHub Secrets）
- [x] 验证项目可编译 + 单元测试通过
- **状态：** complete

### 阶段 3：数独核心引擎
- [x] 实现数独求解器（回溯 + MRV 启发式优化）
- [x] 实现数独生成器（对角填充 → 随机求解 → 挖空 + 唯一解验证）
- [x] 实现验证逻辑（行/列/宫冲突检测）
- [x] 难度分级逻辑（5 级：入门/简单/中等/困难/专家）
- [x] 单元测试核心算法（17 个测试全部通过）
- **状态：** complete

### 阶段 4：UI 界面实现
- [x] 9×9 数独棋盘组件（Compose Canvas 绘制）
- [x] 数字输入面板（1-9 + 清除 + 已满变灰）
- [x] 选中高亮（同行/同列/同宫/相同数字，4 层颜色）
- [x] 笔记模式 UI（3×3 固定布局 + 面板变色）
- [x] 错误标红显示
- [x] 主题配色（亮色/暗色自动适配）
- [x] 主页 → 难度选择 → 游戏画面导航
- [x] ViewModel 完整实现（输入/笔记/撤销/重做/提示/计时器）
- **状态：** complete

### 阶段 5：游戏功能实现
- [x] 新游戏 / 难度选择界面（已在阶段 4 完成）
- [x] 计时器功能（暂停/恢复 + 生命周期感知）
- [x] 提示功能（揭示正确答案，每局限 3 次）
- [x] 撤销/重做功能（无限撤销，栈深 100）
- [x] 笔记（候选数）标记功能
- [x] 自动移除笔记（填入数字后清理同行列宫笔记）
- [ ] 完成庆祝动画（可选优化）
- **状态：** complete

### 阶段 6：数据持久化与状态管理
- [x] 游戏进度自动保存（JSON 文件，每 30 秒 + 暂停时保存）
- [x] 暂停/恢复游戏（主页"继续游戏"按钮）
- [x] 游戏统计（DataStore：完成数、最佳时间/难度）
- [x] 完成后自动删除存档、记录统计
- **状态：** complete

### 阶段 7：测试与优化
- [x] 核心算法单元测试（Solver/Generator/Validator 已有）
- [x] 代码审查（P0-P3 问题识别）
- [x] 修复竞态条件（Mutex 保护文件写入、原子化状态更新）
- [x] 修复内存泄漏（redo 栈限制、计时器单实例保证）
- [x] 性能优化（numberCounts 预计算、避免每帧重算）
- [x] 输入验证（边界检查）
- [x] 返回主页清理（exitGame）
- **状态：** complete

### 阶段 8：打磨与交付
- [x] 推送代码到 GitHub (cecil-su/sudoku)
- [x] 创建 v1.0.0 tag 触发自动发版
- [x] 完成庆祝动画（缩放+淡入，返回主页按钮）
- [x] 应用图标优化（完整 9×9 网格 + 中心数字 9）
- [x] Release 签名配置（keystore + GitHub Secrets）
- **状态：** complete

### 阶段 9：教学式提示系统（v1.9.0）
- [x] `LogicSolver` 新增只读 `findHint(board)`：按技巧优先级返回"下一步"（技巧名 + 中文讲解 + 高亮格 + 可填落子）
- [x] 覆盖 1-6 级技巧 → 对任意"无错误"的局面必有下一步（与生成器难度上限一致）
- [x] **不触碰** `analyze`/`analyzeWithCap`/生成器（生成热路径零风险）
- [x] `GameViewModel`：提示由"抄答案"改为"先查错→求逻辑下一步→展示讲解"；新增 `applyHint`/`dismissHint`；任何棋盘/选择变化清除过期提示
- [x] `SudokuBoard` 支持 `hintCells` 琥珀色描边高亮
- [x] `GameScreen` 数字面板上方渲染讲解卡片（填入 / 知道了）
- [x] 单元测试：逐步解谜 + 落子恒等唯一解 + 生成谜题必有提示
- [x] 质量清理（/simplify）：抽出共享 `commitValue`、合并 `useHint` 分支、`boxCells` 表达式化
- [x] 正确性复审 + 测试加强：4 个高级检测器改 `internal`，手搓候选位图直测（区块排除/显性数对/隐性数对/X-Wing），`LogicSolverTest` 11 测试全过
- **状态：** complete

### 阶段 10：AI 语音教练 —— "演示 + 讲解"模式（规划中）

> **设计定调（2026-06-02 讨论确定，详见 findings 同日"AI 语音教练"条目）：**
> - **核心体验**：把 findings 早期设想的"提示第 3 级（动画演示推理过程）"做成主体验——棋盘上**分步高亮 + 旁白讲解的"带旁白走查"**，不是聊天框。
> - **分层**：确定性演示骨架先行（离线可跑），DeepSeek 作"第二控制器"后插，**骨架零返工**。
> - **控制**：离线 = 按钮；有 AI = 按钮常驻 + 可对话（function calling 驱动同一个 `DemoController`）。
> - **grounding**：演示时间线由引擎生成，LLM 只在轨迹上导航/讲解，不自行解题。

> **评审加固（2026-06-02 · 第三会话，4-agent 评审）：** 范围**维持原决策**（完整版多 provider、本期上语音）；5 个 P0 修复已并入下方子阶段（标 `[评审P0]`）：① 引擎抽共享内核 + `DemoStep` 含被排除格；② AI 资源绑 onStop（非 viewModelScope）；③ "轮到你"复现校验；④ 会话埋点上移 P0；⑤ 首次全屏知情同意。评审提的 P1/P2 待办见 findings 同日条目。

#### 10.1 演示引擎（确定性骨架 · 离线）
- [x] **[评审P0]** 在 `LogicSolver` 内部抽出三方共享的"前进一步"内核 `stepForward(board, cands): DemoStep?`——`analyze`/`analyzeWithCap`（**公开签名/返回语义不变**，生成器依赖）、只读 `findHint`、新轨迹引擎三者**共用同一内核**。实现：六个检测器改为"只检测不变更"返回 `DemoStep?`，`applyStep` 统一推进状态；删掉旧的 6 个"检测即变更"突变函数 + `applyNextTechnique` + 6 个 `*Hint` 检测器（两套重复检测合一；`LogicSolver.kt` 661→487 行，净 −174，已含新增的 `eliminatedCells` 收集与 `stepForward`/`applyStep`/`demoTrajectory`），彻底消灭"第三套检测"
- [x] **[评审P0]** `DemoStep` 含**被排除的格/候选** `eliminatedCells`：`DemoStep(level, 技巧名, lookCells, eliminatedCells, placement?, narration)`；检测器把内部 `any{}` 判定的消除目标真正收集成 `List<Elimination(row,col,values)>`（演示最关键的一帧）
- [x] 轨迹**携带候选状态前进**：新增公开 `demoTrajectory(board): List<DemoStep>`，走 `stepForward`+`applyStep` 循环每步 emit 一个 `DemoStep` → 链式走查成立，**顺手修复 findings 记录的"链式断点"**（消除步解锁的 single 现在能被走到）。`findHint` 改为 `stepForward(...)?.toHint()`（保持 `Hint` 形状不变，UI 零改动；首步与轨迹首步天然一致）
- **verify**：`LogicSolverTest` 现 14 测试全过（+34 全工程绿，含 4 个生成器测试证热路径不变；3 次随机重跑全绿）——① `demoTrajectory` 对 5 个难度均从当前局面走到合法终局（HARD/EXPERT 需消除解锁，证明状态前进/链式修复）；② 每步 看/排除/结论 自洽（单数=填入·level≤2 / 消除=擦候选·level≥3）；③ 同一谜题 `findHint` 首步 == 轨迹首步（证三方共用内核）；④ 4 个高级检测器直测 `eliminatedCells` 内容
- **状态：** complete

#### 10.2 演示播放器 UI（离线 · 按钮控制）
- [x] `DemoController`（`model/DemoController.kt`，**不可变纯状态机**：steps[] / currentIndex；next/prev/replay/jumpTo/gotoCell 全部返回新实例）；VM 持 `_demo: StateFlow<DemoController?>`（沿用 `_activeHint` 隔离）
- [x] `SudokuBoard` 扩展：新增 `demoHighlight: DemoHighlight?` 参数 → 分阶段多色高亮（看=琥珀 / 被排除=橙 / 结论=绿+描边）+ **被排除候选数带删除线**（`eliminatedCells` 那一帧）。**独立 `drawDemoBackgrounds` 分支**，不碰原 `drawCellBackgrounds` 的选中 `when{}`（阶段 9 高亮零改动）；演示态 `readOnly` 忽略点击
- [x] 局内演示面板（`ui/component/DemoPlayer.kt`）：棋盘在上为主角（数字盘隐藏让位）+ 字幕条（技巧名 + 旁白 + `n/N` 步数）+ 播放控制（◀上一步 / 下一步▶ / ⟲重播 / ✕退出）；演示态棋盘只读。入口：顶栏「▶演示」图标（用户选）
- [x] 逐步点击驱动；旁白用引擎模板串（`DemoStep.narration`）+ Android `TextToSpeech`（**默认静音**·`🔇/🔊` 可开，用户选）；TTS 绑 `DisposableEffect` 创建/`onDispose` shutdown + `ON_STOP` stop；中文引擎不可用则隐藏声音键回退字幕
- [x] **[评审P0]"轮到你"复现校验**：落子步上「✋我来填」按钮（可选·按需，用户选；不打断纯走查）→ 藏结论、棋盘该格显「?」、字幕给 1-9 让玩家填；对→「答对了！」、错→即时讲技巧 + 揭晓答案（复用 `DemoStep.placement` 校验）。`DemoChallenge` 隔离在 VM
- [x] **[评审P0]会话埋点起步**：`model/SessionTelemetry.kt`（不可变）记录 求助次数 / 错误 / 演示次数 / 复现对错 / **卡壳技巧**（techniqueHelpCounts）；VM 持 `_telemetry`，**只埋不展示、仅本地、不进 `GameState`、不外发**（沿用 `_activeHint` 隔离）。区域停留留作后续细化。10.4 追加 AI 事件、10.5 做复盘 UI
- **verify**（量化）：✅ 编译 + `assembleDebug` 出 APK；47 单测全过（`DemoControllerTest` 7：空/单步/next/prev/replay/jumpTo 钳位/gotoCell；`SessionTelemetryTest` 5）。逻辑判据自洽：步数=`demoTrajectory` 长度（`下一步`走到 `isAtEnd`，终局 `buildDemoBoard` 全落子=解开）；每步高亮=`controller.current` 的 `DemoStep` 格；prev/replay 回 0；退出不污染（演示从不写 `_state`，棋盘为派生显示态）；静音+无网可走完 + 一次复现。**设备级手动点击待做**（无模拟器）
- **状态：** complete

#### 10.3 多 Provider 设置后台（离线部分）

> **范围调整（2026-06-03）：** 用户裁定本 App 为个人自用 → **砍掉 10.4「首次启用全屏知情同意」P0**。连带「🔌测试连接 / 🔄刷新 `/models` / 推理模型请求容错」因都需出站网络（且原与同意闸门时序耦合），一并**下移到 10.4** 与 HTTP 层同落。10.3 = 纯离线 Provider 后台（无 `INTERNET` 权限）。

- [x] 数据模型：`AiProvider`（id/name/baseUrl/apiKey/models/activeModel）+ `AiSettings`（providers[] + activeProviderId，**不可变纯转换** upsert/remove/setActive/setModel，沿用 `SessionTelemetry` 范式）；`ProviderRepository` 存 DataStore（**org.json 单 JSON 串**，照 `GameRepository` 范式，key 明文 BYOK，损坏 JSON 降级空设置）
- [x] 主页加设置入口（齿轮，`Column` 内 `align(End)` 不重排既有布局）；NavHost 加 `settings` / `provider_edit?providerId={id}` 路由；`SettingsViewModel`（`AndroidViewModel` + `viewModel()`，沿用 `GameViewModel` 范式）
- [x] Provider 列表（卡片 + 单选「当前」+ FAB 添加 + 空态）→ 编辑页（名称 / baseURL / Key 遮罩+显隐 / 模型 ID + 预置快填&建议 chips[预置 + 手动两种来源]）
- [→10.4] 🔌测试连接 / 🔄刷新 `/models` / 请求构造容忍推理模型差异（reasoner/o1 不吃 temperature）—— 三者均需 HTTP + `INTERNET`，随 10.4 HTTP 层同落
- **verify**：✅ 编译全工程 + `assembleDebug` 出 APK；55 单测全过（新增 `AiSettingsTest` 8：空态 / 首个自动激活 / 不抢激活 / 同 id 替换 / 删激活回退首个 / 删尽清空 / setActive 忽略未知 / setModel 仅改目标）。**设备级手动点击待做**（增删改/切换/预置快填/Key 显隐/空态）
- **状态：** complete（离线部分；网络部分见 10.4）

#### 10.4 DeepSeek 接入 + 语音（增强层）

> **简化（2026-06-03，个人自用）：** ① **非流式**——`/chat/completions` 整段返回再显示/朗读（function-calling 本就短），SSE 流式留作后续打磨；② 限流只做**会话内超时/错误降级**，**每日上限**（需持久化）留作后续；③ JSON 用 `org.json`（零新依赖），未引 kotlinx.serialization。

- [x] HTTP 客户端：`ai/AiClient.kt`（`HttpURLConnection` 非流式 + org.json，`/chat/completions` + `/models`，15s/30s 超时，best-effort 取消）；`INTERNET` 权限；推理模型（reasoner/o1-o4）省略 `temperature`
- [x] **[从 10.3 下移]** Provider 编辑页接通：🔌测试连接 + 🔄`/models` 刷新（共用 `fetchModels`，成功=可达+key 有效，拉到的模型成第三来源）
- [x] **第二控制器**：`ai/AiCoach.kt`——意图经 function calling 映射到 `DemoController`（next/prev/replay/jumpTo/gotoCell）；工具分发抽纯函数 `applyCoachTool`（1-based↔0-based，11 单测）；导航后回灌 tool 结果让模型讲解（≤4 轮，兜底补 assistant 保历史合法）
- [x] 语音 I/O：`ai/VoiceInput.kt`（`SpeechRecognizer` 中文 zh-CN）；`RECORD_AUDIO` 运行时权限（`rememberLauncherForActivityResult`）；TTS 念回复（复用 10.2，默认静音）；barge-in（点麦克风停 TTS）
- [x] system prompt 契约：耐心教练 + 盘面 givens + 引擎已验证轨迹（逐步列出）+ 当前位置 + 工具；铁律"只导航/讲解、禁止自行解题"
- [x] 按钮常驻 + 可对话（演示面板下方文字框 + 🎙️，与翻页按钮并存）；未配 provider → 无 AI UI（纯 10.2）；断网/出错 → `coachReply` 显示 ⚠️、演示仍可按钮走查
- [x] **[评审P0]资源生命周期绑界面**：网络（`aiScope` **非 viewModelScope**，`ON_STOP` `cancel()` 并重建）/ `SpeechRecognizer`（`onDispose` destroy、`ON_STOP` stop）/ `TextToSpeech`（已绑）——退后台即停，不泄漏/不后台录音
- [x] ~~**[评审P0]首次启用全屏知情同意**~~ → **已砍（用户裁定个人自用）**；AI 以"未配置 provider = 无 AI"自然门控
- [x] 追加 AI 使用事件到会话埋点：`SessionTelemetry.recordAiUsed`（仍仅本地、不外发）
- [x] 横切：超时（连 15s/读 30s）+ 错误降级（401/402/404/429/5xx 友好中文映射）；~~每日上限~~ 留后续
- [→后续] SSE 流式输出 / 每日调用上限（需持久化）
- **verify**：✅ 编译全工程 + `assembleDebug` 出 APK；66 单测全过（新增 `AiCoachToolTest` 11：工具分发 1-based↔0-based + conclusion 渲染）。⚠️ **网络/STT/TTS/权限均为设备运行时行为，无模拟器只能编译+逻辑自洽，"真机可用"待跑**（配 key→测试连接→演示中文字/语音问"下一步/为什么这格"；断网/未配 key 回退；退后台麦克风停）
- **状态：** complete（设备级未测；**静态已复审 ✓**、**冒烟清单已出 ✓**、**基线复验 ✓**、**S1–S3 已修+测试兜底 ✓** 75 单测+10.28MB APK；S4/S5 可选未做）

#### 10.5 复盘 + 飞轮（P1 · 本期可不做）
- [ ] ~~会话埋点~~ → **已上移到 10.2（P0）**，P0 期即开始攒数据，避免"数据断流"
- [ ] 复盘全屏页（复用演示播放器回放整局 + 读取 10.2 埋点）+ 按弱项定向出题
- **状态：** pending

## 关键问题
1. ~~使用 Jetpack Compose 还是传统 XML 布局？~~ → **Jetpack Compose**
2. ~~是否需要在线功能？~~ → **不需要，纯离线单机**
3. ~~最低支持的 Android 版本？~~ → **API 26 (Android 8.0)**
4. ~~是否需要广告/内购？~~ → **暂不需要**

## 已做决策
| 决策 | 理由 |
|------|------|
| 使用 Kotlin | Android 官方推荐语言，现代特性丰富 |
| MVVM 架构 | Android 官方推荐架构，配合 ViewModel + StateFlow |
| Jetpack Compose | 原生开发体验最佳，声明式 UI 高效 |
| API 26 (Android 8.0) | 覆盖 95%+ 设备，支持现代 API |
| 纯离线单机 | 无需后端，降低复杂度 |
| 暂无广告/内购 | 保持简洁，专注核心体验 |
| GitHub Actions CI/CD | push 自动构建检查，打 tag 自动发 Release + APK |
| AI 教练以"演示+讲解"为核心 | 把"提示第3级"做成主体验，直击差异化"真正学会数独" |
| 确定性骨架 + AI 增强分层 | 离线可跑、grounding 天然成立、骨架零返工 |
| 多 provider BYOK + 可配 baseURL | provider 无关、守住"无后端"基因、不堵代理路 |
| 离线=按钮 / 有AI=按钮+对话 | 翻页按钮永远比语音快/省/稳；AI 赢在按钮表达不了的意图 |
| 个人自用 → 砍知情同意闸门 | 单用户 BYOK、自己即开发者，两级全屏同意屏属过度设计；网络项随 HTTP 层下移 10.4 |
| Provider 持久化用 org.json | 照 `GameRepository` 既有范式，零新依赖；kotlinx.serialization 留到 10.4 网络 DTO 再引 |

## 遇到的错误
| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
|      | 1       |         |

## 备注
- 随着进度更新阶段状态：pending → in_progress → complete
- 做重大决策前重新读取此计划
- 记录所有错误，避免重复
