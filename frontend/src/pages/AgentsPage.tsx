import { useState } from 'react'
import { AgentCreatePanel } from '../components/AgentCreatePanel'
import { AgentEditorModal } from '../components/AgentEditorModal'
import { AgentList } from '../components/AgentList'
import { MindViewer } from '../components/MindViewer'
import type { Agent } from '../types'
import type { PageDataProps } from './pageTypes'
import { BilingualText } from '../components/i18n'

/** 集中查看 Agent 状态、记忆和 SECD 心智快照。 */
export function AgentsPage({ data, visibleAgents, focusedAgent, selectedAgentId, onSelectAgent, onCreateAgent, onUpdateAgent }: PageDataProps) {
  const [editingAgent, setEditingAgent] = useState<Agent | null>(null)

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <span className="section-kicker">居民与记忆</span>
          <h1><BilingualText primary="Residents" secondary="居民社区" /></h1>
          <p>每位居民都有自己的位置、记忆、行为程序和实时心智状态。</p>
        </div>
        <div className="heading-stat"><strong>{visibleAgents.length}</strong><BilingualText primary="visible residents" secondary="当前可见居民" /></div>
      </section>
      <AgentCreatePanel onCreate={onCreateAgent} worldConfig={data?.worldConfig ?? null} />
      <section className="agents-layout">
        <AgentList
          agents={visibleAgents}
          tick={data?.tick ?? 0}
          selectedAgentId={selectedAgentId}
          onSelectAgent={onSelectAgent}
          onEditAgent={setEditingAgent}
        />
        <MindViewer agentName={focusedAgent?.name ?? null} agentId={focusedAgent?.id ?? null} mind={focusedAgent?.mind ?? null} tick={data?.tick ?? 0} />
      </section>

      <AgentEditorModal
        agent={editingAgent}
        worldConfig={data?.worldConfig ?? null}
        onClose={() => setEditingAgent(null)}
        onSave={async (agentId, input) => {
          await onUpdateAgent(agentId, input)
          setEditingAgent(null)
        }}
      />
    </div>
  )
}
