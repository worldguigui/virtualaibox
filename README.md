# VirtualAIBox

> 以 **SECD 为行为执行内核、以 World/Event 为运行环境、以 Memory 为认知状态、以 Effect 为副作用边界、以 LLM 为非确定性 Oracle** 的多智能体沙盒实验系统（AI Sandbox / AI Town 类型）。
>
> 项目定位与边界见 [`docs/vision.md`](docs/vision.md)；当前架构 SSOT 见 [`docs/architecture.md`](docs/architecture.md)。

## 一-技术栈

- **后端**：Java 25、Spring Boot 4.1.1、Maven
- **行为内核**：自研 SECD 抽象机
- **DSL**：ANTLR 4.13.2
- **LLM**：langchain4j
- **前端**：React + TypeScript + Vite

## 二-快速启动

### 1.准备密钥
```bash
# 1. 创建 src/main/resources/secrets.yml
#    参考 default.yml 中的占位符，填入 LLM 平台 base-url、model-name、api-key
#    通过provider 字段指定选择的模型 
llm:
  provider: deepseek
  deepseek:
    base-url: https://api.deepseek.com
    model-name: deepseek-chat
    api-key: sk-kan~dao~zhe~ge~key~de~ren~bian~mao~niang~miao
  glm:
    base-url: https://open.bigmodel.cn/api/paas/v4
    model-name: glm-5.3-flash
    api-key: sk-kan~dao~zhe~ge~key~de~ren~bian~mao~niang~miao

# 2. 启动（自动构建前端）
.\mvnw.cmd spring-boot:run

# 3. 访问
http://localhost:8080
```

## 三-API（REST）

> REST：前后端之间用 HTTP 接口通信的常见风格。表中 GET=读取、POST=创建、PUT=更新；`{agentId}` 是路径占位符，调用时替换为真实居民 ID。tick = 虚拟时钟的一个刻度（世界推进的一步）；聚合 = 一次请求把多类数据汇总返回；convergence（收敛）= 世界是否已趋于不再变化的度量。

| 端点 | 说明 |
|---|---|
| `GET /api/dashboard` | dashboard 聚合：tick、world、agents（含每 Agent 的 `mind`/`memoryStats`/`recentMemories` 字段）、events、metrics、convergence |
| `GET /api/dashboard/state` | 世界快照（tick、world、agents） |
| `GET /api/dashboard/events?limit=50` | 事件历史 |
| `GET /api/dashboard/agents?memoryLimit=8` | Agent 列表（含 SECD 心智摘要与记忆） |
| `GET /api/dashboard/metrics` | 指标（订阅者数、事件历史大小、Agent 数、当前 tick） |
| `GET /api/dashboard/step` | 手动推进一个 tick |
| `POST /api/dashboard/addAgent` | 添加 Agent |
| `PUT /api/dashboard/agents/{agentId}` | 按 `agentId` 更新居民名称、坐标和人格 |
| `GET /api/dashboard/world` | 获取世界名称、尺寸、规则、物品定义和默认居民配置 |
| `PUT /api/dashboard/world` | 通过仪表盘应用世界配置；会校验尺寸、物品定义和现有居民边界 |

## 四-配置总览（SSOT：后端 `application.yml`）

> SSOT（Single Source of Truth，单一事实源）：一切配置以后端 `application.yml` 为唯一标准，前端和代码里不再私存副本。"消费方"指读取该配置段的 Java 类。

| 配置段 | 内容 | 消费方 |
|---|---|---|
| `virtual-ai-box.world` | 世界名、尺寸（37×37）、规则、物品、默认居民（坐标 + 人格） | `WorldConfig` → `WorldEngine` |
| `virtual-ai-box.runtime` | 感知范围(5)、相遇距离(1.5)、事件历史上限(210)、SECD/tick 步数预算、SECD trace 开关、默认计划长度、移动钳制、收敛检测阈值、通用兜底人格 | `RuntimeProperties` → MindController/TickSchedule/EventBus/ConvergenceMonitor 等 |
| `virtual-ai-box.prompt` | 仅短标量（默认/降级理由）；提示词只外置 `intro.txt`/`task.txt` 两个"政策文本"到 `src/main/resources/prompts/decision/`（`PromptRegistry` 启动加载、缺文件即失败、首尾空白自动裁剪）；注入类脚手架段是代码常量，不进配置（ADR-0005 三问检验） | `PromptProperties` + `PromptRegistry` → `LLMBrain` |
| `virtual-ai-box.dashboard` | REST 默认值（事件/记忆条数、轻量快照居民数） | `DashboardProperties` → `DashboardController` |
| `spring.virtual-clock` | 虚拟时钟初始刻度/步长/元时间 | `VirtualClockProperties` |
| `llm.*` + `langchain4j.*` | LLM 平台选择与密钥（`default.yml`/`secrets.yml`） | langchain4j starter |

**词汇注**：感知范围 5＝居民能"看到"周围 5 格内环境；相遇距离 1.5＝两居民距离 ≤1.5 格判为"相遇"，触发对话；移动钳制＝单 tick 每根坐标轴最多走 ±1 格，防瞬移；步数预算＝行为机器单 tick 最大执行步数，防无限循环；收敛检测阈值＝判定"世界已稳定"的连续无变化 tick 数门槛；兜底人格＝居民没配人格时的通用默认；政策文本／脚手架段＝前者是可调的指导性提示词（intro/task.txt），后者是和代码强绑定、写死在代码里的提示词片段；元时间＝tick 0 对应的现实时间起点；starter＝Spring Boot 开箱即用的打包依赖，引入即自动装配；Binder 容器外绑定比对＝用 Spring 绑定器把 yml 绑成对象、逐项和 Java 默认值核对，防两处不一致。

## 五-核心概念速览

| 概念 | 描述 | 权威文档 |
|---|---|---|
| SECD抽象机 | 确定性行为执行内核（S 值栈 / E 环境 / C 控制栈 / D 续体） | `docs/spec/agent-runtime.md`、ADR-0001 |
| LLM Oracle | 只在计划编译期定方向 + ask-llm 原语，不直接改世界 | ADR-0002 |
| Effect | SECD 只产出"意图"，EffectExecutor 才执行副作用，也就是让世界实际发生变更 | `docs/spec/effect.md`、ADR-0003 |
| Perception | 只读感知通道（范围 5、null→#f、同 tick 快照） | `docs/spec/perception.md` |
| Behavior DSL | `.lambda` 行为程序（def/plan/onMeet） | `docs/spec/behavior-dsl.md` |
| D 栈中断 | Interrupt → Handle → Resume，事件驱动精确恢复 | ADR-0004 |
| 副作用| SECD 产出的“意图”经过 EffectExecutor 执行后产生的实际影响 | `docs/spec/effect.md` |
| 意图| 大模型产出的动作指令，经过 SECD 执行后产生实际影响 | `docs/spec/effect.md` |

## 六-文档概述

```text
docs/
├── vision.md              # 为什么做、是什么、边界
├── architecture.md        # 当前架构唯一总览 / SSOT
├── spec/                  # 系统契约（agent-runtime / behavior-dsl / perception / effect）
├── decisions/             # ADR（架构决策记录）：0001=SECD 内核、0002=LLM Oracle、0003=Effect 隔离、0004=D 栈中断、0005=配置提取、0006=SECD 可观测性与原语补全
├── changelog.md           # P0-P6 历史演进
├── consistency-audit.md   # 文档-代码一致性审计记录
└── archive/               # 历史文档（只读）：design-v0.3-frozen.md、logs.md
```

## 七-版本记录
> 更新规则：**新版本条目插入本列表顶部**（最新在上），历史条目只追加不改。

### vab2.1.9 2026-11-18

1. 改动了部分文档的注释

### vab2.1.8 2026-11-17

1. 单行注释用//，JavaDoc注释去掉html标签，按照该规则，处理了注释，增加可读性。

### vab2.1.7 2026-11-16
1. 更新adr0005和adr0006
2. 更新部分代码的注释
3. 运行时原语集合增加 pred/zerop/sub/div

### vab2.1.6 2026-11-15
1. 增加兼容多平台的图标文件，ico(16px,32px,48px)、png(16px,32px,180px,192px,512px)、svg(任意缩放)
2. 更新adr0003和adr0004。

### vab2.1.5 2026-11-13
1. 补充版本迭代记录
2. 修改了readme文档的部分结构，对文档内容进行瘦身
3. 版本升级：SpringBoot升级到4.1.1，Java升级到25，进行了兼容性处理，详见adr0007。
4. 创建了adr文档模板

### vab2.1.4 2026-11-12
1. 更新adr0001和adr0002。
2. 捕获大模型调用异常，避免一个异常导致整个tick被终止

### vab2.1.3 2026-11-09
1. 文档更新
2. secd: 回迁 trace/类型化异常/算术原语，坏程序降级不再终止 tick（ADR-0006）
3. 等待验收

### vab2.1.2 2026-11-06
1. 文档更新

### vab2.1.1 2026-11-06
1. 项目管理进入到更清晰的版本管理中
2. 处理合并后冲突

### vab2.1.0 2026-11-05 进入正式版本管理前的代码变更总结
1. SECD 内核移植，新增 `secd/` 包——SECD 抽象机、指令集与域值系统（AgentRef、Direction 等域内类型），为 Agent 增加行为执行流；新增原语：括号、东西南北
2. 原生 JS 单文件 `index.html` 重构为 React 项目，基于 antd 改造事件流展示，整体风格确认为仿纸风格
3. 删除旧有的前端接口，完善前后端接口；新增创建居民、修改居民信息、修改世界信息
4. 修复对外暴露 apikey 的 bug；
5. 大模型配置增加默认；
6. langchain4j 升级到 1.19.5，引入langchain4j 官方 BOM：统一管理所有模块版本；
7. 配置信息抽取到 yml，docs 文档冻结旧稿并入版本控制

### vab2.0 2026-08-01 事件系统、记忆系统、可视化界面
1. 新增 事件系统相关类 `DomainEvent` / `EventBus` / `EventListener` 发布-订阅总线及 5 类事件（TickStarted / TickEnded / AgentDecided / AgentMet / AgentMoved），`TickSchedule` 在 tick 各阶段发布事件
2. 新增 记忆系统相关类 `AgentMemory` / `MemoryEntry` / `MemoryStore`（默认 InMemoryMemoryStore）；`Agent` 实现事件监听，相遇/观察自动写入记忆
3. LLM 提示词注入记忆摘要（“基于过去的经历决策”），世界网格由 100×100 调整为 37×37
4. 新增 `DashboardController`（`/api/dashboard` 聚合：state / events / agents / metrics），`index.html` 重写为可视化仪表盘

### vab1.0 2026-07-19 最小完整的世界模拟闭环
1. 项目初始化，Spring Boot 项目骨架。
2. application.yml 配置读取、虚拟时钟 `VirtualClock`（步进与暂停逻辑）
3. 模拟闭环，`WorldEngine` 驱动 → `VirtualClock` 推进 tick → `TickSchedule` 协调决策与执行 → `ActionExecutor` 执行移动
4. LLM 接入，引入 langchain4j（1.0.1-beta6，含 webflux/reactor 流式调用）；`LLMBrain` 全量加载世界状态生成移动决策，调用失败回落随机策略
5. 领域模型创建，World / Agent / AgentState / WorldState / MoveAction
6. 创建原生 JS 单文件 `index.html` 用于调试；API 仅包含基础功能 `/test`、`/state`、`/step`