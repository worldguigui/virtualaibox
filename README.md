# VirtualAIBox

> 以 **SECD 为行为执行内核、以 World/Event 为运行环境、以 Memory 为认知状态、以 Effect 为副作用边界、以 LLM 为非确定性 Oracle** 的多智能体沙盒实验系统（AI Sandbox / AI Town 类型）。
>
> 项目定位与边界见 [`docs/vision.md`](docs/vision.md)；当前架构 SSOT 见 [`docs/architecture.md`](docs/architecture.md)。

## 技术栈

- **后端**：Java 21 · Spring Boot 3.5.14 · Maven（项目自带 `mvnw.cmd`）
- **行为内核**：自研 SECD 抽象机（`com.own.virtualaibox.secd`）
- **DSL**：ANTLR 4.13.2（`BehaviorDSL.g4` → `.lambda` 行为程序）
- **LLM**：langchain4j（`secrets.yml` 配置 api-key，文件不入库）
- **前端**：React + TypeScript + Vite（`frontend/`，Maven 构建时自动 build 并拷贝到 `target/classes/static`）

## 快速启动

```bash
# 1. 准备密钥：复制/创建 src/main/resources/secrets.yml（被 .gitignore 排除）
#    参考 application.yml 中的占位符，填入 LLM 平台 api-key
secrets:
  apikey:
    deepseek: <你的 key>

# 2. 启动（自动构建前端）
.\mvnw.cmd spring-boot:run

# 3. 访问
http://localhost:8080
```

> 注意：项目要求 **Java 21**（pom 配置 release 21）。命令行环境若为 JDK 17 会编译失败，请使用 JDK 21 或在 IDE 中配置对应 SDK。

## 使用

- 打开首页 dashboard：观察 37×37 网格世界、每 Agent 的 SECD 心智（S/E/C/D 四寄存器卡 + 状态徽标）、记忆、事件流与收敛指示器。
- 行为 DSL：编辑 [`src/main/resources/agents/default.lambda`](src/main/resources/agents/default.lambda)，重启后生效（按 Agent 名/id/`default` 匹配加载）。语法与契约见 [`docs/spec/behavior-dsl.md`](docs/spec/behavior-dsl.md)。

## API（REST）

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

## 实习/开发路线（按阶段推进）

### P0：基线修复
- 修复编译与测试基线
- 稳定 Agent 创建与默认居民初始化
- 统一前后端 API 结构

### P1：居民与世界模型
- 稳定人格模型
- 记忆闭环（记录、检索、访问、遗忘）
- WorldState 与世界规则

### P2：交互与可靠性
- 消息投递
- 社交关系
- LLM Oracle 可靠性、fallback 与审计

### P2（延伸）：持久化与回放
- 事件日志
- tick 快照
- 世界恢复与回放

### P3：居民管理与调试
- 前端居民管理
- 调试控制台
- 运行控制与状态观察

### P4：玩法扩展
- 经济、任务、战斗、进化等系统

> 实施顺序必须遵守：先修正更新逻辑以 `agentId` 为唯一键；再补世界配置层；最后将默认居民生成逻辑改为配置驱动；之后再推进下一阶段功能。

## 配置总览（SSOT：后端 `application.yml`）

> 决策背景与影响见 [`docs/decisions/ADR-0005-config-extraction.md`](docs/decisions/ADR-0005-config-extraction.md)。

| 配置段 | 内容 | 消费方 |
|---|---|---|
| `virtual-ai-box.world` | 世界名、尺寸（37×37）、规则、物品、默认居民（坐标 + 人格） | `WorldConfig` → `WorldEngine` |
| `virtual-ai-box.runtime` | 感知范围(5)、相遇距离(1.5)、事件历史上限(210)、SECD/tick 步数预算、默认计划长度、移动钳制、收敛检测阈值、通用兜底人格 | `RuntimeProperties` → MindController/TickSchedule/EventBus/ConvergenceMonitor 等 |
| `virtual-ai-box.prompt` | 仅短标量（默认/降级理由）；提示词只外置 `intro.txt`/`task.txt` 两个"政策文本"到 `src/main/resources/prompts/decision/`（`PromptRegistry` 启动加载、缺文件即失败、首尾空白自动裁剪）；注入类脚手架段是代码常量，不进配置（ADR-0005 三问检验） | `PromptProperties` + `PromptRegistry` → `LLMBrain` |
| `virtual-ai-box.dashboard` | REST 默认值（事件/记忆条数、轻量快照居民数） | `DashboardProperties` → `DashboardController` |
| `spring.virtual-clock` | 虚拟时钟初始刻度/步长/元时间 | `VirtualClockProperties` |
| `llm.*` + `langchain4j.*` | LLM 平台选择与密钥（`default.yml`/`secrets.yml`） | langchain4j starter |

**前后端一致性约定**：网格尺寸等世界数据以后端 API 返回为准；前端 `frontend/src/config.ts`
只保留显示兜底值（须与后端默认值一致），坐标上限从 `worldConfig` 动态解析；人格空字段回落
由后端三级解析唯一承担，前端只裁剪不预填（避免遮蔽 `runtime.default-personality`）。每个
配置类的 Java 默认值与 yml 保持一致（yml 是主源），一致性由 `ConfigDefaultsConsistencyTest`
（Binder 容器外绑定比对）机制化校验。

## 核心概念速览

| 概念 | 一句话 | 权威文档 |
|---|---|---|
| SECD | 确定性行为执行内核（S 值栈 / E 环境 / C 控制栈 / D 续体） | `docs/spec/agent-runtime.md`、ADR-0001 |
| Effect | SECD 只产出"意图"，EffectExecutor 才执行副作用 | `docs/spec/effect.md`、ADR-0003 |
| LLM Oracle | 只在计划编译期定方向 + ask-llm 原语，不直接改世界 | ADR-0002 |
| Perception | 只读感知通道（范围 5、null→#f、同 tick 快照） | `docs/spec/perception.md` |
| Behavior DSL | `.lambda` 行为程序（def/plan/onMeet） | `docs/spec/behavior-dsl.md` |
| D 栈中断 | Interrupt → Handle → Resume，事件驱动精确恢复 | ADR-0004 |

## 测试

```bash
.\mvnw.cmd test
```

> ⚠ 2026-08-22 曾出现 `AgentRuntimeTest` 3 例失败与 `contextLoads` 失败记录（其后代码有修改、未重跑测试）；当前测试状态未复核，见 `docs/consistency-audit.md` §3.4。

## 文档地图

```text
docs/
├── vision.md              # 为什么做、是什么、边界
├── architecture.md        # 当前架构唯一总览 / SSOT
├── spec/                  # 系统契约（agent-runtime / behavior-dsl / perception / effect）
├── decisions/             # ADR（架构决策记录）：0005=配置参数提取（0001~0004 尚未落盘）
├── changelog.md           # P0-P6 历史演进
├── consistency-audit.md   # 文档-代码一致性审计记录
└── archive/               # 历史文档（只读）：design-v0.3-frozen.md、logs.md
```
