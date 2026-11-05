import { useCallback, useEffect, useMemo, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { APP_DEFAULTS } from './config'
import { useDashboard } from './hooks/useDashboard'
import { applyFilter, agentMatchesFilter, eventMatchesFilter, formatTime } from './lib/format'
import { AppShell } from './components/AppShell'
import { OverviewPage } from './pages/OverviewPage'
import { WorldPage } from './pages/WorldPage'
import { AgentsPage } from './pages/AgentsPage'
import { EventsPage } from './pages/EventsPage'
import { RuntimePage } from './pages/RuntimePage'

/** 根据查询和步进状态生成界面状态徽标。 */
function deriveStatus(stepping: boolean, isError: boolean, isLoading: boolean, isFetching: boolean, hasData: boolean) {
  if (stepping) return '推进中'
  if (isError) return '离线'
  if (isLoading) return '加载中'
  if (isFetching) return '同步中'
  if (hasData) return '运行中'
  return '空闲'
}

/** 渲染仪表盘主界面并协调筛选、选中和模拟控制状态。 */
export default function App() {
  // 全局模拟控制状态由应用层持有，保证不同页面切换时不中断运行。
  const [autoRunning, setAutoRunning] = useState(false)
  const [autoSpeed, setAutoSpeed] = useState<number>(APP_DEFAULTS.autoSpeed)
  const [eventLimit, setEventLimit] = useState<number>(APP_DEFAULTS.eventLimit)
  const [memoryLimit, setMemoryLimit] = useState<number>(APP_DEFAULTS.memoryLimit)
  const [filterText, setFilterText] = useState('')
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null)

  const onAutoRunError = useCallback(() => setAutoRunning(false), [])

  const { data, isFetching, isLoading, isError, stepping, step, refresh, addAgent, updateAgent, updateWorldConfig } = useDashboard({
    eventLimit,
    memoryLimit,
    autoRunning,
    autoSpeed,
    onAutoRunError,
  })

  const toggleAutoRun = useCallback(() => setAutoRunning((running) => !running), [])
  const stopAutoRun = useCallback(() => setAutoRunning(false), [])

  // 文本过滤覆盖 Agent 和事件的主要可读字段。
  const text = filterText.trim().toLowerCase()
  const visibleAgents = useMemo(
    () => applyFilter(data?.agents ?? [], (agent) => agentMatchesFilter(agent, text)),
    [data, text],
  )
  const visibleEvents = useMemo(
    () => applyFilter(data?.events ?? [], (event) => eventMatchesFilter(event, text)),
    [data, text],
  )

  // 当前聚焦的 Agent 优先使用用户选择项，否则回退到第一个可见 Agent。
  const focusedAgent = useMemo(
    () => visibleAgents.find((agent) => agent.id === selectedAgentId) ?? visibleAgents[0] ?? null,
    [visibleAgents, selectedAgentId],
  )

  // 在所有页面保留单步、自动运行和刷新的键盘入口。
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.code === 'Space') {
        event.preventDefault()
        void step()
      } else if (event.code === 'KeyA') {
        toggleAutoRun()
      } else if (event.code === 'KeyR') {
        void refresh()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [step, toggleAutoRun, refresh])

  const lastSync = data ? `最近同步：${formatTime(data.metrics.serverTime)}` : '最近同步：-'
  const status = deriveStatus(stepping, isError, isLoading, isFetching, Boolean(data))

  const pageData = {
    data,
    visibleAgents,
    visibleEvents,
    focusedAgent,
    selectedAgentId,
    onSelectAgent: setSelectedAgentId,
    onCreateAgent: addAgent,
    onUpdateAgent: updateAgent,
    onUpdateWorldConfig: updateWorldConfig,
  }
  const simulationProps = {
    ...pageData,
    autoRunning,
    autoSpeed,
    eventLimit,
    memoryLimit,
    filterText,
    lastSync,
    onStep: () => void step(),
    onToggleAuto: toggleAutoRun,
    onRefresh: () => void refresh(),
    onStop: stopAutoRun,
    onAutoSpeedChange: setAutoSpeed,
    onEventLimitChange: setEventLimit,
    onMemoryLimitChange: setMemoryLimit,
    onFilterTextChange: setFilterText,
  }

  return (
    <AppShell data={data} status={status} onRefresh={() => void refresh()}>
      <Routes>
        <Route path="/" element={<OverviewPage {...simulationProps} />} />
        <Route path="/world" element={<WorldPage {...simulationProps} />} />
        <Route path="/agents" element={<AgentsPage {...pageData} />} />
        <Route path="/events" element={<EventsPage {...pageData} />} />
        <Route path="/runtime" element={<RuntimePage {...pageData} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  )
}
