import type { Agent, AgentCreateRequest, DashboardData, DashboardEvent, WorldConfig } from '../types'

export interface PageDataProps {
  data?: DashboardData
  visibleAgents: Agent[]
  visibleEvents: DashboardEvent[]
  focusedAgent: Agent | null
  selectedAgentId: string | null
  onSelectAgent: (id: string) => void
  onCreateAgent: (input: AgentCreateRequest) => Promise<void>
  onUpdateAgent: (agentId: string, input: AgentCreateRequest) => Promise<void>
  onUpdateWorldConfig: (input: WorldConfig) => Promise<void>
  onRefresh: () => void
}

export interface SimulationPageProps extends PageDataProps {
  autoRunning: boolean
  autoSpeed: number
  eventLimit: number
  memoryLimit: number
  filterText: string
  lastSync: string
  onStep: () => void
  onToggleAuto: () => void
  onRefresh: () => void
  onStop: () => void
  onAutoSpeedChange: (value: number) => void
  onEventLimitChange: (value: number) => void
  onMemoryLimitChange: (value: number) => void
  onFilterTextChange: (value: string) => void
}
