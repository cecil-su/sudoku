# 功能路线图（backlog）

> 经典模式 + 教学提示 + AI 语音教练（阶段 1–10）已完成。以下是规划过但未实现的扩展功能。
> 本文件是**待办储备**，不替代 `task_plan.md`（后者只追踪当前活跃任务）。
> 已**有意排除**的低价值项（个人自用）：成就徽章、段位 ELO、每日打卡。

## 复用接缝（先立，后续多个功能共享）
- **接缝 A · 技巧定向出题** `generateExercising(technique)`：在难度带内生成 → 用 `demoTrajectory` 过滤出解法真正用到该技巧的题。被「复盘定向练」+「技巧训练专项」共享。
- **接缝 B · 埋点持久化** `TelemetryRepository`：现 `SessionTelemetry` 仅内存、newGame 即重置；落 DataStore 存每局 + 累计 `techniqueHelpCounts`。被「复盘」+「训练弱项」共享。

## 推荐顺序

### ① 游戏设置页（基础设施 · 进行中）
把只管 AI Provider 的 Settings 扩成完整设置页。
- 设置存储：`GameSettings` + `GameSettingsRepository`（DataStore "game_settings"）
- 暖色主题：`ThemeChoice{系统/亮/暗/暖}` + `Color.kt` WarmColorScheme + `Theme.kt` 线程化 + `MainActivity` 顶层读取
- 错误检查模式：`ErrorCheckMode{即时/手动/不检查}` → `GameViewModel.updateErrors` 条件化 + 手动「检查」按钮（**唯一碰核心输入流处**）
- 音效开关（SoundPool）、自动笔记开关、计时器隐藏
- 工作量 M

### ② 10.5 复盘飞轮（使命对齐最高）
一局完成后复盘整局 + 按弱项喂下一题。
- 接缝 B 持久化 + `ReviewScreen`（route review）+ 接缝 A 定向出题
- 回放复用 `DemoController`+`DemoPlayer` 跑 `demoTrajectory(原 givens)`（不需存玩家真实落子，引擎轨迹即标准复盘）
- 工作量 M–L；定向出题需设尝试上限 + 回退普通难度

### ③ 技巧训练专项模式（差异化核心）
选一个技巧，连续刷只考它的题 + `findHint` 讲解。
- `TrainingScreen`（route training）+ 技巧选择（LogicSolver 的 6 级技巧）+ 复用 GameScreen + 接缝 A
- 工作量 M；与②相邻做最省（共享接缝 A）

### ④ 冲刺模式（看兴趣）
连续 5 题计总时，追心流。
- `SprintSession`（题序列 + 累计时 + 当前索引）+ 复用 GameScreen 套 session + 结算屏 + StatsRepository sprint best
- 后台预生成下一题避免卡顿；工作量 M

### ⑤ 变体数独（最大工程 · 看兴趣，建议最后）
对角线 / 不规则宫 / 杀手。
- 约束集泛化：`SudokuSolver.isValid` / `SudokuValidator.hasConflict` / `LogicSolver` 单元定义 从硬编码行列宫 → `units(variant)`/`peers(variant)`；`GameState` 加 variant 字段（杀手加 cage：格分组 + 目标和）；生成器与 `SudokuBoard` 渲染按变体
- 工作量 L；先打通**对角线**（最小：+2 对角单元 + 高亮），杀手后置（cage 模型 + 专属技巧 + 渲染）
- 风险：人类技巧体系对杀手不全适用、难度标签语义变；每种变体重验「逻辑可解 + 唯一解」

---

## 进度
- ① 进行中（增量 1 已落：`GameSettings` + `GameSettingsRepository` + 测试）。详见 `task_plan.md` 阶段 11 / `progress.md`。
