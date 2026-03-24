# Airicraft In-Mod LLM Companion Technical Design

Status: draft  
Date: 2026-03-24

## 1. Summary

本文档定义 Airicraft 下一阶段的核心架构：在 Fabric mod 内直接内置 LLM 驱动的游戏 companion，使其完全掌控安装了 mod 的 Minecraft 客户端；真实玩家通过多人联机方式加入该客户端所在世界，包括：

- 单人世界开启 LAN 后邀请其他玩家加入
- 直接加入远程多人服务器

这意味着本系统不是“辅助玩家操作其本人客户端”的助手，也不是独立 Mineflayer bot，而是“一个拥有自身客户端实体的游戏 agent”。因此：

- 不需要设计“玩家输入优先抢占 AI 控制”的共享控制仲裁
- 需要设计“会话管理、玩家识别、联机内自然交互、稳定执行、调试可观测性”
- `wrapper` 保留为调试与自动化测试通道，不作为产品主交互面
- LLM 直接内置在 mod 中，但所有 LLM 调用必须异步执行，不能阻塞 Minecraft client thread

本设计不沿用 legacy bot 中“LLM 生成任意 JS 并驱动工具调用”的模式，而是采用：

- 语义事件驱动的内置 planner
- 可靠行为代码驱动的行为树 / 状态机
- 面向 companion 场景耦合感知与基础行为的 embodied skills
- 向 LLM 仅暴露高层语义事件、任务状态、对话上下文、有限查询能力

## 2. Current Repository Baseline

当前仓库已有一个较薄的客户端 runtime + localhost bridge 架构：

- [src/client/java/ai/moeru/airicraft/AiricraftClient.java](../../src/client/java/ai/moeru/airicraft/AiricraftClient.java)
- [src/client/java/ai/moeru/airicraft/ClientRuntimeController.java](../../src/client/java/ai/moeru/airicraft/ClientRuntimeController.java)
- [src/client/java/ai/moeru/airicraft/ModBridgeServer.java](../../src/client/java/ai/moeru/airicraft/ModBridgeServer.java)
- [src/client/java/ai/moeru/airicraft/PlayerViewService.java](../../src/client/java/ai/moeru/airicraft/PlayerViewService.java)
- [src/client/java/ai/moeru/airicraft/SingleplayerWorldService.java](../../src/client/java/ai/moeru/airicraft/SingleplayerWorldService.java)
- [src/client/java/ai/moeru/airicraft/SavedServerService.java](../../src/client/java/ai/moeru/airicraft/SavedServerService.java)
- [src/client/java/ai/moeru/airicraft/HighlightManager.java](../../src/client/java/ai/moeru/airicraft/HighlightManager.java)

当前能力：

- 会话能力：查看状态、列出单人世界、加入单人世界、列出服务器、加入服务器
- 读取能力：查看 focus、查看局部 world snapshot
- 写入能力：`lookAt` 与 highlights
- 生命周期：client start、tick、render、disconnect、shutdown

当前不足：

- 没有 agent runtime
- 没有长期任务状态
- 没有事件归纳
- 没有导航、跟随、交互、聊天、恢复等行为原语
- `wrapper` 的 HTTP bridge 语义面向 transport/debug，而不是面向 companion runtime

## 3. Design Goals

### 3.1 Goals

- 在 mod 内直接集成 LLM core
- 让 agent 完全控制客户端
- 支持单人世界 + LAN 邀请，以及直接加入远程服务器
- 让真实玩家通过多人联机与 agent 互动
- 以可靠行为代码为执行主轴，而不是让 LLM 直接产生命令式脚本
- 对 LLM 暴露高层语义事件，而不是每 tick 的低层游戏状态流
- 保持强可观测性，方便 coding agent 与 wrapper 做自动化测试
- 每个模块必须可被 coding agent 在实际运行环境中自动验证——单元测试不算数，只有在生产环境下工作才是真正工作的代码
- Verification 在 mod 内部执行（同 JVM、同线程），Bridge 作为纯只读观测面供 coding agent 检查结果

### 3.2 Non-Goals

- 不构建一个通用的“任何客户端都可共享控制”的输入仲裁系统
- 不复刻 legacy mineflayer 的 JS planner / REPL 机制
- 不把 `wrapper` 做成正式产品交互界面
- 不在 V1 中覆盖采矿、合成、战斗、完整生存自动化
- 不在 V1 中追求全自动多目标自治伙伴

## 4. Core Decisions

### 4.1 LLM 内置于 Mod

LLM 核心直接运行在 Fabric mod 所在 JVM 中。

原因：

- companion 的产品边界已经转向“独占一个客户端”，不再需要通过 wrapper 间接控制
- 将对话、会话状态、行为树状态、世界上下文都放在 mod 内，可减少跨进程序列化与一致性问题
- 对联机内自然交互，内置 planner 更容易拿到低延迟的运行时状态

约束：

- LLM 请求必须在后台线程执行
- 主线程只消费 planner 结果，不直接发起网络请求
- planner 输出必须是受约束的结构化对象，而非任意代码

### 4.2 Wrapper 仅保留为 Debug/Test Surface

`wrapper` 的职责保留为：

- 自动化测试
- runtime introspection
- 调试命令注入
- coding agent 的测试面

`wrapper` 不承担：

- 玩家主交互入口
- 正式产品控制协议
- LLM 所在进程

### 4.3 不单独暴露通用 Perception Layer 给 LLM

这里不采用 legacy 中显式的 `perception -> reflex -> conscious` 作为对外主要架构概念。

改为：

- 在 mod 内部按“能力域”耦合感知与基础行为
- 由这些 embodied capabilities 产出高层语义事件
- LLM 只看到语义事件、目标状态、任务上下文、有限摘要查询

这不是取消感知，而是把感知从“单独产品层”降为“能力域内部细节”。

### 4.4 行为树 / 状态机仍然保留

尽管不强调独立 perception layer，行为树 / 状态机仍然是执行主轴。

原因：

- LLM 适合环境理解、意图理解、任务拆解、自然语言响应
- 行为执行需要稳定 tick 驱动、超时控制、失败恢复、中断、进度反馈
- multiplayer / LAN 场景下的定位、跟随、重获目标、断线恢复不适合交给 LLM 逐步决策

### 4.5 Agentic Verifiability 作为一等设计约束

本项目的开发流程本身是高度 agentic 自动化的：coding agent 负责实现功能，并需要在实际运行环境中自动验证其正确性。因此：

核心架构决策：**Verification 在 mod 内部执行，不经过 HTTP bridge。**

原因：

- 在 mod 内部直接调用 service 方法进行注入和断言，全部运行在 client tick thread 上，**零跨线程同步问题**
- 与 Claude Computer Use / OpenAI CUA 等纯截图验证不同，本项目天然拥有对运行时状态的完全结构化访问——不是通过 HTTP 间接访问，而是在同一个 JVM 内直接引用 runtime 对象
- Verification 代码和功能代码享有**完全相同的运行时特权**：同一线程、同一 JVM、直接引用
- HTTP bridge 退化为**纯只读观测面**，不承担业务注入或验证驱动职责

设计原则（参考 OSWorld execution-based verification、BacktrackAgent verifier-judger-reflector、Sentience assertion-gated execution）：

- **In-process verification >> HTTP-based verification**：注入在 service boundary 而非 HTTP boundary，断言直接读 runtime 对象而非轮询 endpoint。完全消除了跨线程因果性问题
- **Execution-based verification**：验证通过检查实际系统状态（game state、event buffer、goal memory），而非 pixel matching 或 mock
- **Assertion-gated step execution**：每个 capability 定义 `VerificationScenario`，由 tick-driven coroutine 逐步推进
- **Service boundary injection**：每个 service 暴露 `inject*` 方法，走的是和真实事件完全相同的后续处理链路，只是数据来源不是 Minecraft 网络层
- **每个 phase 同步交付 verification**：功能代码 + inject 方法 + VerificationScenario，三件套同时交付

不采用的模式：

- 不依赖 mock 过的单元测试作为验收标准
- 不采用 HTTP-based verification（消除跨线程同步和因果性问题）
- 不采用纯 screenshot-based verification（我们有结构化 API，不需要从截图推断状态）
- 不在 V1 中构建复杂 trajectory 训练流水线

### 4.6 Game Actuation 必须有 Single Source of Truth

legacy bot 的一个核心失败点，是 `reflex behavior` 和 `llm-driven skills` 都在试图驱动同一套游戏输入，最终出现视角、移动、交互互相打架。

这一版必须把该问题作为硬约束消除：

- **所有游戏动作输出只有一个权威执行面**
- 这个执行面是 client thread 上的 `BehaviorTreeRuntime`（经由其节点调用 `LookController` / `MovementController` / 其他后续 effectors）
- `Capability`、`Planner`、`DialogueRuntime`、`VerificationRunner`、`wrapper/debug surface` 都**不能**直接驱动游戏输入
- 这些模块只能产出 `semantic event`、`planner response`、`goal mutation`、`verification trigger` 等高层信号

换言之：

- LLM 决定“要做什么”
- GoalDirector 决定“当前目标是什么”
- BehaviorTreeRuntime 决定“这一 tick 具体按什么输入”

如果未来新增 mining / crafting / combat / recovery 等能力，也必须复用这个单一动作授权模型，不能再引入第二套 reflex executor 或 skill-local actuator ownership。

## 5. Architecture Overview

### 5.1 Top-Level Runtime

在 [ClientRuntimeController.java](../../src/client/java/ai/moeru/airicraft/ClientRuntimeController.java) 下新增一个常驻宿主：

- `EmbodiedAgentRuntime`

职责：

- 管理 agent 生命周期
- 管理 session mode
- 持有行为树/状态机执行器
- 接收能力域语义事件
- 驱动内置 planner
- 维护任务状态与记忆
- 向 debug bridge 暴露状态

推荐顶层结构：

```text
ClientRuntimeController
  ├─ HighlightManager
  ├─ ModBridgeServer
  └─ EmbodiedAgentRuntime
       ├─ SessionRuntime
       ├─ CapabilityRuntime
       ├─ GoalDirector
       ├─ DialogueRuntime
       ├─ LlmCore
       ├─ AgentMemory
       ├─ DebugSnapshotService
       └─ VerificationRunner          ← verification mode 下启用
            ├─ SessionVerification
            ├─ SocialVerification
            ├─ FollowVerification
            ├─ DialogueVerification
            └─ VerificationReport
```

### 5.2 Capability-Oriented Internal Design

内部不按”纯感知层 / 纯行为层”拆，而按能力域拆：

- `SessionCapability`
- `SocialCapability`
- `FollowCapability`
- `NavigationCapability`
- `ObservationCapability`
- `RecoveryCapability`

每个 capability 拥有：

- 局部环境观察
- 状态归纳
- 语义事件发射

**关键约束：capability 不直接驱动 effector（LookController / MovementController）。**

Capability 只负责感知和状态归纳，由行为树节点读取 capability 状态后独占驱动 effector。这避免了 legacy code 中 reflex behavior 和 LLM-driven skills 两套系统同时操作同一组 effector 互相打架的问题。

例如 `FollowCapability`：

- 观察附近玩家与当前跟随目标
- 维护距离、可见性、同维度、卡住状态
- 发出 `follow.target_acquired`、`follow.target_lost`、`follow.stuck` 等事件
- **不直接调用 LookController 或 MovementController**——这由行为树中的 FollowPlayerSubtree 节点读取 FollowCapability 状态后执行

## 6. Session Model

### 6.1 Session Modes

定义四种主 session mode：

- `OUT_OF_WORLD`
- `SINGLEPLAYER_LOCAL`
- `SINGLEPLAYER_LAN_HOST`
- `REMOTE_MULTIPLAYER`

补充 session state：

- `clientBooted`
- `worldLoaded`
- `connectionState`
- `dimensionId`
- `sessionId`
- `serverAddress`
- `lanPort`

### 6.2 Session Transitions

关键状态迁移：

1. client 启动
2. 进入单人世界
3. 单人世界开启 LAN
4. 玩家加入该 LAN 世界
5. 退出世界
6. 加入远程服务器
7. 断线或切服

运行时必须把这些迁移转换成稳定语义事件，而不是让 LLM 自己根据原始 screen/world 变化去推断。

建议事件：

- `session.client_started`
- `session.world_loaded`
- `session.lan_opened`
- `session.remote_connected`
- `session.world_unloaded`
- `session.connection_lost`

### 6.3 LAN Hosting

V1 需要新增 `LanHostingService`，负责：

- 检查当前是否为单人世界
- 开启 LAN
- 返回端口、game mode、是否允许 cheats
- 维护“当前是否已开放 LAN”的状态

这是 agent client 作为 host 时的关键能力，也是让玩家加入该 agent 所在世界的基础。

## 7. Semantic Event Model

### 7.1 Rationale

LLM 不应看到每 tick 的 block/entity 细节，也不应看到 legacy 风格的大量底层 query surface。

LLM 应只看到：

- 当前任务上下文
- 当前关注玩家
- 最近发生的高层语义事件
- 当前行为状态
- 必要的摘要世界状态

### 7.2 Event Schema

定义统一事件结构：

```text
SemanticEvent {
  eventId: String
  type: String
  timestampMs: long
  priority: EventPriority
  source: EventSource
  sessionId: String
  payload: Map<String, Object>
  context: EventContext
}
```

其中：

- `priority`: `CRITICAL | HIGH | NORMAL | LOW`
- `source`: `session | social | follow | navigation | recovery | planner | system`
- `context`: 当前玩家、维度、目标、活跃 goal、最近失败原因等小摘要

### 7.3 Initial Event Set

V1 建议最小事件集：

- `social.player_joined_nearby`
- `social.player_spoke`
- `social.player_addressed_agent`
- `social.player_requested_follow`
- `social.player_requested_stop`
- `social.player_requested_clarification`
- `follow.target_acquired`
- `follow.target_lost`
- `follow.distance_too_far`
- `follow.distance_restored`
- `follow.path_blocked`
- `recovery.stuck_detected`
- `recovery.stuck_cleared`
- `planner.reply_required`
- `planner.goal_completed`
- `planner.goal_failed`
- `session.world_loaded`
- `session.connection_lost`

### 7.4 Event Emission Rules

- 同类事件需去抖，避免每 tick 重复发射
- 距离型事件只在阈值跨越时触发
- `player_spoke` 与 `player_addressed_agent` 必须区分
- 会话类事件优先级高于闲聊事件
- `stuck` 类事件只有持续超过设定时长后才发出

## 8. Agent Memory

### 8.1 Memory Partitions

内存状态拆为三类：

- `ConversationMemory`
- `GoalMemory`
- `OperationalMemory`

#### ConversationMemory

- 最近 N 轮对话
- 当前对话主题
- 最近一次玩家要求
- 最近一次 agent 承诺

#### GoalMemory

- 当前主目标
- 当前目标玩家
- 当前子任务
- 任务开始时间
- 最近完成/失败原因

#### OperationalMemory

- 最近活跃语义事件
- 当前行为树节点
- 最近一次 path blocked/stuck
- session mode

### 8.2 Summarization

LLM 上下文必须受控，不能无限增长。

策略：

- 保存最近短窗口原始对话
- 超出阈值后生成 task/social summary
- planner 输入优先使用 summary + recent turns

初版不需要复杂长期记忆数据库。

## 9. LLM Core Design

### 9.1 Responsibility

LLM 负责：

- 自然语言理解
- 意图识别
- 任务拆解
- 生成对玩家的回复内容
- 在有限 goal 集合中做高层决策
- 当系统进入失败或歧义状态时提出澄清/解释

LLM 不负责：

- 逐步控制移动
- 每 tick 决策
- 直接控制底层动作
- 执行任意代码

### 9.2 Invocation Policy

LLM 仅在以下情况触发：

- 玩家发言
- 目标切换
- 当前 goal 完成
- 当前 goal 失败
- 关键歧义出现
- 闲置且需要主动社交时

LLM 不因普通 tick 触发。

### 9.3 Planner Output Schema

禁止任意 JS/脚本输出。

V1 输出建议固定为 JSON：

```json
{
  "reply": {
    "speak": true,
    "text": "I will follow you."
  },
  "intent": {
    "type": "set_goal",
    "goal": "follow_player",
    "targetPlayer": "Alice"
  },
  "notes": {
    "reasoningSummary": "The player explicitly asked the agent to follow."
  }
}
```

支持的 intent：

- `set_goal`
- `clear_goal`
- `reply_only`
- `ask_clarification`
- `acknowledge_failure`

### 9.4 LLM Backend

新增接口：

```text
LlmBackend {
  PlannerResponse generate(PlannerRequest request)
}
```

V1 实现：

- `OpenAiCompatibleLlmBackend`

技术方案：

- 使用 `java.net.http.HttpClient`
- 使用 JSON 序列化
- 后台 executor 发送请求
- 主线程只轮询/接收完成结果

### 9.5 Config

建议配置路径：

- `config/airicraft/agent.json`

包含：

- provider base URL
- api key
- model
- request timeout
- max recent conversation turns
- enable proactive social mode

敏感配置不通过 bridge 输出。

### 9.6 Error and Fallback Policy

当 LLM 返回异常时的降级行为：

- **超时**：保持当前 goal 继续执行行为树，不阻塞 tick。超时后退回 idle 并发出 `planner.timeout` 事件
- **格式错误 / JSON parse 失败**：丢弃该响应，发出 `planner.parse_error` 事件，向玩家回复 "I got confused for a moment"
- **不支持的 intent type**：忽略 intent，仅执行 reply（如有），发出 `planner.unknown_intent` 事件
- **连续失败 N 次**：进入 `degraded` 模式，仅保持行为树自主执行（follow/idle），暂停 LLM 调用直到手动恢复或冷却期结束
- **API key 无效 / provider 不可达**：启动时检测并报告，不尝试重试。通过 `GET /v1/agent/status` 暴露 `llmAvailable: false`

## 10. Behavior Runtime

### 10.1 Execution Model

行为运行时由 `GoalDirector` + `BehaviorTreeRuntime` 组成。

每 tick：

1. 更新 session/runtime state
2. 更新 capability 局部状态
3. 消费新语义事件
4. 如有 planner 结果，更新 goal memory
5. 运行行为树一步
6. 输出调试快照

### 10.2 Initial Behavior Tree

V1 根树建议：

```text
Root
  ├─ If session invalid -> WaitForSession
  ├─ If reply pending -> ReplyToPlayer
  ├─ If active goal = follow_player -> FollowPlayerSubtree
  ├─ If target player nearby and idle -> IdleCompanionSubtree
  └─ Fallback -> ObserveAndWait
```

`FollowPlayerSubtree`：

- EnsureTargetPlayerKnown
- FaceTargetWhenNear
- ReacquireTargetWhenLost
- MoveCloserWhenTooFar
- StopWhenWithinDistance
- EmitFailureIfStuck

### 10.3 Node Contract

所有节点统一返回：

- `RUNNING`
- `SUCCESS`
- `FAILURE`

并支持：

- `onEnter`
- `tick`
- `onExit`
- timeout
- failure reason
- optional semantic event emission

### 10.4 Skill-Level Coupling

这里明确允许 skill 内部耦合感知与动作。

例如 `MaintainFollowDistanceSkill` 可以在单个节点内部：

- 读取目标玩家实体位置
- 检查距离
- 调整相机
- 驱动移动
- 检测卡住

这类局部耦合是有意设计，不视为架构污染。

但这里的“动作”有严格边界：

- 只允许发生在 **行为树节点内部**
- 只允许通过受控的 controller / effector primitive 执行
- 不允许 capability service、planner、dialogue runtime、verification code 直接调用 controller 去“顺手修一下朝向/移动”

真正需要保持纯净的边界有三个：

- Minecraft 主线程访问边界
- LLM 输入输出边界
- 动作授权边界（single source of truth for actuation）

## 11. First Capability Set

V1 必须落地的能力：

- `ChatIngestService`
- `NearbyPlayerTracker`
- `AgentSpeechService`
- `LookController`
- `MovementController`
- `FollowCapability`
- `SessionCapability`
- `DialogueRuntime`
- `GoalDirector`

### 11.1 ChatIngestService

职责：

- 监听聊天消息
- 识别消息来源玩家
- 判断是否显式提及 agent
- 将消息转换为 `social.player_spoke` 或 `social.player_addressed_agent`

Verification inject：

- `injectMessage(String playerName, String text)` — 模拟一条玩家聊天消息，走完整的后续处理链路（事件发射、地址识别等）

### 11.2 NearbyPlayerTracker

职责：

- 跟踪附近玩家
- 提供最近互动玩家
- 提供当前主交互玩家
- 提供目标玩家是否仍可见、是否同维度

Verification inject：

- `injectPlayerNearby(String name, Vec3d position)` — 模拟一个玩家出现在指定位置
- `injectPlayerDisconnect(String name)` — 模拟一个玩家离线
- `injectPlayerMove(String name, Vec3d newPosition)` — 模拟玩家位置变更

### 11.3 AgentSpeechService

职责：

- 让 agent 在游戏内发言
- 控制冷却与节流
- 记录最后一次发言

Verification inject：

- 无需额外 inject——通过读取 `lastSpokenText()` 和 `lastSpokenTick()` 即可验证

### 11.4 LookController

在现有 [PlayerViewService.java](../../src/client/java/ai/moeru/airicraft/PlayerViewService.java) 基础上演进出更稳定的 typed primitive：

- `lookAtEntity`
- `lookAtPosition`
- `lookTowardPlayerHead`
- 平滑旋转
- 中断规则

### 11.5 MovementController

V1 不要求复杂 pathfinding，但至少支持：

- 前进/停止
- jump
- sprint
- basic obstruction detection
- 简单重试和 stuck detection

后续可升级为更可靠导航。

### 11.6 Service Boundary Injection 汇总

每个 service 必须暴露 inject 方法，用于 `VerificationRunner` 在 client thread 上直接调用。这些方法**不是 mock**——调用后走的是和真实事件完全相同的后续处理链路，只是数据来源不是 Minecraft 网络层。

关键限制：inject 只允许模拟**外部输入或系统事件**，不允许绕过正常 runtime 直接驱动 controller / effector，也不应成为常规调试控制面。

| Service | Inject 方法 | 模拟的是什么 |
|---------|------------|------------|
| `ChatIngestService` | `injectMessage(player, text)` | 玩家发言 |
| `NearbyPlayerTracker` | `injectPlayerNearby(name, pos)` | 玩家出现在附近 |
| `NearbyPlayerTracker` | `injectPlayerDisconnect(name)` | 玩家离线 |
| `NearbyPlayerTracker` | `injectPlayerMove(name, pos)` | 玩家位置变更 |
| `SessionRuntime` | `injectConnectionLost()` | 断线 |
| `LlmBackend` | `injectMockResponse(response)` | LLM 返回固定结果（可选，用于不依赖真实 LLM 的验证） |

`GoalDirector` 不提供常规 `injectGoal()` 作为验证入口。verification 应优先通过“玩家发言 -> planner response -> goal update”这条真实链路来驱动目标变更；如果后续确实需要 goal-level test seam，也必须明确标记为 test-only internal API，且不能暴露给 bridge/debug surface。

## 12. Multiplayer Interaction Model

### 12.1 Primary Interaction Player

agent 需要维护一个 `primaryInteractionPlayer`。

选取规则：

- 最近显式与 agent 说话的玩家优先
- 当前目标玩家优先
- 当前最近且持续靠近的玩家可作为候选

过期机制：

- primary player 绑定有 freshness TTL（建议 120 秒）
- 如果 TTL 内 primary player 没有任何交互（发言、proximity 信号），primary 位置自动释放
- 释放后，下一个与 agent 交互的玩家成为新 primary
- 这避免了"Alice 说完话离开后，Bob 靠近但 agent 仍然锁定 Alice"的目标漂移问题

这样可避免多人同时存在时 agent 目标飘移。

### 12.2 LAN and Remote Equivalence

从 companion runtime 视角：

- LAN host 与 remote multiplayer 应尽量统一为“多人会话”
- 差异主要体现在 session capability 层

companion 行为层不应关心玩家是通过 LAN 还是远程服务器进入，只关心：

- 当前 world 是否稳定
- 目标玩家是否存在
- 是否能在当前世界继续互动

## 13. Threading and Scheduling

### 13.1 Client Thread Ownership

所有 Minecraft world/player 读写必须在 client thread 上执行。

禁止：

- 后台线程直接访问 `MinecraftClient` 状态
- 后台线程直接操作 player/world
- 后台线程直接更新 render/debug state

### 13.2 Background Executors

后台线程仅负责：

- LLM 请求
- prompt 构建
- summary 生成
- 非 Minecraft 状态的纯计算

推荐两个 executor：

- `plannerExecutor`
- `ioExecutor`

### 13.3 Main-Thread Handoff

planner 完成后，不直接执行动作。

流程：

1. 背景线程生成 `PlannerResponse`
2. 将结果放入 thread-safe queue
3. `EmbodiedAgentRuntime.onTick` 读取 queue
4. 更新 goal memory
5. 行为树在 client thread 上推进，并作为**唯一**动作执行者调用 controllers/effectors

## 14. Debug, Observation, and Verification

### 14.1 Bridge 作为纯只读观测面

[ModBridgeServer.java](../../src/client/java/ai/moeru/airicraft/ModBridgeServer.java) 保留，但职责严格限定为**只读观测**。Bridge 上不存在业务写操作。

GET 端点：

- `GET /v1/agent/status` — 返回完整 runtime 快照
- `GET /v1/agent/session` — 返回 session mode、lanPort、serverAddress、dimensionId
- `GET /v1/agent/goals` — 返回当前 goal 状态
- `GET /v1/agent/events/recent?since=<seqNo>` — 返回最近语义事件列表（支持序列号过滤）
- `GET /v1/agent/dialogue` — 返回最近对话、最后 planner 响应
- `GET /v1/agent/tree` — 返回行为树当前活跃节点路径
- `GET /v1/verification/results` — 返回最近 verification 结果

唯一的写端点（仅触发，不注入业务状态）：

- `POST /v1/verification/run {"scenario": "follow.basic"}` — 触发已编译的 verification scenario

不再保留的端点：

- ~~`POST /v1/agent/debug/simulate-event`~~ — 改为 mod 内部 inject
- ~~`POST /v1/agent/debug/set-goal`~~ — 改为 mod 内部 inject

#### 14.1.1 `GET /v1/agent/status` Response Schema

```json
{
  "state": "SINGLEPLAYER_LAN_HOST",
  "position": {"x": 100.5, "y": 64.0, "z": -200.3, "dimension": "minecraft:overworld"},
  "nearbyPlayers": [
    {"name": "Alice", "distance": 5.2, "visible": true}
  ],
  "follow": {
    "targetPlayer": "Alice",
    "currentDistance": 5.2,
    "state": "TRACKING"
  },
  "behaviorTree": {
    "activeNode": "MoveCloserWhenTooFar",
    "activeNodePath": ["Root", "FollowPlayerSubtree", "MoveCloserWhenTooFar"]
  },
  "primaryPlayer": {"name": "Alice", "expiresAtTick": 158400},
  "llmAvailable": true,
  "degraded": false,
  "tickCount": 156000
}
```

### 14.2 Wrapper Usage

`wrapper` 用于：

- 启动后通过 bridge GET 检查 agent 状态
- 查看最近事件
- 触发 verification scenario 并读取结果
- 人工调试时观测 runtime 状态

### 14.3 Visual Debugging

[HighlightManager.java](../../src/client/java/ai/moeru/airicraft/HighlightManager.java) 用于：

- 当前目标玩家位置
- 当前跟随目标点
- 当前失联区域
- 当前行为树活跃目标

该功能是调试增强，不应成为 runtime 核心依赖。

### 14.4 In-Mod Verification System

#### 14.4.1 设计理念

参考 LLM computer use 领域的最新进展（OSWorld execution-based verification、BacktrackAgent verifier-judger-reflector pipeline、Sentience assertion-gated execution），并结合本项目的特殊优势——verification 代码运行在和功能代码相同的 JVM、相同的 client thread 上，直接引用 runtime 对象——本项目采用**in-mod verification**而非传统的 HTTP-based testing。

核心区别：

```text
传统单元测试:   mock 一切                        → 不算数
HTTP 验证:      HTTP POST → 跨线程 → HTTP GET    → 时序差、因果性问题
In-mod 验证:    同 JVM、同 tick thread、直接引用   → 零同步问题、最高确定性
```

关键洞察：

- **注入在 service boundary，不在 HTTP boundary**：直接调用 `chatIngest.injectMessage("Alice", "follow me")`，走的是和真实玩家聊天完全相同的后续处理链路
- **断言直接读 runtime 对象**：不经过 JSON 序列化/反序列化，不存在快照过期问题
- **Tick-driven coroutine**：verification scenario 不是一次性执行完的——它挂在 `onTick()` 上，每 tick 推进一步，可以等待多 tick 行为（走路、LLM 响应等）

#### 14.4.2 VerificationRunner 架构

```text
VerificationRunner
  ├─ scenarios: Map<String, VerificationScenario>  ← 场景注册表
  ├─ currentScenario: VerificationScenario         ← 当前执行中
  ├─ stepIndex: int                                 ← 当前步骤
  ├─ ticksSinceStep: int                            ← 当前步骤已等待 ticks
  └─ report: VerificationReport                    ← 结果
```

`VerificationRunner` 通过 config 或启动参数决定是否启用：

```json
{
  "verification": {
    "enabled": true,
    "autoRunOnWorldLoad": ["session.basic", "social.chat_ingest"],
    "autoRunAll": false
  }
}
```

Runner 的 tick 循环核心逻辑：

```text
onTick():
  if currentScenario == null: return
  step = currentScenario.currentStep()

  case REQUIRE:
    if predicate passes → advance
    else → fail("precondition not met: " + description)

  case ACTION:
    execute action (直接调用 service inject 方法)
    record lastActionTick
    advance

  case WAIT_UNTIL:
    if predicate passes → advance
    else if ticksSinceStep > timeoutTicks → fail("timeout")
    else → do nothing, check again next tick

  case ASSERT:
    if assertion passes → advance
    else → fail(assertion.failureMessage())

  case SCENARIO_COMPLETE:
    record PASSED in report
    start next scenario (if queued)
```

#### 14.4.3 Verification Scenario 示例

每个 capability 同时交付自己的 `VerificationScenario`。这是编译进 mod 的 Java 代码：

**SessionVerification:**

```java
public class SessionVerification extends VerificationScenario {
    @Override public String name() { return "session.basic"; }

    @Override protected void define(ScenarioBuilder s) {
        s.require("client booted", () -> session.mode() == OUT_OF_WORLD);

        s.action("join world", () -> session.joinWorld("test_world"));
        s.waitUntil("world loaded", 300, () -> session.mode() == SINGLEPLAYER_LOCAL);
        s.assertThat("world_loaded event",
            () -> events.containsSince(scenarioStartTick, "session.world_loaded"));

        s.action("open LAN", () -> lanHosting.openLan());
        s.waitUntil("LAN opened", 100, () -> session.mode() == SINGLEPLAYER_LAN_HOST);
        s.assertThat("LAN port assigned", () -> session.lanPort() > 0);

        s.action("leave world", () -> session.leaveWorld());
        s.waitUntil("back to menu", 300, () -> session.mode() == OUT_OF_WORLD);
    }
}
```

**FollowVerification:**

```java
public class FollowVerification extends VerificationScenario {
    @Override public String name() { return "follow.basic"; }

    @Override protected void define(ScenarioBuilder s) {
        s.require("in world", () -> session.mode() != OUT_OF_WORLD);

        // 注入：模拟玩家出现（service boundary injection）
        s.action("spawn Alice nearby", () ->
            nearbyPlayers.injectPlayerNearby("Alice", agentPos().add(5, 0, 0)));

        // 注入：模拟聊天（走完整处理链路）
        s.action("Alice requests follow", () ->
            chatIngest.injectMessage("Alice", "@agent follow me"));

        // 等待 LLM 响应（或 mock response）
        s.waitUntil("planner responds", 300, () ->
            dialogue.lastResponse() != null);
        s.assertThat("intent is set_goal",
            () -> dialogue.lastResponse().intent().type() == IntentType.SET_GOAL);

        // 验证行为树生效
        s.waitUntil("follow acquired", 200, () ->
            events.containsSince(scenarioStartTick, "follow.target_acquired"));
        s.assertThat("goal is follow",
            () -> goals.active().type() == GoalType.FOLLOW_PLAYER);

        // 模拟目标移动
        s.action("Alice moves far", () ->
            nearbyPlayers.injectPlayerMove("Alice", agentPos().add(20, 0, 0)));
        s.waitUntil("agent starts moving", 100, () ->
            behaviorTree.activeNodePath().contains("MoveCloser"));

        // 模拟目标丢失
        s.action("Alice disconnects", () ->
            nearbyPlayers.injectPlayerDisconnect("Alice"));
        s.waitUntil("target lost event", 100, () ->
            events.containsSince(lastActionTick, "follow.target_lost"));
    }
}
```

**DialogueVerification:**

```java
public class DialogueVerification extends VerificationScenario {
    @Override public String name() { return "dialogue.basic"; }

    @Override protected void define(ScenarioBuilder s) {
        s.require("in world", () -> session.mode() != OUT_OF_WORLD);

        // 可选：注入 mock LLM 响应（不依赖真实 LLM provider）
        s.action("setup mock LLM", () -> llmBackend.injectMockResponse(
            new PlannerResponse(
                new Reply(true, "Sure, I'll follow you!"),
                new Intent(IntentType.SET_GOAL, "follow_player", "Alice"),
                null
            )));

        s.action("Alice speaks", () ->
            chatIngest.injectMessage("Alice", "@agent follow me"));

        s.waitUntil("planner responds", 300, () ->
            dialogue.lastResponse() != null);
        s.assertThat("reply text exists",
            () -> !dialogue.lastResponse().reply().text().isEmpty());
        s.assertThat("agent spoke in chat",
            () -> speechService.lastSpokenTick() > scenarioStartTick);
    }
}
```

**LlmDegradationVerification:**

```java
public class LlmDegradationVerification extends VerificationScenario {
    @Override public String name() { return "llm.degradation"; }

    @Override protected void define(ScenarioBuilder s) {
        s.require("in world", () -> session.mode() != OUT_OF_WORLD);

        // 注入连续超时
        s.action("trigger timeout 1", () -> llmBackend.injectTimeout());
        s.waitUntil("timeout event 1", 100, () ->
            events.containsSince(lastActionTick, "planner.timeout"));

        s.action("trigger timeout 2", () -> llmBackend.injectTimeout());
        s.waitUntil("timeout event 2", 100, () ->
            events.countSince(scenarioStartTick, "planner.timeout") >= 2);

        s.action("trigger timeout 3", () -> llmBackend.injectTimeout());
        s.waitUntil("degraded mode", 100, () -> runtime.isDegraded());

        // 验证行为树仍正常
        s.assertThat("behavior tree still running",
            () -> behaviorTree.activeNode() != null);

        // 手动恢复
        s.action("reset LLM state", () -> runtime.resetLlmState());
        s.waitUntil("no longer degraded", 20, () -> !runtime.isDegraded());
    }
}
```

#### 14.4.4 触发机制

三种方式：

**A. Config 自动运行：**

mod 启动并进入世界后，根据 config 自动运行指定 scenarios。适合 CI 或 coding agent 的 build-test 循环。

**B. 游戏内 chat command：**

```text
/airicraft verify follow.basic
/airicraft verify all
/airicraft verify --list
```

Coding agent 可通过 wrapper 触发游戏内命令或 verification run 入口，但不应通过任何 debug surface 直接注入 goal 或 controller 动作。

**C. Bridge 触发：**

```text
POST /v1/verification/run {"scenario": "follow.basic"}
GET  /v1/verification/results
```

这个 POST 只是往队列里放一个"请在下一个 tick 开始跑此 scenario"的信号。实际执行在 client thread 上，不存在跨线程因果性问题。

#### 14.4.5 Verification Report

失败时自动附带 runtime snapshot，coding agent 不需要再去猜"为什么失败了"：

```json
{
  "scenario": "follow.basic",
  "status": "FAILED",
  "startedAtTick": 14200,
  "completedAtTick": 14487,
  "durationTicks": 287,
  "steps": [
    {"name": "in world",             "status": "PASSED"},
    {"name": "spawn Alice nearby",   "status": "PASSED"},
    {"name": "Alice requests follow","status": "PASSED"},
    {"name": "planner responds",     "status": "PASSED", "waitedTicks": 87},
    {"name": "intent is set_goal",   "status": "PASSED"},
    {"name": "follow acquired",      "status": "FAILED",
     "reason": "timeout after 200 ticks",
     "snapshot": {
       "goals": {"active": null},
       "recentEvents": ["social.player_addressed_agent"],
       "behaviorTree": {"activeNode": "ObserveAndWait", "path": ["Root", "ObserveAndWait"]},
       "position": {"x": 100.5, "y": 64.0, "z": -200.3}
     }}
  ]
}
```

#### 14.4.6 Coding Agent 工作流

```text
1. 写功能代码（capability / service）
2. 写对应 inject 方法
3. 写对应 VerificationScenario
4. gradle build（incremental，几秒）
5. mod 重启或热加载
6. Verification 自动运行（config autoRun）或手动触发（chat command / bridge POST）
7. GET /v1/verification/results → 读取 PASSED/FAILED
8. 如果 FAILED → 读取 snapshot → 定位 bug → 回到步骤 1
```

关键：coding agent 判断是否通过只需要**一个 GET 调用**。所有复杂的注入、等待、断言都在 mod 内部完成。

## 15. Proposed Package Layout

建议新增包：

```text
src/client/java/ai/moeru/airicraft/agent/
  AgentRuntime.java
  AgentRuntimeSnapshot.java
  AgentConfig.java

src/client/java/ai/moeru/airicraft/agent/session/
  SessionRuntime.java
  LanHostingService.java
  SessionSnapshot.java

src/client/java/ai/moeru/airicraft/agent/social/
  ChatIngestService.java
  DialogueRuntime.java
  AgentSpeechService.java
  PrimaryInteractionResolver.java

src/client/java/ai/moeru/airicraft/agent/follow/
  FollowCapability.java
  FollowState.java
  FollowTarget.java

src/client/java/ai/moeru/airicraft/agent/control/
  LookController.java
  MovementController.java

src/client/java/ai/moeru/airicraft/agent/llm/
  LlmBackend.java
  OpenAiCompatibleLlmBackend.java
  PlannerRequest.java
  PlannerResponse.java
  PlannerExecutor.java

src/client/java/ai/moeru/airicraft/agent/memory/
  AgentMemory.java
  ConversationMemory.java
  GoalMemory.java

src/client/java/ai/moeru/airicraft/agent/events/
  SemanticEvent.java
  SemanticEventBuffer.java
  SemanticEventFactory.java

src/client/java/ai/moeru/airicraft/agent/behavior/
  GoalDirector.java
  BehaviorTreeRuntime.java
  BehaviorNode.java
  NodeStatus.java

src/client/java/ai/moeru/airicraft/agent/verification/
  VerificationRunner.java
  VerificationScenario.java
  ScenarioBuilder.java
  VerificationReport.java
  VerificationStep.java
  scenarios/
    SessionVerification.java
    SocialVerification.java
    FollowVerification.java
    DialogueVerification.java
    LlmDegradationVerification.java
```

## 16. Rollout Plan

每个 phase 同步交付四样东西：功能代码、service inject 方法、对应的 VerificationScenario（Java 代码）、bridge GET 端点（纯观测）。

### Phase 1: Runtime Skeleton + Verification Infrastructure

交付：

- `EmbodiedAgentRuntime` + lifecycle 接入
- config loading
- `VerificationRunner` 骨架 + `ScenarioBuilder` DSL
- `SessionVerification` scenario

Bridge endpoints：

- `GET /v1/agent/status`
- `GET /v1/verification/results`
- `POST /v1/verification/run`

Verification scenario `session.basic`：

```text
require: client booted, OUT_OF_WORLD
action:  session.joinWorld("test_world")
wait:    session.mode() == SINGLEPLAYER_LOCAL (300 ticks)
assert:  events contain "session.world_loaded"
action:  session.leaveWorld()
wait:    session.mode() == OUT_OF_WORLD (300 ticks)
→ PASS
```

### Phase 2: Session + Social Ingest

交付：

- `ChatIngestService` + `injectMessage()`
- `NearbyPlayerTracker` + `injectPlayerNearby()` / `injectPlayerDisconnect()` / `injectPlayerMove()`
- `PrimaryInteractionResolver`
- `LanHostingService`
- `SocialVerification` scenario

Bridge endpoints：

- `GET /v1/agent/session`
- `GET /v1/agent/events/recent?since=<seqNo>`

Verification scenario `social.chat_ingest`：

```text
require: in world
action:  nearbyPlayers.injectPlayerNearby("Alice", pos)
assert:  events contain "social.player_joined_nearby"
action:  chatIngest.injectMessage("Alice", "hello everyone")
assert:  events contain social.player_spoke{player:"Alice"}
action:  chatIngest.injectMessage("Alice", "@agent follow me")
assert:  events contain social.player_addressed_agent{player:"Alice"}
→ PASS
```

Verification scenario `session.lan`：

```text
require: in SINGLEPLAYER_LOCAL
action:  lanHosting.openLan()
wait:    session.mode() == SINGLEPLAYER_LAN_HOST (100 ticks)
assert:  session.lanPort() > 0
assert:  events contain "session.lan_opened"
→ PASS
```

### Phase 3: Minimal Embodied Follow

交付：

- `LookController`
- `MovementController`
- `FollowCapability`
- 行为树骨架 + `GoalDirector`
- stuck detection
- `FollowVerification` scenario

Bridge endpoints：

- `GET /v1/agent/goals`
- `GET /v1/agent/tree`

Verification scenario `follow.basic`：

```text
require: in world
action:  nearbyPlayers.injectPlayerNearby("Alice", agentPos + (5,0,0))
action:  chatIngest.injectMessage("Alice", "@agent follow me")
wait:    dialogue.lastResponse() != null (300 ticks)
assert:  dialogue.lastResponse().intent.type == SET_GOAL
wait:    events contain "follow.target_acquired" (200 ticks)
assert:  goals.active().type == FOLLOW_PLAYER
action:  nearbyPlayers.injectPlayerMove("Alice", agentPos + (20,0,0))
wait:    behaviorTree.activeNodePath contains "MoveCloser" (100 ticks)
action:  nearbyPlayers.injectPlayerDisconnect("Alice")
wait:    events contain "follow.target_lost" (100 ticks)
→ PASS
```

### Phase 4: In-Mod LLM

交付：

- `LlmBackend` + `injectMockResponse()` + `injectTimeout()`
- planner request/response schema
- `DialogueRuntime`
- 最近对话记忆
- `DialogueVerification` + `LlmDegradationVerification` scenarios

Bridge endpoints：

- `GET /v1/agent/dialogue`

Verification scenario `dialogue.basic`：

```text
require: in world
action:  llmBackend.injectMockResponse(SET_GOAL reply)
action:  chatIngest.injectMessage("Alice", "@agent follow me")
wait:    dialogue.lastResponse() != null (300 ticks)
assert:  dialogue.lastResponse().reply.text is non-empty
assert:  speechService.lastSpokenTick() > scenarioStartTick
→ PASS
```

Verification scenario `llm.degradation`：

```text
require: in world
action:  llmBackend.injectTimeout() × 3
wait:    runtime.isDegraded() (100 ticks)
assert:  behaviorTree.activeNode() != null (行为树仍在运行)
action:  runtime.resetLlmState()
wait:    !runtime.isDegraded() (20 ticks)
→ PASS
```

### Phase 5: Hardening + End-to-End

交付：

- retry / timeout / rate limit handling
- session recovery
- long-running stability
- summary compaction
- highlight overlays
- 端到端 verification scenarios

Verification scenario `e2e.full_companion_flow`：

```text
require: OUT_OF_WORLD
action:  session.joinWorld("test_world")
wait:    SINGLEPLAYER_LOCAL
action:  lanHosting.openLan()
wait:    SINGLEPLAYER_LAN_HOST
action:  nearbyPlayers.injectPlayerNearby("Alice", pos)
action:  chatIngest.injectMessage("Alice", "@agent follow me")
wait:    goals.active().type == FOLLOW_PLAYER
wait:    events contain "follow.target_acquired"
action:  nearbyPlayers.injectPlayerDisconnect("Alice")
wait:    events contain "follow.target_lost"
action:  session.injectConnectionLost()
wait:    session.mode() == OUT_OF_WORLD (不卡死)
→ PASS
```

## 17. Acceptance Criteria

V1 验收分两部分：人类可读描述 + 机器可执行 verification protocol。两者等价，后者是前者的精确表达。

### 17.1 Human-Readable Criteria

- agent client 能进入单人世界并开启 LAN
- 其他客户端玩家可加入该世界
- 玩家在聊天中要求 agent 跟随时，agent 能识别目标玩家并执行跟随
- 玩家停止或离开视野后，agent 能维持/重获目标，或报告失败原因
- agent 能在聊天中做出合理回应
- disconnect / world unload / target lost 时不会卡死
- wrapper 能读取 agent 状态、最近事件、当前目标、当前行为树节点

### 17.2 Machine-Executable Verification

以下验收标准由编译进 mod 的 `VerificationScenario` 实现。Coding agent 通过 `GET /v1/verification/results` 读取通过/失败结果。

| AC | 对应人类标准 | Verification Scenario | 关键断言 |
|----|------------|----------------------|---------|
| AC-1 | 进入世界 + LAN | `session.basic` + `session.lan` | mode transitions, lanPort > 0 |
| AC-2 | 玩家加入 | `social.chat_ingest` | `social.player_joined_nearby` event |
| AC-3 | 聊天驱动跟随 | `follow.basic` | intent == SET_GOAL, `follow.target_acquired` |
| AC-4 | 目标丢失 + 恢复 | `follow.basic` (后半段) | `follow.target_lost` event |
| AC-5 | 聊天回应 | `dialogue.basic` | reply.text non-empty, agent spoke |
| AC-6 | 断线韧性 | `e2e.full_companion_flow` | connection_lost 后不卡死 |
| AC-7 | LLM 降级 | `llm.degradation` | degraded mode + 行为树仍运行 + 恢复 |

V1 验收 = 所有上述 scenarios 在实际运行的 Minecraft 客户端中 PASS。

## 18. Risks and Mitigations

### 18.1 LLM Stall

风险：

- 网络慢或 provider 超时导致 planner 卡住

缓解：

- planner 异步化
- 行为树不等待 planner 阻塞 tick
- 未返回时保持当前 goal 或退回 idle

### 18.2 Over-Abstracting Too Early

风险：

- 过早追求通用 `perception layer` 与 `action layer` 导致复杂度膨胀

缓解：

- capability-oriented 设计
- V1 仅围绕 social + follow 场景建设
- 只保留必要边界：client thread ownership 与 LLM I/O contract

### 18.3 Multiplayer Uncertainty

风险：

- 多玩家同时靠近、同时说话，导致目标不稳定

缓解：

- 引入 `primaryInteractionPlayer`
- 目标切换必须显式或有冷却

### 18.4 Navigation Reliability

风险：

- V1 没有成熟 pathfinding 时，跟随稳定性不足

缓解：

- V1 只做短距离跟随与近距离陪伴
- 明确对”远距离自主寻路”降级处理

### 18.5 Verification Scenario 与功能代码耦合

风险：

- Verification scenarios 直接引用 runtime 内部方法，功能重构时 scenario 容易 break
- inject 方法的行为可能与真实 Minecraft 网络层产生的事件有微妙差异

缓解：

- inject 方法走的是和真实事件完全相同的后续处理链路，只是入口不同——最小化行为差异
- inject 方法定义在 service 的公开接口上，重构时编译器会报错
- Verification scenario 失败时附带完整 runtime snapshot，便于定位是 scenario 过时还是功能 bug
- Phase 5 的 e2e scenario 可通过真实 LAN 连接运行（不使用 inject），作为最终 smoke test

### 18.6 LLM 迁移至 Sidecar

风险：

- 长期来看 LLM 调用可能需要迁移到外部 sidecar（资源隔离、模型切换、多 agent 共享）

缓解：

- 当前 `LlmBackend` 接口已经是 HTTP client 模式，迁移路径天然存在
- 迁移仅需替换 `LlmBackend` 实现，不影响 planner I/O contract
- 本设计有意保留此迁移路径，但 V1 不实现

## 19. Explicit Defaults

本文档明确采用以下默认决策：

- LLM 内置 mod
- wrapper 仅调试
- agent 独占控制客户端
- 真实玩家通过 multiplayer 与 agent 互动
- V1 产品重点为 `social + follow companion`
- planner 输出为结构化 JSON，而非脚本
- capability 内允许感知与基础行为耦合
- 行为树负责执行，LLM 负责高层语义与计划
- Verification 在 mod 内部执行（in-process），不经过 HTTP bridge
- 每个 service 暴露 inject 方法用于 verification，走真实处理链路
- Bridge 是纯只读观测面，不承担业务注入
- 每个 phase 同步交付 VerificationScenario（Java 代码）
- 单元测试不作为验收标准——只有在实际运行环境中通过的 verification 才算数
- LLM 异常时降级到行为树自主执行，不阻塞 runtime
- primaryInteractionPlayer 有 freshness TTL，超时自动释放

## 20. Next Implementation Target

实现应从以下最小闭环开始：

1. `EmbodiedAgentRuntime`
2. `SessionRuntime`
3. `ChatIngestService`
4. `NearbyPlayerTracker`
5. `PrimaryInteractionResolver`
6. `LookController`
7. `MovementController`
8. `FollowCapability`
9. `BehaviorTreeRuntime`
10. `LlmBackend` + `DialogueRuntime`

第一条端到端路径：

- 进入单人世界
- 开启 LAN
- 玩家加入
- 玩家发言要求跟随
- agent 识别玩家
- agent 回复
- agent 看向并跟随
- 玩家消失时 agent 报告丢失/恢复

### 20.1 Actionable Implementation Plan

推荐按 5 个连续工作包推进。每个工作包都必须满足：

- 代码可编译
- 对应 verification scenario 可运行
- bridge 只增加只读观测面
- 不引入第二条动作执行通路

### Work Package 1: Runtime Skeleton and Ownership Guardrails

目标：先把宿主 runtime、tick 驱动、verification runner、以及 single-source-of-truth 守卫搭起来，避免后面边写边漂移。

实现步骤：

1. 新建 `agent/` 包骨架与 `EmbodiedAgentRuntime`，接入 `ClientRuntimeController` lifecycle。
2. 新建 `AgentRuntimeSnapshot`、`AgentConfig`、`SessionSnapshot` 等最小快照对象。
3. 实现 `VerificationRunner`、`VerificationScenario`、`ScenarioBuilder` 最小版本，只支持 `require / action / waitUntil / assert`。
4. 新增 `GET /v1/agent/status`、`GET /v1/verification/results`、`POST /v1/verification/run`。
5. 明确 controller ownership 约束：只有 `BehaviorTreeRuntime` 所在层能持有或调用 `LookController` / `MovementController`。

建议改动文件：

- `src/client/java/ai/moeru/airicraft/ClientRuntimeController.java`
- `src/client/java/ai/moeru/airicraft/ModBridgeServer.java`
- `src/client/java/ai/moeru/airicraft/agent/AgentRuntime.java`
- `src/client/java/ai/moeru/airicraft/agent/verification/*`

完成定义：

- `session.basic` 能从 bridge 触发并给出 PASS/FAIL
- bridge 能返回 runtime 未初始化 / 已初始化状态
- 代码里还没有任何 capability 或 dialogue runtime 直接触碰 controller

Single-source-of-truth 检查：

- controller 不暴露给 capability/service/debug handler
- verification 只能触发 scenario，不能直接注入动作

### Work Package 2: Session and Social Signal Pipeline

目标：把“世界状态 + 玩家聊天/出现”变成稳定语义事件，为后面 planner 和行为树提供唯一输入面。

实现步骤：

1. 实现 `SessionRuntime`，统一 world load / unload / LAN / disconnect 状态归纳。
2. 实现 `ChatIngestService`，把聊天转成 `social.*` 事件。
3. 实现 `NearbyPlayerTracker`，维护 nearby players、可见性、位置更新。
4. 实现 `PrimaryInteractionResolver`，只产出 target player 决策，不直接做动作。
5. 打通 `SemanticEventBuffer`，让上述 service 全部只写 event，不写 controller。
6. 完成 `social.chat_ingest` 与 `session.lan` verification。

建议改动文件：

- `src/client/java/ai/moeru/airicraft/agent/session/*`
- `src/client/java/ai/moeru/airicraft/agent/social/*`
- `src/client/java/ai/moeru/airicraft/agent/events/*`

完成定义：

- agent 能稳定产出 `session.*`、`social.*` 事件
- `primaryInteractionPlayer` 能随聊天/接近变化而更新
- 所有 social/session 流程都还没有直接驱动视角或移动

Single-source-of-truth 检查：

- `ChatIngestService` 和 `NearbyPlayerTracker` 只发事件，不保存 controller 引用
- `PrimaryInteractionResolver` 只能输出 player selection / target metadata

### Work Package 3: Control Primitives and Minimal Follow Loop

目标：引入真正的动作执行面，但只允许通过行为树调用。

实现步骤：

1. 实现 `LookController` typed primitives，封装平滑转向与中断规则。
2. 实现 `MovementController` typed primitives，先支持前进、停止、jump、sprint、stuck detection。
3. 实现 `FollowCapability`，只负责观察目标、距离、可见性、卡住状态与 `follow.*` 事件。
4. 实现 `GoalDirector`、`BehaviorTreeRuntime`、`BehaviorNode`、`NodeStatus`。
5. 先落最小根树：`WaitForSession`、`ReplyToPlayer`、`FollowPlayerSubtree`、`ObserveAndWait`。
6. 完成 `follow.basic` verification，并补 `GET /v1/agent/goals`、`GET /v1/agent/tree`。

建议改动文件：

- `src/client/java/ai/moeru/airicraft/agent/control/*`
- `src/client/java/ai/moeru/airicraft/agent/follow/*`
- `src/client/java/ai/moeru/airicraft/agent/behavior/*`

完成定义：

- 玩家发言后，即使 planner 还是 mock/占位，也能让 follow goal 驱动行为树执行
- `MoveCloserWhenTooFar` 与 `follow.target_lost` 能被稳定观测到
- 唯一调用 controller 的位置在行为树节点内部

Single-source-of-truth 检查：

- capability 不得 import controller
- debug bridge 返回 active node path，但不提供任何 controller write API
- 所有移动/看向日志都能映射回 behavior tree node

### Work Package 4: In-Mod LLM and Goal Mutation Pipeline

目标：让聊天真正经过 planner 形成 reply + intent，但 planner 仍然不能直接操作动作层。

实现步骤：

1. 实现 `PlannerRequest`、`PlannerResponse`、`LlmBackend`、`PlannerExecutor`。
2. 实现 `DialogueRuntime`，负责 prompt 构建、recent turns、last response。
3. 规定唯一 goal 变更路径：`social/player event -> planner response -> goal memory update -> behavior tree tick`。
4. 增加 timeout / parse error / degraded mode。
5. 完成 `dialogue.basic` 与 `llm.degradation` verification。
6. 增加 `GET /v1/agent/dialogue` 只读观测。

建议改动文件：

- `src/client/java/ai/moeru/airicraft/agent/llm/*`
- `src/client/java/ai/moeru/airicraft/agent/social/DialogueRuntime.java`
- `src/client/java/ai/moeru/airicraft/agent/memory/*`

完成定义：

- LLM 可以设置 `follow_player` 目标并生成回复
- provider 超时不会卡住 tick
- planner 结果只会改变 goal/reply state，不会直接触发 controller

Single-source-of-truth 检查：

- `PlannerExecutor` 不得引用 controller 或 Minecraft player/world 对象
- `LlmBackend.injectMockResponse()` 只注入 planner output，不注入动作

### Work Package 5: Hardening, Recovery, and End-to-End Flow

目标：把最小闭环从“能跑”提升到“能持续跑、能定位问题、能在真实多人场景下恢复”。

实现步骤：

1. 实现 session recovery、target lost recovery、retry/backoff、summary compaction。
2. 增加 visual debug overlays，但保持其完全依赖 runtime snapshot，不反向控制 runtime。
3. 完成 `e2e.full_companion_flow` 与真实 LAN smoke test。
4. 补全 failure snapshot、最近事件窗口、active goal / node / target player 的诊断输出。
5. 做一次长时间运行检查，确认 disconnect / world unload / reconnect 后行为树不会僵死。

建议改动文件：

- `src/client/java/ai/moeru/airicraft/agent/session/*`
- `src/client/java/ai/moeru/airicraft/agent/follow/*`
- `src/client/java/ai/moeru/airicraft/HighlightManager.java`
- `src/client/java/ai/moeru/airicraft/ModBridgeServer.java`

完成定义：

- 所有 verification scenarios PASS
- 真实多人会话里 follow / reply / target loss / disconnect recovery 可观测
- debug 信息足够让 coding agent 定位“planner 问题、goal 问题、还是 behavior 问题”

Single-source-of-truth 检查：

- overlay/highlight 只读 runtime snapshot，不触发状态变更
- recovery 通过更新 goal/state 驱动行为树，不新增第二套 reflex executor

### 20.2 Recommended PR Sequence

如果按小步快跑的方式提交，建议拆成以下 PR：

1. `runtime-skeleton-and-verification`
2. `session-social-event-pipeline`
3. `control-primitives-and-follow-tree`
4. `in-mod-llm-dialogue-pipeline`
5. `hardening-recovery-and-e2e`

每个 PR 的 review 问题固定先问三件事：

1. 这次是否新增了任何绕过行为树的动作写路径？
2. 这次是否新增了任何 bridge/debug 写接口？
3. 这次是否同步交付了 verification scenario 和只读观测？

### 20.3 Immediate Next Coding Slice

如果现在立刻开始实现，建议先做 Work Package 1 的最小闭环：

1. `EmbodiedAgentRuntime` 挂到 `ClientRuntimeController`
2. `VerificationRunner` 跑起一个 `session.basic`
3. `ModBridgeServer` 暴露 `GET /v1/agent/status`、`GET /v1/verification/results`、`POST /v1/verification/run`
4. 把 “只有行为树能调用 controller” 写进代码结构里，而不是只写在文档里

做到这一步后，再继续写 social/session ingest，会轻松很多，因为 runtime 宿主、验证入口、和架构护栏都已经站稳了。
