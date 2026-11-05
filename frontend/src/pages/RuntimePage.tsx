import { MindViewer } from '../components/MindViewer'
import { StatusPanel } from '../components/StatusPanel'
import type { PageDataProps } from './pageTypes'
import { BilingualText } from '../components/i18n'
import { TownActionsPanel } from '../components/TownActionsPanel'

/** 汇总执行内核、收敛检测和当前 Agent 心智状态，作为运行诊断入口。 */
export function RuntimePage({ data, visibleAgents, focusedAgent, onRefresh }: PageDataProps) {
  const memoryCoverage = `${visibleAgents.filter((agent) => agent.memoryStats).length}/${visibleAgents.length || 1} agents`

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <span className="section-kicker">执行与诊断</span>
          <h1><BilingualText primary="Runtime" secondary="运行内核" /></h1>
          <p>检查 SECD 执行状态、世界收敛情况和当前系统能力。</p>
        </div>
        <div className="heading-stat"><strong>SECD</strong><BilingualText primary="execution core" secondary="行为执行核心" /></div>
      </section>
      <section className="runtime-grid">
        <StatusPanel world={data?.world} metrics={data?.metrics} memoryCoverage={memoryCoverage} convergence={data?.convergence} />
        <MindViewer agentName={focusedAgent?.name ?? null} agentId={focusedAgent?.id ?? null} mind={focusedAgent?.mind ?? null} tick={data?.tick ?? 0} />
      </section>
      <TownActionsPanel agents={visibleAgents} world={data?.world} onRefresh={onRefresh} />
      <section className="capability-panel panel">
        <div className="panel-header"><div><BilingualText primary="Runtime Capabilities" secondary="运行能力" className="panel-title" /><div className="panel-subtitle">当前后端已声明的能力入口</div></div></div>
        <div className="capability-list">
          {(data?.capabilities ?? []).map((capability) => <span className="capability-chip" key={capability}>{capability}</span>)}
        </div>
      </section>
    </div>
  )
}
