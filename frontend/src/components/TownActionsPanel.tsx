import { useState } from 'react'
import { createTownQuest, sendTownMessage, spawnTownItem } from '../api/dashboard'
import type { Agent, WorldInfo } from '../types'
import { BilingualText } from './i18n'

interface TownActionsPanelProps {
  agents: Agent[]
  world?: WorldInfo
  onRefresh: () => void
}

export function TownActionsPanel({ agents, world, onRefresh }: TownActionsPanelProps) {
  const [fromAgentId, setFromAgentId] = useState(agents[0]?.id ?? '')
  const [toAgentId, setToAgentId] = useState(agents[1]?.id ?? agents[0]?.id ?? '')
  const [message, setMessage] = useState('')
  const [questTitle, setQuestTitle] = useState('')
  const [questDescription, setQuestDescription] = useState('')
  const [feedback, setFeedback] = useState('')

  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
      setFeedback('操作已应用')
      onRefresh()
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : '操作失败')
    }
  }

  return (
    <section className="panel">
      <div className="panel-header">
        <div><BilingualText primary="Town Actions" secondary="小镇行动" className="panel-title" /><div className="panel-subtitle">通过仪表盘触发可审计的世界行为</div></div>
        <div className="badge">{world?.itemInstances?.length ?? 0} items · {world?.quests?.length ?? 0} quests</div>
      </div>
      <div className="form-grid">
        <div className="config-section">
          <h3>发送消息</h3>
          <select value={fromAgentId} onChange={(event) => setFromAgentId(event.target.value)}>{agents.map((agent) => <option key={agent.id} value={agent.id}>{agent.name}</option>)}</select>
          <select value={toAgentId} onChange={(event) => setToAgentId(event.target.value)}>{agents.map((agent) => <option key={agent.id} value={agent.id}>{agent.name}</option>)}</select>
          <input placeholder="消息内容" value={message} onChange={(event) => setMessage(event.target.value)} />
          <button className="button button-primary" type="button" onClick={() => void run(() => sendTownMessage({ fromAgentId, toAgentId, content: message }))}>发送</button>
        </div>
        <div className="config-section">
          <h3>发布任务</h3>
          <input placeholder="任务标题" value={questTitle} onChange={(event) => setQuestTitle(event.target.value)} />
          <input placeholder="任务描述" value={questDescription} onChange={(event) => setQuestDescription(event.target.value)} />
          <button className="button button-primary" type="button" onClick={() => void run(() => createTownQuest({ title: questTitle, description: questDescription, reward: 25 }))}>创建任务</button>
        </div>
        <div className="config-section">
          <h3>生成物品</h3>
          <div className="muted">在世界配置中先定义物品，再使用 API/控制台生成实例。</div>
          <div className="muted">运行时物品：{world?.itemInstances?.length ?? 0}</div>
          {world?.items?.[0] && <button className="button" type="button" onClick={() => void run(() => spawnTownItem({ definitionId: world.items[0].id, x: agents[0]?.x ?? 0, y: agents[0]?.y ?? 0, quantity: 1 }))}>在首位居民处生成</button>}
        </div>
      </div>
      {feedback && <div className="form-error">{feedback}</div>}
    </section>
  )
}
