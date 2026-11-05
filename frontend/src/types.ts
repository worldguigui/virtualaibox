/** 与后端 /api/dashboard 返回结构对应的类型定义 */

/** 世界网格和 Agent 数量摘要。 */
export interface WorldInfo {
  name: string
  worldDescription: string
  width: number
  height: number
  agentCount: number
  rules: Record<string, boolean>
  items: WorldItemDefinition[]
  itemInstances: WorldItem[]
  relationships: SocialRelation[]
  messages: TownMessage[]
  quests: TownQuest[]
}

export interface WorldItem {
  id: string
  definitionId: string
  x: number
  y: number
  ownerId: string | null
  quantity: number
}

export interface SocialRelation {
  agentId: string
  otherAgentId: string
  trust: number
  affinity: number
  interactions: number
  status: string
}

export interface TownMessage {
  id: string
  fromAgentId: string
  toAgentId: string
  content: string
  tick: number
  createdAt: string
}

export interface TownQuest {
  id: string
  title: string
  description: string
  assigneeId: string | null
  reward: number
  status: string
}

export interface WorldItemDefinition {
  id: string
  name: string
  type: string
  maxCount: number
  spawnWeight: number
  description: string
}

export interface WorldConfig {
  name: string
  worldDescription: string
  width: number
  height: number
  rules: Record<string, boolean>
  items: WorldItemDefinition[]
  defaultResidents: Array<{ name: string; x: number; y: number }>
}

/** 新居民创建请求。坐标为空时由世界引擎分配默认位置。 */
export interface AgentCreateRequest {
  name?: string
  x?: number
  y?: number
  personality?: PersonalityProfile
}

/** 居民的稳定人格摘要。 */
export interface PersonalityProfile {
  role: string
  temperament: string
  speakingStyle: string
  motivations: string
  values: string
  knowledgeBoundary: string
}

/** 推进世界后返回的完整世界与居民状态。 */
export interface StepResult {
  tick: number
  world: WorldInfo
  agents: Agent[]
}

/** 单个 Agent 的记忆分类统计。 */
export interface MemoryStats {
  agentId: string
  totalCount: number
  observationCount: number
  eventCount: number
  relationshipCount: number
  locationCount: number
  selfCount: number
  learningCount: number
}

/** 仪表盘展示的一条记忆记录。 */
export interface MemoryEntry {
  id: string
  type: string
  content: string
  tags: string[]
  importance: number
  createdAt: string
  lastAccessedAt: string
  accessCount: number
  tick: number
  relatedAgentId: string | null
}

/** 仪表盘展示的 Agent 状态、位置和记忆信息。 */
export interface Agent {
  id: string
  name: string
  personality: PersonalityProfile | null
  x: number
  y: number
  active: boolean
  coins: number
  inventory: string[]
  eventHistorySize: number
  memoryStats: MemoryStats | null
  memorySummary: string
  recentMemories: MemoryEntry[]
  /** 该 Agent 的 SECD 行为执行状态（无运行时为 null）。 */
  mind: MindState | null
}

/** SECD 四寄存器状态摘要，对应 AgentRuntime.mindSummary。 */
export interface MindState {
  status: 'executing' | 'suspended' | 'idle'
  terminated: boolean
  sSize: number
  eSize: number
  cSize: number
  dSize: number
  topValue: string
  currentInstruction: string
  sTop: string[]
  eTop: Array<{ key: string; value: string }>
  cTop: string[]
  /** 上次编译的行为程序（C 栈快照，机器 idle 时仍保留）。 */
  program: string[]
  /** 上次 tick 产出的副作用摘要。 */
  lastActions: string[]
}

/** 收敛/活锁状态快照，对应 ConvergenceMonitor.summary。 */
/** 世界和 Agent 的收敛、卡死及循环检测摘要。 */
export interface ConvergenceInfo {
  worldConverged: boolean
  stableTicks: number
  stuckAgents: string[]
  loopAgents: string[]
  dStackOverflowAgents: string[]
}

/** 事件 detail 为多态字段,不同事件类型结构不同 */
/** 仪表盘展示的领域事件及其多态 detail 数据。 */
export interface DashboardEvent {
  eventId: string
  eventType: string
  description: string
  tick: number
  timestamp: string
  priority: number
  sourceSystem: string
  processed: boolean
  detail: Record<string, unknown> | null
}

/** 服务端运行指标摘要。 */
export interface Metrics {
  eventSubscriberCount: number
  eventHistorySize: number
  agentCount: number
  currentTick: number
  serverTime: string
}

/** 仪表盘接口返回的完整数据。 */
export interface DashboardData {
  tick: number
  world: WorldInfo
  worldConfig: WorldConfig
  agents: Agent[]
  events: DashboardEvent[]
  metrics: Metrics
  capabilities: string[]
  convergence: ConvergenceInfo
}
