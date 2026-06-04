# 进度日志

## 会话：2026-06-04

### 阶段 11 增量 1+2：游戏设置页 + 暖色主题
- **状态：** complete（已提交，未推送）
- 续接：上一轮用户"先停，我要调整"，本轮"按你的建议推进"→ 先提交增量 1 数据层基线，再接线 UI。
- **增量 1 提交 `221814f`** feat(settings)：`GameSettings` + `ThemeChoice`/`ErrorCheckMode` 枚举（容错 `fromName`）+ `GameSettingsRepository`（DataStore "game_settings"）+ 4 测试 + `roadmap.md`。纯新增数据层、零行为改动。（提交标题首次混入 `@` 字符，已 `--amend` 修正）
- **增量 2** feat(theme)：
  - `Color.kt` 加暖色护眼调色板（`#FAF3E0` 米黄底 + 暖橙 `#B5651D` accent）。
  - `Theme.kt` 的 `SudokuTheme` 由 `darkTheme: Boolean` 改为 `theme: ThemeChoice`；**显式选择（LIGHT/DARK/WARM）关闭 dynamicColor**，仅 SYSTEM 走 Material You——否则选了暗色却被动态取色覆盖。
  - `GameScreen` 棋盘明暗原直接 `isSystemInDarkTheme()`，会无视显式选择；改为 `colorScheme.background.luminance() < 0.5f` 推断，一行覆盖显式/动态/暖色全部情况。
  - 新 `GameSettingsViewModel`（镜像 `SettingsViewModel`）+ 新 `GameSettingsScreen`（主题单选 + "AI 教练设置"跳转行）。
  - `MainActivity`：`GameSettingsViewModel` 提到 `SudokuTheme` 之上驱动主题；齿轮 `settings` → `game_settings`；原 AI 设置路由降级为 `ai_settings` 子项。
  - 设置页只放"已生效"的控件（主题 + AI 入口）；音效/查错/计时器等留到对应增量随行为一起加，避免死控件。
- **verify：** 79 单测全绿（主题为纯 UI 接线，可测逻辑已在增量 1 覆盖）+ APK 构建通过。
- **下一步：** 增量 3（错误检查模式：`GameViewModel.updateErrors` 按 `ErrorCheckMode` 条件化 + 手动「检查」入口）。

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

### 阶段 10.4：DeepSeek 接入 + 语音（增强层 · 一气做）
- **状态：** complete（设备级未测）
- 背景：阶段 10 最重的一块——给离线演示叠上 AI"第二控制器"：玩家用文字/语音问"下一步/为什么这格"，由 LLM 经 function calling 驱动同一个 `DemoController`，并接通 10.3 缓下的测试连接/`/models`。用户选"整个 10.4 一气做"。
- 三处简化（个人自用、降风险，已写进 task_plan/findings）：① 非流式（整段返回，SSE 留后续）；② 限流只做会话内超时/错误降级，每日上限留后续；③ JSON 用 org.json（零新依赖）。
- 执行的操作（8 步增量，每步编译/单测兜）：
  - manifest 加 `INTERNET` + `RECORD_AUDIO`。
  - `ai/ChatMessage.kt`：chat 消息/工具/结果模型 + `AiException`。
  - `ai/AiClient.kt`：`HttpURLConnection` 非流式 OpenAI 兼容客户端（`/chat/completions` + `/models`），org.json 编解码，推理模型省 `temperature`，友好错误映射，best-effort 取消。
  - 接通编辑页「测试连接 / 拉取模型」：`SettingsViewModel.fetchModels`（测试与刷新共用），编辑页加测试按钮 + 结果 + 拉到的模型并入建议 chips（第三来源）。
  - `ai/AiCoach.kt`：function-calling 第二控制器；system prompt 含盘面 givens + 逐步轨迹 + 当前位置 + 铁律；工具分发抽纯函数 `applyCoachTool`（1-based↔0-based，**单测**）；导航后回灌 tool 结果让模型讲解（≤4 轮，兜底补 assistant）。
  - `ai/VoiceInput.kt`：`SpeechRecognizer` 中文封装（create/start/stop/destroy 分离）。
  - `GameViewModel`：`activeProvider`/`aiBusy`/`coachReply` 三 StateFlow + `askCoach`（**跑在独立 `aiScope`、非 viewModelScope**）+ `stopAiWork`；演示导航/退出清 `coachReply`。
  - `DemoPlayer`：AI 交互区（回复 + 文字框 + 发送 + 🎙️ + 思考中 + 错误）；`VoiceInput` 绑 `onDispose`/`ON_STOP`；`RECORD_AUDIO` 运行时申请；barge-in（点麦停 TTS）；TTS 优先念 AI 回复。`GameScreen`：收集新态 + `ON_STOP→stopAiWork` + 传参。`SessionTelemetry.recordAiUsed`。
- 自查抓到并修的两个缺陷：① `stopAiWork` 取消时 `askCoach` 的 `CancellationException` 被通用 catch 吞 → 重抛；② `AiCoach.respond` `MAX_ROUNDS` 耗尽时历史以 tool 结尾（OpenAI 拒）→ 补 assistant 兜底。
- **[评审P0]资源生命周期**：网络绑 `aiScope`（非 viewModelScope！）`ON_STOP` cancel、STT `onDispose` destroy + `ON_STOP` stop、TTS 已绑——退后台全停。
- 验证：编译全工程 + `assembleDebug` 出 APK；66 单测全过（+11 `AiCoachToolTest`）。⚠️ 网络/STT/TTS/权限是设备运行时行为，无模拟器只能编译+逻辑自洽，真机可用待跑。
- 创建/修改的文件：
  - 新增：`ai/ChatMessage.kt`、`ai/AiClient.kt`、`ai/AiCoach.kt`、`ai/VoiceInput.kt`、`test/.../ai/AiCoachToolTest.kt`
  - 修改：`AndroidManifest.xml`、`viewmodel/SettingsViewModel.kt`、`ui/screen/ProviderEditScreen.kt`、`MainActivity.kt`、`viewmodel/GameViewModel.kt`、`ui/component/DemoPlayer.kt`、`ui/screen/GameScreen.kt`、`model/SessionTelemetry.kt`
- 下一步：10.5 复盘 + 飞轮（P1，本期可不做）；或先设备级跑通 10.2/10.3/10.4。

### 阶段 10 收口：AI 层静态复审 + 设备冒烟清单 + 基线复验
- **状态：** complete（复审 + 文档；未改任何源码）
- 背景：`/clear` 后续接，用户裁定方向="收口未测风险"——阶段 10 代码全绿全提交，但 10.2/10.3/10.4 整块 AI 教练**真机从未跑过**（只编译+单测+逻辑自洽），且 10.4 含网络/STT/TTS/权限等纯设备行为。本环境无模拟器，我能做且不需设备的两件事：① 静态正确性复审；② 产出设备冒烟清单。
- **基线复验**（亲验 HEAD 而非信文档）：`testDebugUnitTest --rerun-tasks` 强制重跑 → **66 用例 0 失败 0 错误**（9 个套件）；`assembleDebug` 出 **10.22MB** debug APK。文档声称的绿属实。
- **静态复审结论**：读了 `AiClient`/`AiCoach`/`ChatMessage`/`VoiceInput`/`GameViewModel`(askCoach/aiScope/stopAiWork)/`DemoPlayer`/`GameScreen`/`SettingsViewModel`/`ProviderEditScreen`/`DemoController`。**无"编译过但真机崩"类 bug**（空安全、下标、越界坐标 no-op、TOOLS JSON 配平、OpenAI 协议三不变量、CancellationException 重抛、MAX_ROUNDS 兜底全在）。5 项较低级遗留（详见 findings 同日"收口"条，按严重度）：
  - **S1**🟠安全：baseUrl 填 `http://` → BYOK key 明文上链路（`AiClient.kt:51,60-65` 不校验 scheme）。**唯一带安全维度，建议现在修（~3 行）**。
  - **S2**🟡陷阱：存"只名称+URL 无模型"的 provider → AI 框出现但每次发送 `throw 未选择模型`（`ProviderEditScreen.kt:86` canSave 不要 model ↔ `AiClient.kt:26`）。
  - **S3**🟡竞态：AI 思考中翻页按钮未禁用，手动翻被 stale 快照覆盖（`DemoPlayer.kt:247-249`，同主线程→不崩、丢交互）。
  - **S4**🟢健壮：畸形 200 抛原始 JSONException（`parseChat` 在 `request()` try 外），上游接住不崩、文案丑。
  - **S5**🟢隐私：`allowBackup=true` 把明文 key 带进云备份（评审 P2 遗留）。
  - 反向确认正确的高风险点：1-based↔0-based 接缝、资源生命周期 P0（aiScope 非 viewModelScope、STT/TTS 绑 onStop/dispose、barge-in）、网络在 `Dispatchers.IO`、"轮到你"藏结论（`SudokuBoard.kt:108` 跳过挑战格绿填充）。
- **设备冒烟清单**（只有真机能确认的，按 A 离线演示 / B 设置 / C AI 网络+语音 / D 回归 四组，含失败回退矩阵与"退后台麦克风熄灭"P0 验证）已在本会话输出给用户；要点：演示三色高亮+删除线渲染、TTS 中文可用性回退、测试连接 401/断网映射、语音转写+barge-in、退后台 cancel。
- 未改源码（用户选的是"复审+清单"，非"加固"）。S1–S3 是否顺手修留待用户拍板。
- 创建/修改的文件：仅文档（`task_plan.md`/`progress.md`/`findings.md`）。

### 阶段 10 收口续：修 S1–S3（用户拍板"打包修掉"）
- **状态：** complete（75 单测全绿 + APK）
- 三处修复，均沿用既有"抽纯函数单测、IO/org.json 接缝不测"范式：
  - **S1 https 强制**：`AiClient` 抽顶层纯函数 `isSecureUrl(url)`（trim + `startsWith("https://", ic)`）→ `request()` 入口设防（**单一 chokepoint，同时覆盖 chat 与 listModels**，给任意非 https URL 抛 `AiException("仅支持 https…")`）；编辑页 `ProviderEditScreen` 接同一函数：`canSave`/`canTest` 加 `!insecureBaseUrl` 闸、baseURL 字段 `isError` 红框 + 文案"必须 https://"。**决定 https-only（无 localhost 碰巧豁免）**——预置都是远端 https，YAGNI；本地 http LLM 是另需再说。
  - **S2 无模型 provider 门控**：`AiProvider` 加 `val isUsable get() = activeModel != null || models.isNotEmpty()`（**职责落模型层**）；`GameScreen` 的 `aiAvailable = activeProvider?.isUsable == true`——存了"只名称+URL"的 provider 不再假装可用（自然门控，合"未配置=无AI"基因）。未动 `canSave`（不过度约束编辑页，允许先存半成品回头补）。
  - **S3 翻页竞态**：`DemoPlayer` 的 ◀▶⟲ 加 `&& !aiBusy`、"✋我来填"加 `enabled = !aiBusy`——AI 思考中禁止改 demo 态，杜绝"手动翻被 stale 快照覆盖"。
- 新增单测：`ai/AiClientTest`（5：https/大小写/去空白/http拒/其他scheme拒）、`model/AiProviderTest`（4：isUsable 四组合）。
- 验证：`testDebugUnitTest assembleDebug` 实际执行（非缓存）→ **75 用例 0 失败 0 错误**（11 套件，66→75）；APK 10.28MB。
- 未做：S4（畸形 200 包 AiException）/S5（allowBackup 排除 key）按用户意留作可选；**真机冒烟仍是唯一未闭环面**（S1–S3 改的是编辑页/网络闸/UI 禁用态，真机点按仍待验）。
- 创建/修改的文件：
  - 新增：`test/.../ai/AiClientTest.kt`、`test/.../model/AiProviderTest.kt`
  - 修改：`ai/AiClient.kt`、`model/AiProvider.kt`、`ui/screen/GameScreen.kt`、`ui/screen/ProviderEditScreen.kt`、`ui/component/DemoPlayer.kt`
- 下一步：真机跑冒烟清单 / 10.5 复盘飞轮（P1）/ 发版 v1.10.0（建议真机后）。

### roadmap 落盘 + 阶段 11（游戏设置页）增量 1
- **状态：** 增量 1 complete（基建层；增量 2-4 待做）
- 背景：用户问"还有未开发功能吗"→ 我用代码核实（grep 0 匹配 + `SettingsScreen` 仅管 Provider）列出"规划过未实现"的，给看法 → 用户要"非低价值项"的实现计划 → 我读 `SudokuGenerator`/`Validator`/`GameState`/`Theme`/`SessionTelemetry`/`StatsRepository`/`Difficulty`/`MainActivity` 导航做地基核实 → 出 5 功能计划（①设置页 ②复盘 ③技巧训练 ④冲刺 ⑤变体）+ 两条复用接缝（A 技巧定向出题 / B 埋点持久化）→ 用户"要"→ 写 `roadmap.md` + 开工 ①。
- **`roadmap.md`（新）**：5 功能 backlog + 接缝 A/B + 推荐序；声明不替代 `task_plan.md`、已排除低价值项（成就/段位/打卡）。
- **阶段 11 增量 1（基建，零 UX 决策、可回退）**：
  - `model/GameSettings.kt`：不可变 holder（theme/errorCheck/soundEnabled/autoRemoveNotes/showTimer）+ `ThemeChoice{系统/亮/暗/暖}`、`ErrorCheckMode{即时/手动/不检查}` 两枚举，各带中文 `label`（照 `Difficulty` 范式）+ 容错 `fromName`（未知/null 降级默认，应对持久化损坏值）。
  - `data/GameSettingsRepository.kt`：DataStore "game_settings"（名唯一，已 grep 确认不撞 `game_stats`/`ai_settings`）；`Context.gameSettingsDataStore` 文件私有扩展（不撞 `StatsRepository` 的同名私有扩展）；枚举经 `fromName` 解码降级——照 `StatsRepository`/"损坏降级"范式。
  - `test/.../model/GameSettingsTest.kt`：4 测试（默认值 + 两枚举 `fromName` 往返/降级）——沿用"抽纯逻辑单测、DataStore 接缝不测"范式。
- 验证：`testDebugUnitTest assembleDebug` 实际执行 → **79 用例 0 失败**（75→79，+4）、12 套件、APK 10.32MB。无行为改动（纯新增数据层）。
- 创建/修改：新增 `roadmap.md`、`model/GameSettings.kt`、`data/GameSettingsRepository.kt`、`test/.../GameSettingsTest.kt`；改 `task_plan.md`（加阶段 11 + 增量拆解）/`progress.md`/`roadmap.md`。**未提交**。
- 下一步：增量 2（暖色主题）——开工前需用户定 ① 暖色具体色值 ② 错误检查默认模式。

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
| 我在哪里？ | 阶段 1-9 已发布；**阶段 10（AI 语音教练）10.1–10.4 全部实现并提交**（HEAD `d9591ea` + 工作区有 S1–S3 修复**未提交**）。**10.4 已静态复审（无崩溃类 bug）+ 冒烟清单已出 + 修复 S1–S3（https 强制/无模型门控/翻页竞态）+ 新增 2 测试，75 单测全绿 + 10.28MB APK**。S4/S5 可选未做。版本号仍停 `1.9.0/versionCode 6`（阶段 10 未升版未发版） |
| 我要去哪里？ | 三选一：**① 提交 S1–S3 修复**（工作区待提交，用户未发提交指令前不提）；**② 真机跑冒烟清单**（网络/STT/TTS/权限/渲染，唯一未闭环面，需设备+key）；**③ 10.5 复盘+飞轮**（P1，埋点已采集、本期可不做）。之后才考虑发版 v1.10.0 |
| 目标是什么？ | 给已发布数独 App 加"AI 语音教练"：棋盘分步高亮 + 旁白走查；确定性骨架离线可跑，DeepSeek 作第二控制器经 function-calling 驱动同一 `DemoController`。范围 = 完整版多 provider + 本期上语音 |
| 我学到了什么？ | 见 findings.md 顶部条目：「阶段 10 收口·静态复审」(2026-06-03，S1–S5 遗留 + 基线复验)、「10.4 DeepSeek+语音落地」(aiScope 非 viewModelScope/取消重抛/tool 不结尾/1-based 接缝)、「10.3 多 provider 后台」、「10.2 演示播放器」、「10.1 共享内核+链式断点」 |
| 我做了什么？ | 本会话：`/clear` 续接 → 恢复任务态 → 用户选"收口未测风险" → AI 层静态复审（10 文件，无崩溃 bug，列 S1–S5）+ 基线复验 + 四组冒烟清单 → 用户选"S1–S3 打包修掉" → 修 S1（isSecureUrl 闸）/S2（isUsable 门控）/S3（!aiBusy 守卫）+ 2 新测试，75 单测全绿 + 10.28MB APK。S1–S3 修复在工作区**未提交** |

---
*每个阶段完成后或遇到错误时更新此文件*
