# 进度日志

## 会话：2026-06-03

### 提交 10.1 + 10.2（拆两提交）
- **状态：** complete
- 续接前 10.1/10.2 代码全部悬在工作区未提交。先跑 `:app:testDebugUnitTest` 闸门（绿），再拆两个提交：
  - `91dc66d` refactor(engine)：10.1 共享内核（`LogicSolver` + `DemoStep` + 测试，+347/−443）
  - `9375427` feat(demo)：10.2 离线演示播放器（14 文件，+1044/−50，6 新文件）
- `.claude/settings.local.json` 按约定不进功能提交。

### 阶段 10.3：多 Provider 设置后台（离线部分）
- **状态：** complete（离线部分）
- 背景：实现 AI 教练的配置后台——Provider 增删改/切换 + 模型选择，纯离线，为 10.4 接 DeepSeek 铺路。
- **两个范围决策（用户裁定本 App 个人自用）：**
  1. **砍掉 10.4「首次启用全屏知情同意」P0**：单用户 BYOK、自己即开发者，两级全屏同意屏属过度设计。AI 仍"默认关"，但改由"未配置 provider = 无 AI"自然门控。
  2. **网络项下移 10.4**：「测试连接 / `/models` 刷新 / 推理模型请求容错」均需出站网络（原与同意闸门时序耦合），随 10.4 HTTP 层同落。10.3 = 纯离线后台，不加 `INTERNET` 权限。
- 执行的操作（5 步增量，每步验证）：
  - `model/AiProvider.kt` + `model/AiSettings.kt`：不可变模型 + 纯转换（upsert/remove/setActive/setModel），沿用 `SessionTelemetry`/`DemoController` 范式。`AiSettingsTest` 8 测试（纯转换，避开 `org.json` 在 JVM 单测不可用）。
  - `data/ProviderRepository.kt`：DataStore + `org.json` 单 JSON 串持久化，照 `GameRepository` 范式（零新依赖），损坏 JSON 降级空设置。
  - `viewmodel/SettingsViewModel.kt`：`AndroidViewModel`，`stateIn` 暴露 `settings`，三个 intent（save/delete/select）。
  - `ui/screen/SettingsScreen.kt`：Provider 列表（卡片 + 单选「当前」+ FAB + 空态）。`ui/screen/ProviderEditScreen.kt`：名称/baseURL/Key 遮罩+显隐/模型 ID + 预置快填&建议 chips（DeepSeek/OpenAI 预置 + 手动）。图标全用 material-icons-core 自带（不引扩展包）。
  - `MainActivity` 接线：`SettingsViewModel` + `settings`/`provider_edit?providerId={id}` 路由；`HomeScreen` 右上角齿轮（`Column` 内 `align(End)`，不重排既有居中布局）。
- 验证：编译全工程 + `assembleDebug` 出 APK；55 单测全过（+8 `AiSettingsTest`）。设备级手动点击未做（无模拟器）。
- 创建/修改的文件：
  - 新增：`model/AiProvider.kt`、`model/AiSettings.kt`、`data/ProviderRepository.kt`、`viewmodel/SettingsViewModel.kt`、`ui/screen/SettingsScreen.kt`、`ui/screen/ProviderEditScreen.kt`、`test/.../AiSettingsTest.kt`
  - 修改：`MainActivity.kt`、`ui/screen/HomeScreen.kt`
- 下一步：10.4 DeepSeek 接入 + 语音（HTTP/SSE + function calling 第二控制器 + 语音 I/O + 资源绑 onStop；顺带接通测试连接/`/models` 刷新）。

## 会话：2026-06-02

### 阶段 9：教学式提示系统（v1.9.0）
- **状态：** complete
- 背景：`LogicSolver` 能按人类技巧逐级推理，但只用于生成时打难度标签；对局提示却只是抄答案。本次把这套引擎接到提示按钮，从"给答案"变为"教技巧"。
- 执行的操作：
  - `LogicSolver` 新增只读 `findHint`（唯一余数 / 隐性唯一数 / 区块排除 / 显性数对·三数组 / 隐性数对 / X-Wing，各带中文讲解与高亮格），不改动 `analyze`/生成器
  - `GameViewModel`：重写 `useHint`（先查错→求逻辑下一步→展示），新增 `applyHint`/`dismissHint`/`findWrongCell`，并在 `selectCell`/`inputNumber`/`clearCell`/`undo`/`redo`/`newGame`/`continueGame` 清除过期提示
  - `SudokuBoard` 新增 `hintCells` 描边高亮；`GameScreen` 新增讲解卡片 `HintCard`
  - 新增 `Hint` 数据模型、琥珀色高亮配色
  - `LogicSolverTest` 新增 3 个测试；版本 → 1.9.0 / versionCode 6
- 验证：`:app:testDebugUnitTest` 编译（含 Compose UI）+ 全部单元测试通过。设备级手动点击未做。
- 创建/修改的文件：
  - 新增：`model/Hint.kt`
  - 修改：`engine/LogicSolver.kt`、`viewmodel/GameViewModel.kt`、`ui/component/SudokuBoard.kt`、`ui/screen/GameScreen.kt`、`ui/theme/Color.kt`、`app/build.gradle.kts`、`test/.../LogicSolverTest.kt`

### 阶段 9 收尾：质量清理 + 复审 + 测试加强
- **状态：** complete
- `/simplify`（4 agent 并行）：抽出共享 `commitValue`（手动输入与提示填入共用提交路径）、合并 `useHint` 的 placement 分支、`boxCells` 改表达式体；其余按"刻意隔离/符合惯例"保留。
- 正确性复审：核心逻辑无 bug；落子恒等唯一解、次数经济与撤销均与改前一致。挖出一个限制（见 findings 2026-06-02 第 2 条）。
- 测试加强：四个高级检测器（区块排除/显性数对/隐性数对/X-Wing）改 `internal`，新增 4 个用手搓候选位图直测的单测。`LogicSolverTest` 现 11 测试全过。

### 阶段 10 规划：AI 语音教练（演示 + 讲解）—— 设计定调
- **状态：** 设计完成，待实现（本轮纯设计讨论，未写代码）
- 缘起：用户提出"项目能否结合 AI"。多轮讨论从"拍照导入/教练/飞轮"等脑洞，收敛到把 findings 早期设想的"提示第 3 级（动画演示推理）"做成核心体验——AI 语音教练以**"演示 + 讲解"**形态呈现。
- 关键决策（详见 findings 同日"AI 语音教练"条目）：
  - 形态：棋盘分步高亮 + 旁白的"带旁白走查"，**不是聊天框**；UI = 叠在棋盘上的"演示播放器"。
  - 分层：**确定性演示骨架（离线可跑）先行**，DeepSeek 作"第二控制器"后插。
  - 控制：离线 = 按钮；有 AI = 按钮常驻 + 语音对话（**function calling** 驱动同一个 `DemoController`）。
  - 配置：完整版多 provider 后台，可配 baseURL/apiKey/model（BYOK），模型清单混合（预置 + 拉 `/models` + 手动）。
  - grounding：演示轨迹由引擎生成，LLM 只导航/讲解、不自行解题。
- 牵出的引擎前置工作：现 `findHint` 无状态（不保留消除，即"链式断点"）→ 演示轨迹须携带候选状态前进，顺手修复该限制；"按格解释"需新增按格分析；"复盘"需会话埋点（列 P1）。
- 产出：task_plan.md 新增阶段 10（10.1 演示引擎 / 10.2 播放器 UI / 10.3 多 provider 设置 / 10.4 DeepSeek+语音 / 10.5 复盘飞轮 P1）。
- 读过的现有代码：`LogicSolver`、`Hint`、`GameScreen`/`HomeScreen`/`GameTopBar`、`Theme`/`Color`、`MainActivity`（确认导航是 Navigation-Compose，加屏便宜；GameScreen 已有"数字盘上方卡片槽"可复用；TopBar 有空位放 🎙️）。

### 阶段 10 评审：4-agent 并行评审 + P0 加固
- **状态：** 评审完成，5 个 P0 已并入计划（仍未写实现代码）
- 操作：用 4 个并行 agent（架构可行性 / 范围简单性 / 风险横切 / 产品教学）只读评审 task_plan 阶段 10；各 agent 实际读了 `GameViewModel`/`SudokuBoard`/manifest/build.gradle 等核对现状。
- 结论：架构地基四视角一致认可；四视角各抓到一个本领域 P0（几乎不重叠）。
- **用户裁决**：范围维持原决策（完整版多 provider + 本期上语音），**P0 全收**。
- 改动文件：
  - `task_plan.md`：阶段 10 并入 5 个 P0（标 `[评审P0]`）——10.1 共享内核+`DemoStep.eliminatedCells`、10.2 复现校验+埋点+量化 verify、10.4 资源绑 onStop+知情同意、10.5 埋点上移说明；「当前阶段」行更新。
  - `findings.md`：新增「设计评审（第三会话）」——5 个 P0 + 11 条 P1/P2 待办（防丢）。
- 未写任何实现代码。

### 阶段 10.1：演示引擎（确定性骨架 · 离线）
- **状态：** complete
- 背景：实现阶段 10 第一块——把 `LogicSolver` 的三条检测路径（生成器用的 `analyze`/`analyzeWithCap`、对局提示用的 `findHint`、以及演示要用的轨迹）**统一到一个共享内核**，并产出含"被排除候选"的步骤轨迹，作为后续演示播放器（10.2）的数据源。
- 执行的操作：
  - 新增 `model/DemoStep.kt`：`DemoStep(level, techniqueName, lookCells, eliminatedCells, placement?, narration)` + 内嵌 `Elimination(row,col,values)` / `Placement(row,col,value)`。`eliminatedCells` 是演示最关键的一帧（消除类技巧的"结论"是擦候选而非填格）。
  - 重写 `engine/LogicSolver.kt`：
    - 抽出私有共享内核 `stepForward(board, cands): DemoStep?`（只检测、不变更，按技巧优先级链 6 个检测器）+ `applyStep(board, cands, step)`（统一推进：有 placement 就落子，否则按 `eliminatedCells` 擦候选）。
    - 六个检测器由"检测即变更（返回 Boolean）"改为"只检测返回 `DemoStep?`"，并把原先 `any{}` 一笔带过的消除目标**真正收集成 list**。
    - `analyze`/`analyzeWithCap` 改走 `stepForward`+`applyStep` 循环——**公开签名/返回语义逐字保持**（生成器零感知）。
    - `findHint` 改为 `stepForward(board, initCandidates(board))?.toHint()`——投影回 `Hint`，UI/ViewModel 零改动；首步与轨迹首步天然一致。
    - 新增公开 `demoTrajectory(board): List<DemoStep>`——携带候选状态前进，**顺手修复"链式断点"**（消除步解锁的 single 现在能被走到）。
    - 删除旧的 6 个突变检测器 + `applyNextTechnique` + 6 个 `*Hint` 检测器（两套重复检测合一；`LogicSolver.kt` 661→487 行，净 −174，已含新增逻辑；彻底消灭评审担心的"第三套检测漂移"）。
  - `LogicSolverTest`：4 个高级检测器测试改名到 `*Step` + `.lookCells` 并新增 `eliminatedCells` 断言；新增 3 个轨迹测试（各难度走到合法终局 / 每步自洽 / findHint 首步==轨迹首步）。
- 验证：`:app:testDebugUnitTest` 全绿——`LogicSolverTest` 14 测试、全工程 34 测试 0 失败；其中 4 个 `SudokuGeneratorTest` 通过证明热路径行为不变；`--rerun-tasks` 随机重跑 3 次全绿（生成器/轨迹是随机谜题，重跑加固）。
- 未做：版本号不动（10.1 纯引擎+测试、无 UI/无用户可见变化；版本待 10.2+ 上 UI 再升）。未提交（用户未要求）。
- 创建/修改的文件：
  - 新增：`model/DemoStep.kt`
  - 修改：`engine/LogicSolver.kt`、`test/.../LogicSolverTest.kt`

### 阶段 10.2：演示播放器 UI（离线 · 按钮控制）
- **状态：** complete
- 背景：把 10.1 的 `demoTrajectory` 接到界面——棋盘分步高亮 + 字幕走查解题的"演示播放器"，离线纯按钮可跑；含两个评审 P0（「轮到你」复现 + 会话埋点起步）。
- 用户拍板（AskUserQuestion，3 问全选推荐）：① 「轮到你」= **可选·按需挑战**（不打断纯走查，保 verify 主流程）；② TTS **默认静音**·可手动开；③ 演示入口 = **顶栏「▶演示」图标**。
- 执行的操作：
  - 新增 `model/DemoController.kt`（不可变纯状态机：steps/currentIndex + next/prev/replay/jumpTo/gotoCell 返回新实例）+ `DemoControllerTest`（7 测试）。
  - 新增 `model/SessionTelemetry.kt`（不可变埋点模型：hints/errors/demos/challenges/techniqueHelpCounts）+ `SessionTelemetryTest`（5 测试）。
  - 新增 `model/DemoChallenge.kt`（「轮到你」会话态，放 model 层避免 ui→viewmodel 反向依赖）。
  - `ui/component/SudokuBoard.kt`：加 `demoHighlight: DemoHighlight?` + `drawDemoBackgrounds`（看/排除/结论三色填充 + 结论描边）+ `drawDemoMarks`（被排除候选数带删除线 + 挑战「?」）；演示态 `readOnly` 忽略点击。**原 `drawCellBackgrounds` 选中 `when{}` 零改动**（独立分支，护阶段 9 高亮）。
  - `ui/component/DemoPlayer.kt`（新）：字幕（技巧名/旁白/步数）+ 控制行（◀▶⟲✕ + 🔇/🔊）+「✋我来填」+ 挑战 1-9 行 + 即时反馈；TTS 绑 `DisposableEffect`（创建/`onDispose` shutdown）+ `ON_STOP` stop，中文不可用则隐藏声音键。
  - `ui/component/GameTopBar.kt`：加可选 `onDemo` → 「▶演示」`PlayArrow` 图标（仅未完成且非演示态显示）。
  - `ui/theme/Color.kt`：加演示三阶段配色（look/eliminate/conclude + strike，亮/暗各一套）。
  - `viewmodel/GameViewModel.kt`：加 `_demo`/`_demoChallenge`/`_telemetry` 三个 StateFlow + startDemo（先查错→建轨迹→暂停计时）/demoNext/Prev/Replay/exitDemo/startChallenge/submitChallenge/dismissChallenge/clearDemo；`resumeTimer` 加 `_demo==null` 守卫（演示态不计时）；在 useHint/commitValue 记录埋点；newGame/continueGame/exitGame 清演示 + 重置埋点。
  - `ui/screen/GameScreen.kt`：collect demo/demoChallenge；演示态走独立分支（派生只读 `buildDemoBoard` = 原局 + steps[0..currentIndex] 落子，挑战时 blank 该格）+ `DemoPlayer` 替代数字盘；顶栏挂 `onDemo`。**演示从不写 `_state`** → 退出不污染。
- 验证：`:app:testDebugUnitTest` 47 测试 0 失败（新增 12）；`:app:assembleDebug` 出 APK（10.6MB debug，release 有 R8 收缩不计此预算）。主源 + Compose UI 全编译过。**设备级手动点击未做**（环境无模拟器）。
- 未做/留待：版本号未升（10.2 是阶段中途，待阶段 10 上 AI 或用户决定单独发离线版再升）；区域停留埋点、棋盘字面"放大"动画、gotoCell 接棋盘点击（留 10.4 AI）。
- 创建/修改的文件：
  - 新增：`model/DemoController.kt`、`model/SessionTelemetry.kt`、`model/DemoChallenge.kt`、`ui/component/DemoPlayer.kt`、`test/.../DemoControllerTest.kt`、`test/.../SessionTelemetryTest.kt`
  - 修改：`ui/component/SudokuBoard.kt`、`ui/component/GameTopBar.kt`、`ui/theme/Color.kt`、`viewmodel/GameViewModel.kt`、`ui/screen/GameScreen.kt`

## 会话：2026-03-30

### 阶段 1：需求确认与架构设计
- **状态：** in_progress
- **开始时间：** 2026-03-30
- 执行的操作：
  - 创建项目规划文件（task_plan.md / findings.md / progress.md）
  - 初步梳理功能需求和技术方案
  - 提出关键问题待用户确认
- 创建/修改的文件：
  - task_plan.md
  - findings.md
  - progress.md

### 阶段 2：项目搭建
- **状态：** pending
- 执行的操作：
  -
- 创建/修改的文件：
  -

### 阶段 3：数独核心引擎
- **状态：** pending
- 执行的操作：
  -
- 创建/修改的文件：
  -

## 测试结果
| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
|      |      |         |         |      |

## 错误日志
| 时间戳 | 错误 | 尝试次数 | 解决方案 |
|--------|------|---------|---------|
|        |      | 1       |         |

## 五问重启检查
| 问题 | 答案 |
|------|------|
| 我在哪里？ | 阶段 1-9 已完成并发布；阶段 10 已规划+评审加固；**10.1 演示引擎 + 10.2 演示播放器 UI 已实现并测试通过（47 单测全绿 + assembleDebug 出 APK；未提交、设备级手动点击待做）** |
| 我要去哪里？ | **下一步：10.3 多 Provider 设置后台**——`providers[]`(id/name/baseURL/apiKey/models)+active 存 DataStore；主页加设置入口(齿轮) + NavHost 加 settings/provider_edit 路由；Provider 列表/编辑页(Key 遮罩/模型清单三来源/测试连接)。之后 10.4 DeepSeek+语音 → 10.5 复盘(P1) |
| 目标是什么？ | 给已发布的数独 App 加"AI 语音教练"：棋盘分步高亮 + 旁白走查解题；确定性骨架离线可跑，DeepSeek 作第二控制器。范围 = 完整版多 provider + 本期上语音 |
| 我学到了什么？ | 见 findings.md 顶部五条（均 2026-06-02）：「10.2 演示播放器落地」、「10.1 演示引擎落地」(共享内核+链式断点已修)、「设计评审·第三会话」、「设计决策·第二会话」、「教学式提示系统」 |
| 我做了什么？ | 三轮规划会话后连做两块实现：**10.1**（`stepForward` 共享内核 + `DemoStep`/`demoTrajectory` 修复链式断点）+ **10.2**（`DemoController` 纯状态机 + 棋盘分阶段高亮叠加层 + `DemoPlayer` 字幕/控制/默认静音 TTS +「轮到你」复现 + `SessionTelemetry` 埋点起步）；两个评审 P0 落地，生成器/阶段 9 高亮零回归 |

---
*每个阶段完成后或遇到错误时更新此文件*
