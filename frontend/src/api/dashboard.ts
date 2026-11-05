import { request } from './client'
import type { Agent, AgentCreateRequest, DashboardData, StepResult, WorldConfig, WorldInfo, WorldItem, SocialRelation, TownMessage, TownQuest } from '../types'

/** 拉取仪表盘主数据。 */
export function fetchDashboard(eventLimit: number, memoryLimit: number) {
  const query = new URLSearchParams({ eventLimit: String(eventLimit), memoryLimit: String(memoryLimit) })
  return request<DashboardData>(`/api/dashboard?${query}`)
}

/** 请求服务端推进一个世界 tick。 */
export function stepTick() {
  return request<StepResult>('/api/dashboard/step')
}

/** 创建居民并返回创建后的世界与居民列表。 */
export interface CreateAgentResult {
  tick: number
  agent: Agent
  world: WorldInfo
  agents: Agent[]
}

export function createAgent(input: AgentCreateRequest) {
  return request<CreateAgentResult>('/api/dashboard/addAgent', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateAgent(agentId: string, input: AgentCreateRequest) {
  return request<CreateAgentResult>(`/api/dashboard/agents/${encodeURIComponent(agentId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function fetchWorldConfig() {
  return request<WorldConfig>('/api/dashboard/world')
}

export function updateWorldConfig(input: WorldConfig) {
  return request<{ tick: number; world: WorldInfo; worldConfig: WorldConfig; agents: Agent[] }>('/api/dashboard/world', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export interface TownRuntime {
  items: WorldItem[]
  relationships: SocialRelation[]
  messages: TownMessage[]
  quests: TownQuest[]
  agents: Agent[]
}

export function fetchTownRuntime() {
  return request<TownRuntime>('/api/dashboard/town/runtime')
}

export function spawnTownItem(input: { definitionId: string; x: number; y: number; quantity: number }) {
  return request<{ item: WorldItem; runtime: TownRuntime }>('/api/dashboard/town/items/spawn', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}

export function sendTownMessage(input: { fromAgentId: string; toAgentId: string; content: string }) {
  return request<{ message: TownMessage; runtime: TownRuntime }>('/api/dashboard/town/messages', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}

export function createTownQuest(input: { title: string; description: string; reward: number }) {
  return request<{ quest: TownQuest; runtime: TownRuntime }>('/api/dashboard/town/quests', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}
