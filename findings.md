# 发现与决策

## 需求（已确认）
- 核心玩法：9×9 标准数独
- 纯离线单机，无广告/内购
- Kotlin + Jetpack Compose，API 26+

## 技术发现（2026-06-03）：10.3 多 Provider 设置后台落地（离线）+ 两个范围裁定

**范围裁定（用户：本 App 个人自用）**：① **砍掉 10.4「首次启用全屏知情同意」P0**——单用户 BYOK、自己即开发者，两级全屏同意屏属过度设计；AI 改由"未配置 provider = 无 AI"自然门控（仍默认关）。② 连带「测试连接 / `/models` 刷新 / 推理模型请求容错」因都需出站网络（且原与同意闸门时序耦合：测试连接是**第一个出站调用**，本须先过同意）**下移 10.4** 与 HTTP 层同落。10.3 守住"无 `INTERNET` 权限"的纯离线基因。**保留** 10.4「资源绑 onStop」P0（非隐私，是后台录音/泄漏问题）。

**持久化用 `org.json`、非 kotlinx.serialization**：phase 6 `GameRepository` 既用 `org.json` 手写存档 → `ProviderRepository` 照搬（单 JSON 串塞一个 Preferences key），**零新依赖**。⚠️ 关键约束：`org.json` 是 Android framework 类，**JVM 单测里被 stub（"not mocked"）不可用** → `GameRepository` 的 JSON 从来没单测。对策（沿用 `SessionTelemetry`/`DemoController` 范式）：**模型层保持 Android-free + 不可变纯转换**（`AiSettings.upsert/remove/setActive/setModel`），纯转换**单测**（`AiSettingsTest` 8 测试）；JSON encode/decode 留在 repo（Context 绑定、不进 JVM 单测，损坏降级空设置）。10.4 网络 DTO 再决定 org.json vs 引 kotlinx.serialization。

**结构变化：出现第二个 ViewModel**。此前 App 仅 `GameViewModel` 一只。`SettingsViewModel`（`AndroidViewModel` + `viewModel()`，与 `GameViewModel` 同范式）在 `MainActivity` NavHost 层与 game VM 并列；`settings`/`provider_edit` 两路由共享它。provider_edit 用**可选 navArgument** `provider_edit?providerId={id}`（nullable，null=新增）；编辑页从 `settings.value.providers.firstOrNull{id}` 取既有项。冷启动直达编辑的"瞬时 null"边角已接受（永远从已加载的列表进入）。

**图标只用 material-icons-core**：Add/Settings/ArrowBack/Delete 均在 core，未引 `material-icons-extended`（守依赖精简，10.4 同此）。Key 显隐用文字按钮（"显示/隐藏"）而非 Visibility 图标（后者在 extended）。

**模型清单当前两来源（预置 + 手动）**：编辑页 `PRESETS`（DeepSeek/OpenAI）给"快填 provider"AssistChip + 按 baseUrl 关键词匹配的建议 FilterChip；手动直接编辑"模型 ID"字段。第三来源「🔄 `/models` 拉取」随 10.4 网络层补齐 → 三来源合一。

**验证**：55 单测全过（+8）；`assembleDebug` 出 APK（主源 + Compose 全编译过）。设备级点击（增删改/切换/快填/Key 显隐/空态）待做（无模拟器）。

## 技术发现（2026-06-02）：10.2 演示播放器落地（离线·按钮）

**形态**：棋盘在上为主角、下方"演示面板"（字幕 + 播放控制），数字盘在演示态隐藏让位——不是覆盖式叠层，符合第二会话"棋盘被画着、下方一条字幕"的定调。入口 = 顶栏「▶演示」图标（用户选）。

**关键设计决策（实现期定，含与 verify 的张力调和）**：
1. **演示棋盘 = 派生只读显示态，绝不写 `_state`**（这是"退出不污染"的根）。`GameScreen.buildDemoBoard(state, controller, blankCell)` = 原局 + `steps[0..currentIndex]` **inclusive** 落子；`remember(state, demo, challenge)` 缓存。选 inclusive 而非 exclusive 是为对齐 verify"连续下一步**到终局**"——末步是落子（`demoTrajectory` 循环到 `isSolved` 才停，最后一帧必填满），inclusive 下末 index 即解开的整盘。演示从不碰 `GameState`，退出 `_demo=null` 即恢复真盘。
2. **棋盘高亮走独立分支，不并入选中 `when{}`**（护阶段 9）。`SudokuBoard` 加 `demoHighlight: DemoHighlight?`：非空时走 `drawDemoBackgrounds`（看=琥珀/排除=橙/结论=绿+描边）+ `drawDemoMarks`（被排除候选数**带删除线**——`eliminatedCells` 那一帧的可视化 + 挑战「?」），**原 `drawCellBackgrounds` 一字未动**。演示态 `rememberUpdatedState(demoHighlight!=null)` 令点击 `return@detectTapGestures`（只读）。
3. **「轮到你」做成可选·按需，而非强制每段一挑**（用户选；调和 P0 与 verify）。P0 要"主动复现"，但 verify 要"连续点下一步到终局、步数=轨迹长度"——若每步强制挑战就打断纯走查、步数对不上。落点：落子步上「✋我来填」按钮，点了才藏结论、棋盘显「?」、字幕给 1-9；对→夸、错→即时讲技巧+揭晓（复用 `DemoStep.placement` 当标准答案）。挑战是**叠加项**，不拦纯走查 → 两个判据都满足。
4. **三块新态全部隔离在 VM、沿用 `_activeHint` 先例**：`_demo: StateFlow<DemoController?>`、`_demoChallenge`、`_telemetry`——都不进 `GameState` 序列化。`DemoController` 不可变纯状态机（next/prev/replay/jumpTo/gotoCell 返回新实例，已单测）。`DemoChallenge` 放 **model 层**（不放 viewmodel）以免 `ui.component.DemoPlayer` 反向依赖 viewmodel。
5. **TTS 默认静音 + 生命周期绑定**（用户选默认静音）：`DemoPlayer` 内 `DisposableEffect` 创建 `TextToSpeech`、`onDispose` `stop()+shutdown()`，并监听 `ON_STOP` `stop()`；中文引擎不可用（`setLanguage<LANG_AVAILABLE`）则 `ttsReady=false`、隐藏声音键回退字幕。这是 10.4「资源绑 onStop」P0 的轻量前身——⚠️ 10.4 接麦克风/网络时要把这套升级到 Activity 级、更严。构造期 `OnInitListener` 回调早于 `ttsRef` 赋值的竞态，用捕获的 `var engine` 闭包绕开。
6. **计时器演示态暂停**：`startDemo` 取消 `timerJob`；`resumeTimer` 加 `_demo==null` 守卫（退后台再回前台也不会在演示中重启计时）；`exitDemo` 才恢复。演示是学习视图、不算解题用时。

**会话埋点（P0「起步」）**：`SessionTelemetry`（不可变）记 求助次数/错误/演示次数/复现对错/**卡壳技巧**（`techniqueHelpCounts`，弱项画像核心）；在 `useHint`/`commitValue`/`submitChallenge` 落点记录。**仅本地、不进 `GameState`、不外发**。范围取舍：**仅内存、不持久化**（"沿用 `_activeHint` 隔离先例"字面=内存隔离；持久化 + 复盘 UI 是 10.5）；**区域停留**埋点需计时基建，留作后续细化。"数据断流"靠"采集代码路径现在就活"来防，而非现在就落库。

**验证**：47 单测 0 失败（新增 `DemoControllerTest` 7 + `SessionTelemetryTest` 5）；`assembleDebug` 出 APK（主源 + Compose 全编译过）。逻辑判据见 task_plan 10.2 verify。**设备级手动点击待做**（环境无模拟器）——这是本阶段唯一未闭环项。

**适用范围/后续**：`DemoController.gotoCell` 已实现+单测但 10.2 不接棋盘点击（板只读），留给 10.4 AI「解释这一格」。`DemoPlayer` 的控制函数组（next/prev/replay/jumpTo/gotoCell）即 10.4 DeepSeek function-calling 的目标函数集——第二控制器叠加时不改这组签名。APK debug 10.6MB 略超 P2 的 <10MB，但 release 有 R8 收缩，不计此预算。

## 技术发现（2026-06-02）：10.1 演示引擎落地 —— 三方检测统一到共享内核 + 链式断点已修

**现象/动机**：评审 P0-1 要求 `analyze`/`findHint`/演示轨迹**共用一个检测内核**。落地前 `LogicSolver` 已有**两套并行**的技巧实现——① 6 个"检测即变更"的突变检测器（`nakedSingle`…`xWing`，返回 Boolean，供 `analyze`/`analyzeWithCap`/生成器）；② 6 个只读 `*Hint` 检测器（返回 `Hint`，供 `findHint`）。若演示再加第三套，必然"检测漂移→演示讲错步骤"。

**改动**：抽出私有共享内核 `stepForward(board, cands): DemoStep?`（**只检测、不变更**，按技巧优先级链 6 个检测器）+ `applyStep(board, cands, step)`（**统一推进**：有 `placement` 落子，否则按 `eliminatedCells` 擦候选）。六个检测器统一改为只检测、返回 `DemoStep?`。四个出口全部走这一个内核：`analyze`/`analyzeWithCap`（循环 `stepForward`+`applyStep`，签名/语义不变）、`findHint`（`stepForward(...)?.toHint()`）、新增 `demoTrajectory`（循环收集 `DemoStep`）。**删除**旧 6 突变检测器 + `applyNextTechnique` + 6 个 `*Hint`（两套重复合一；`LogicSolver.kt` 661→487 行、净 −174，已含新增的 `eliminatedCells` 收集与 `stepForward`/`applyStep`/`demoTrajectory`）。

**为何生成器热路径逐字不变（正确性论证，非测试侥幸）**：
- 新旧检测器**迭代顺序逐一对齐**（naked→hidden→pointing/claiming→naked subset→hidden subset→X-Wing；每个内部的 r/c/v/unit 嵌套顺序与旧突变版一致），故"首个命中"相同。
- 消除类技巧旧版"`changed` 才返回"⟺ 新版"`eliminatedCells` 非空才 emit"——同一条件；故 `stepForward` 返 null ⟺ `applyNextTechnique` 返 −1。
- `applyStep` 对一步的变更与旧突变函数**逐位等价**（已验证 naked subset 的 `x and (x and union).inv() == x and union.inv()`；hidden pair 的"只留 b1,b2"==擦 `mask.inv()`；pointing/claiming/X-Wing 擦的格集合相同）。故 cands 网格逐步同构、`maxLevel` 与 `solved` 不变。
- `analyzeWithCap` 早退：新版"检测→超 cap 则不 apply 直接 false"，旧版"apply 后超 cap 直接 false 丢弃副本"——返回值（含 `maxLevel` 不含超 cap 级）相同。

**`eliminatedCells` 收集口径（演示最关键帧）**：`List<Elimination(row,col,values)>`。pointing/claiming/X-Wing → 被擦格 + 单值 `[v]`；naked 数对/三数组 → 同单元其他格被擦的"与 union 交集"值；hidden 数对 → 两枚模式格自身被擦的"非 {v1,v2}"值（看/排除位置重叠，符合"这两格只能是 v1/v2，划掉它们的其他候选"）。

**链式断点已修**：`demoTrajectory` 携带候选状态前进，"先消除→解锁 single"的链条现在走得通（旧 `findHint` 无状态、每次从盘面值重算基础候选，只能反复给同一个消除）。**验证即铁证**：HARD/EXPERT 谜题必须靠消除解锁 single，单测里 `demoTrajectory` 对全部 5 难度都走到合法终局 → 证明状态确实在前进。`findHint` 仍无状态（只给"当前盘面下一步"），但其首步 == 轨迹首步（同一 `stepForward` 同一初值），单测已断言。

**验证**：`LogicSolverTest` 14 测试 + 全工程 34 测试 0 失败；4 个 `SudokuGeneratorTest` 通过 = 热路径不变的活证；`--rerun-tasks` 随机重跑 3 次全绿。

**适用范围/后续**：`DemoStep`/`demoTrajectory` 是 10.2 `DemoController` 的数据源。当前仍是**单条线性轨迹**（无分支/试探，覆盖生成器上限 X-Wing 足矣）。"按格解释"（10.4 `explainTechnique`）需在此之上加"按格分析"；自动标记候选可复用 `eliminatedCells`。

## 设计评审（2026-06-02 · 第三会话）：阶段 10 · 4-agent 评审结论

用 4 个并行 agent（架构可行性 / 范围简单性 / 风险横切 / 产品教学）对 task_plan 阶段 10 做只读评审。**共识**：架构地基（确定性骨架 + 单状态机双控制器 + grounding）四视角一致认可、值得做；四个视角各抓到一个本领域 P0，几乎不重叠（说明多视角覆盖到位）。

**用户裁决**：范围**维持原决策不变**——保留**完整版多 provider** + **本期上语音**（即拒绝评审建议的"Lite 折中"与"语音推 P1"）；**5 个 P0 全收**。

**已并入 task_plan 阶段 10 的 5 个 P0**：
1. **引擎落点（架构）**：不是"把 `findHint` 改带状态"，而是抽 `analyze`/`findHint`/轨迹引擎**三方共享的"前进一步"内核**（`analyze` 本就带状态；公开签名/生成器不动）；`DemoStep` 必须含**被排除的格/候选** `eliminatedCells`（现 `Hint` 只返回模式格、不够，检测器内部 `any{}` 的消除目标要收集成 list）。否则工作量被低估 +「第三套技巧检测漂移→演示讲错步骤」。
2. **生命周期（风险，已核实代码）**：现计时器 `viewModelScope`+`onCleared` **退后台不取消**；AI 的网络/录音/TTS 必须绑 `onStop`/`onDestroy` 显式 release，否则**后台仍录音**（隐私+政策灾难）+ 原生资源泄漏 + ANR。原表述"复用计时器模式"是误导，已改写。
3. **复现校验（产品）**：阶段 10 是单向播放，比现有卡片还退步（卡片至少逼点一次「填入」）→ 会变"更高级地喂答案"。加"轮到你"：演示完留一步玩家自填，复用 `findHint`+落子校验。
4. **埋点留 P0（产品）**：复盘 UI 可 P1，但**会话埋点必须 P0 期就开始**（否则早期"弱项画像"数据断流）；仅本地、不随 AI 请求外发。
5. **知情同意（风险）**：默认关 + 首次全屏知情页 + 两级授权（盘面文本 / 麦克风音频）+ 点明 STT 音频经 Google。

**评审提出、本期暂不并入计划的 P1/P2 待办（实现期再消化，记此防丢）**：
- [P1] 可配 baseURL = SSRF/key 外泄面 → 强制 https、scheme 校验、"key 发给谁"提示文案。
- [P1] 成本闸门：本地前置节流（最小间隔 + 当日计数，达上限不发请求）；token 主成本是重复 resend 盘面 → 考虑只发增量/步号。
- [P1] DeepSeek tool 调用兜底：入参在 `DemoController` 侧硬校验/夹取（坐标/index 边界）；无 tool_call 时降级。grounding 不止于 prompt，执行边界再设一道闸。
- [P1] 失败态回退矩阵：{未配 key / 断网 / 超时 / 401·余额 / STT 不可用 / TTS 不可用} × 降级动作，每格落到"按钮版 100% 可用"。
- [P1] 会话状态机（Idle→Listening→Thinking→Speaking→Interrupted）：barge-in 立即 `tts.stop()` + 作废在途响应；三档超时（STT 静默 / 首 token / 整请求）。
- [P1] 权限可见性政策面：manifest 加 `INTERNET`/`RECORD_AUDIO` 破坏"纯离线"对外信号 → 商店话术/隐私页；评估 AI 作可选 feature 模块隔离权限。
- [P2] 中文 STT/TTS 引擎缺失探测（`isRecognitionAvailable`/`isLanguageAvailable`），不可用则灰掉语音入口、回退按钮+字幕。
- [P2] APK<10MB 硬预算：HttpURLConnection/OkHttp 二选一 + kotlinx-serialization，避开 Retrofit 全家桶；CI 量 size diff。
- [P2] `allowBackup=true` 会把 key 带进云备份 → 给装 key 的 DataStore 配 `fullBackupContent` 排除。
- [P2] 演示叠层别遮挡棋盘（数字盘已压到 36dp）→ 演示态隐藏数字盘、字幕占位；高亮加非颜色冗余（描边/序号），利于无障碍/色盲。
- [P2] `DemoController` 并发：按钮（主线程）与 AI（异步回调）都改 currentIndex → 状态变更收敛到单一主线程 reducer。

**适用范围**：本条是阶段 10 的评审基线；P1/P2 待办在进入对应子阶段（尤其 10.3/10.4）时逐条消化。

## 设计决策（2026-06-02 · 第二会话）：AI 语音教练 = "演示 + 讲解"

**缘起**：用户问"项目能否结合 AI"。多轮讨论把方向收敛为：不做花哨外挂，而是把 findings 早期设想的"提示三级递进"里**第 3 级（动画演示完整推理过程）做成核心体验**——一个**棋盘上分步高亮 + 旁白讲解的"带旁白走查"**，正中差异化定位"真正学会数独"。

**贯穿铁律：AI 当"老师的嘴"，不当"解题的脑"。** LLM 解数独不可靠；演示的"真相"（看哪些格 / 排除哪些候选 / 结论）一律由确定性的 `LogicSolver` 产生，LLM 只负责把每步念得好听、回答追问。

**关键设计决策**：
1. **"演示+讲解"不是聊天 UI，而是叠在棋盘上的"演示播放器"**：棋盘是主角（被画着），下方一条字幕 + 播放控制。之前纠结的"局内聊天框放哪（卡片/抽屉/全屏/悬浮）"是错问题——分步 + 可回放 + 引擎驱动把那四种方案的毛病都治了。
2. **确定性骨架 + AI 增强分层**（决定可落地性）：
   - 骨架（离线就能跑）：`LogicSolver` 吐结构化**步骤轨迹** → `SudokuBoard` 分步动画 → Android TTS 念引擎模板旁白。
   - 增强（配了 key 才有）：DeepSeek 把旁白升级成对话式、可追问、按水平调整。
   - 红利：演示内核不依赖联网（守住离线基因）；grounding 天然成立（时间线引擎生成，LLM 编不出新步骤）；可分两步交付、骨架零返工。
3. **一个状态机，两个控制器**（回答用户"离线按钮 / 有 AI 由 AI 控制"的可行性）：`DemoController`（steps[] / currentIndex / next / prev / replay / jumpTo / gotoCell）是唯一真相。离线 = 按钮直接调；有 AI = DeepSeek **function calling** 把语音意图映射到**同一组函数**。
4. **AI 模式不取代按钮，而是叠加**（对用户原方案的修正）：纯"翻页"按钮永远赢——0ms vs 2-3s、免费 vs 烧 token、永不误触 vs STT/意图可能出错。AI 的价值在按钮表达不了的意图（"上一步那个换个说法" / "为什么第 5 列排除 7" / "跳到结论"）。保留按钮反而**少写代码**（两控制器本就指向同一组函数）。tool-calling 一旦接通，gotoCell / explainTechnique / jump 等同机制解锁整个"可对话教练"。
5. **演示范围用 step-based 统一**：原子单位 = 一个逻辑步骤；连续"下一步" = 走完整条技巧链；先选格再演示 = 讲透某格——一套设计涵盖三种诉求，无需三选一。
6. **多 provider 配置（完整版）**：因全是 OpenAI 兼容，多 provider 在**协议层几乎免费**，复杂度全在配置 UI/存储。数据模型 `providers[] + active`；可配 baseURL/apiKey/model（BYOK，明文 DataStore，守"无后端"且不堵代理路）；模型清单**混合**（预置 + 拉 `/models` + 手动兜底，因为别家 `/models` 不一定干净/实现）。

**牵出的真问题（实现前须知）**：
- **引擎要改**：现 `findHint` 是**无状态**的（每次从盘面值重算基础候选，不保留消除）——这正是本文件下方记录的"链式断点"。演示要"链式走查"，轨迹必须**携带候选状态前进**；做这件事顺手**修复链式断点**。
- 场景"解释某一格"需**按格分析**（现 `findHint` 只给全局第一步）。
- 场景"复盘"需**会话埋点**（现完全没记录）→ 列为 P1。

**验证方式/落点**：见 task_plan.md 阶段 10（10.1 演示引擎 / 10.2 播放器 / 10.3 多 provider 设置 / 10.4 DeepSeek+语音 / 10.5 复盘飞轮 P1）。本会话纯设计，未写代码。

**适用范围**：本条是"AI 语音教练"特性的设计基线；与下方"教学式提示系统（findHint）"是同一引擎血脉的延伸——findHint 的单步检测将被升级/复用为演示的步骤轨迹源。

## 技术发现（2026-06-02）：教学式提示系统

**现象/动机**：`LogicSolver`（人类技巧逐级求解：唯一余数→隐性唯一数→区块排除→数对/三数组→隐性数对→X-Wing）此前只在生成时给难度打标签（`analyze` 仅返回 `maxLevel`），其全部推理过程被丢弃；而对局中的提示（旧 `useHint`）只是从左上扫描第一个空格、直接抄 `solution` 答案，不教任何东西。这是"最值钱的引擎被用一次就扔"的不对称。

**改动**：新增只读 `LogicSolver.findHint(board): Hint?`，复用 `initCandidates`/`allUnits`，按与 `analyze` 相同的技巧优先级，返回**下一步**（技巧名 + 中文讲解 + 高亮格 + 可填落子）。提示流程改为：① 先查错（任何非给定格 `value != solution` 即点出，免费、不耗次数）；② 再求逻辑下一步并展示讲解卡片（消耗 1 次）；③「填入」是已揭示步骤的免费便捷操作。

**关键设计决策**：
- **不碰 `analyze`/`analyzeWithCap`/`SudokuGenerator`**——生成是 app 命根子，每题合法性都依赖它。新增独立只读路径，生成热路径零风险。代价是技巧检测逻辑有一定重复，换取隔离。
- **覆盖 1-6 级 = 提示永不落空**：生成器保证每题最高只需 X-Wing（专家 level 6），所以对任意"无错误"的局面，`findHint` 必能给出一步，不会出现"找不到提示"。
- **落子恒等唯一解**：逻辑唯一解谜题的强制推理结果必然等于 `solution`（单元测试已断言），比旧版直接读 `solution` 更可证明、且顺带把"填错就覆盖"改成"先提示纠错"。
- 提示状态放在 `GameViewModel`（`_activeHint` StateFlow），**不进 `GameState`**，避免触碰 `GameRepository` 的逐字段序列化。

**验证方式**：`LogicSolverTest` 新增——逐步 `findHint`+落子能把纯唯一数谜题解到合法终局；对 5 个难度的新生成谜题均返回非空提示；中等难度下落子恒等于 `state.solution`。`:app:testDebugUnitTest` 全绿。

**适用范围/后续**：当前为"先讲解、可选填入"两段式；findings.md 早期设想的三级递进提示、按技巧的专项训练、以及"按弱项技巧定向出题"的飞轮，均可在此 `findHint` 之上叠加。

### 技术发现（2026-06-02 复审补充）

**1. `findHint` 检测器的单测做法。** 四个高级检测器（`pointingClaimingHint`/`nakedSubsetHint`/`hiddenSubsetHint`/`xWingHint`）改为 `internal`（同模块测试可见，不进公开 API），测试里**直接手搓候选位图**喂给单个检测器来断言技巧名+高亮格。原因见第 2 条——无法通过"构造整盘 + 调 `findHint`"来稳定触发某个高级技巧。

**2. `findHint` 对候选消除是无状态的（设计取舍 + 限制）。** `findHint` 每次都用 `initCandidates` 从棋盘**值**重算"基础候选"，不保留任何区块排除/数对带来的候选消去。含义：
- 它只汇报"从当前盘面看，人类下一步该做的那一个动作"；优先返回 single，没有 single 时才返回消除类提示。
- **链式断点**：当真正的下一步是"先做消除 → 才出现 single"时，`findHint` 只能反复给出那个**消除**，永远看不到被它解锁的 single（因为消除不改变棋盘值，下次重算又回到基础候选）。对只靠"填入"的玩家，这类局面会卡住（消除类提示没有"填入"按钮）。
- 这也解释了为何高级技巧很难作为"整盘的首个可命中技巧"被构造出来用于测试——基础候选网格里通常先有 single/区块排除。
- 现状可接受（消除类提示本就是"教你看出这一步"）；若要消除此限制，可让 `findHint` 内部链式应用消除直到出现可填落子，或把消除结果写进玩家笔记（同时支撑"自动标记候选"的体验）。属未来增强，本次不做。

## 头脑风暴汇总（三视角分析：2026-03-30）

### 一、难度系统 — 基于解题技巧而非仅挖空数量

| 级别 | 挖空数 | 核心技巧要求 |
|------|--------|-------------|
| 入门 | 30-35 | 唯一余数（Naked Single）、隐性唯一数（Hidden Single） |
| 简单 | 36-42 | + 区块排除（Pointing/Claiming） |
| 中等 | 43-50 | + 显性数对/数组（Naked Pair/Triple） |
| 困难 | 50-55 | + 隐性数对（Hidden Pair）、X-Wing |
| 专家 | 55-62 | + Swordfish、XY-Wing、链式推理 |

**关键**：生成谜题后用求解器打标签，记录"解题所需最高技巧"作为难度分类依据。

### 二、游戏模式

| 模式 | 描述 | 优先级 |
|------|------|--------|
| **经典模式** | 5 个难度自选，标准数独 | P0 (MVP) |
| **每日挑战** | 每天 3 道题（简单/中等/困难），日历打卡 | P1 |
| **冲刺模式** | 连续 5 道简单题计总时间，追求心流 | P2 |
| **技巧训练** | 针对每种技巧的专项练习，交互式教学 | P1（差异化核心） |
| **变体数独** | 对角线、杀手数独、不规则宫（逐步解锁） | P2 |

### 三、核心交互设计

#### 棋盘与输入
- 棋盘占屏幕上方 ~60%，数字面板固定底部（1-9 横排，拇指可达）
- 选格 → 填数两步操作（点格子选中 → 点数字填入）
- 某数字已出现 9 次时，对应按钮自动变灰
- 宫分隔：粗线 + 极浅交替背景色辅助区分

#### 高亮系统
- 选中格子 → 高亮同行/同列/同宫（浅色底）
- 选中已填数字 → 棋盘上所有相同数字高亮
- 冲突数字标红

#### 笔记（候选数）
- 切换按钮明确，视觉状态清晰区分（面板变色）
- 笔记布局 3×3 固定位置（1-2-3/4-5-6/7-8-9），靠位置辨识
- **自动清除关联笔记**：填入数字后自动擦除同行/列/宫的候选数（可选开关）

#### 提示系统（三级递进）
- 第 1 级：高亮"下一步最容易突破的区域"
- 第 2 级：高亮具体格子 + 提示所用技巧名称
- 第 3 级：动画演示完整推理过程
- 每局限制 3 次提示

#### 撤销
- 无限撤销，支持重做
- 可选：步骤历史列表，跳回第 N 步

#### 错误检查（三种可选模式）
- 即时检查：填入即判对错（新手适用）
- 手动检查：点按钮才显示错误
- 不检查：纯自力更生（高手适用）

### 四、激励系统

#### 统计面板
- 各难度平均用时趋势图（周/月折线图）
- 完成总数、无错误率、无提示率
- 最佳时间记录（按难度）
- 连续打卡天数

#### 成就徽章（示例）
- "一气呵成"：困难题无撤销/无笔记/无提示通关
- "速度恶魔"：冲刺模式 5 题低于 6 分钟
- "完美主义者"：连续 10 题零错误
- "全勤"：连续 30 天每日挑战
- 每个成就 3 级（铜/银/金）

#### 段位系统
- ELO 类综合评分，可升可降
- 趣味命名：候选数学徒 → 排除法新手 → 数对发现者 → X-Wing 飞行员 → 数独宗师

### 五、UI/UX 要点

#### 配色方案（3 套主题）
- **经典浅色**：白底，蓝色强调。预设数字黑色，玩家填入深蓝
- **暗色模式**：深灰底(#1A1A2E)，浅色数字
- **护眼暖色**：米黄底(#FAF3E0)，长时间游玩友好

#### 颜色编码规则
- 预设数字：最深色（不可修改）
- 玩家填入：主题强调色（蓝/青）
- 错误：红色高亮
- 笔记候选数：灰色小字

#### 主界面
- 极简，不超过 5 个入口：继续游戏、模式选择、每日挑战、统计、设置
- 计时器可隐藏（设置项）

#### 完成反馈
- 简洁动画（<2 秒），如格子波纹扩散 + 清脆音效
- 结算数据：用时、vs 平均对比、提示次数、是否完美通关

### 六、差异化定位

> **"不只是玩数独，而是真正学会数独。"**

核心差异化：
1. **技巧训练系统** — 竞品几乎没有把"教你变强"作为核心体验
2. **智能三级提示** — 引导学习而非喂答案
3. **纯净无广告** — 在广告轰炸的市场中本身就是卖点
4. **轻量化** — APK < 10MB，启动到可玩 < 1 秒

### 七、MVP 优先级（分版本）

**v1.0 (MVP)：**
- 经典模式 5 个难度
- 基于技巧标签的题库生成（保证唯一解）
- 棋盘高亮联动 + 笔记 + 撤销
- 基础统计面板
- 亮色/暗色主题
- 自动保存

**v1.1：**
- 技巧训练模式 + 三级提示系统
- 每日挑战 + 日历打卡
- 成就系统

**v1.2：**
- 冲刺模式
- 复盘功能（解题路径回放）
- 变体数独（对角线、杀手）
- 段位系统

### 八、竞品关键启示

| 竞品 | 学习点 |
|------|--------|
| Sudoku.com | 每日挑战 + 连胜体系设计成熟 |
| Conceptis | 谜题对称设计、教学性提示 |
| Andoku 3 | 变体丰富、精细技巧难度评级、良心无广告 |
| 好数独 | 最完善的技巧教学 + 智能提示 + 求解器 |

**共同差评**：广告太多、笔记操作差、谜题需要猜、基本功能付费锁

---

## 技术决策
| 决策 | 理由 |
|------|------|
| Kotlin | Android 官方推荐语言 |
| Jetpack Compose | 原生开发，体验最佳，包体小 |
| MVVM 架构 | 配合 Jetpack ViewModel + StateFlow，清晰分层 |
| 回溯法 + 技巧标签 | 生成后用求解器分析难度，而非仅按挖空数 |
| API 26 (Android 8.0) | 覆盖 95%+ 设备 |
| 纯离线、无广告/内购 | 专注核心游戏体验，差异化卖点 |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
|      |         |

## 资源
- Android Jetpack Compose 官方文档
- 数独生成算法参考
- 竞品参考：Andoku 3（变体+无广告）、好数独（教学体系）

---
*每执行2次查看/浏览器/搜索操作后更新此文件*
