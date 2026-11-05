import { useCallback, useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createAgent, fetchDashboard, stepTick, updateAgent as updateAgentApi, updateWorldConfig as updateWorldConfigApi } from '../api/dashboard'
import { APP_DEFAULTS } from '../config'
import type { AgentCreateRequest, DashboardData, WorldConfig } from '../types'

/** 仪表盘查询、步进和自动运行所需的配置。 */
export interface UseDashboardOptions {
  eventLimit: number
  memoryLimit: number
  /** 自动运行开关 */
  autoRunning: boolean
  /** 自动运行步进间隔(ms) */
  autoSpeed: number
  /** 自动运行中步进失败时回调(用于停止自动运行) */
  onAutoRunError?: () => void
}

/**
 * 仪表盘数据查询 + 手动步进 + 自动运行循环。
 *
 * - 空闲（未自动运行）时每 5s 轮询
 * - eventLimit / memoryLimit 变化会改变 queryKey,自动触发重新拉取
 * - 自动运行使用链式 setTimeout,避免步进耗时超过间隔时重叠调用
 */
export function useDashboard({
  eventLimit,
  memoryLimit,
  autoRunning,
  autoSpeed,
  onAutoRunError,
}: UseDashboardOptions) {
  const queryClient = useQueryClient()
  const [stepping, setStepping] = useState(false)

  const query = useQuery<DashboardData>({
    queryKey: ['dashboard', eventLimit, memoryLimit],
    queryFn: () => fetchDashboard(eventLimit, memoryLimit),
    refetchInterval: autoRunning ? false : APP_DEFAULTS.pollIntervalMs,
    refetchOnWindowFocus: false,
    retry: false,
  })

    /** 手动刷新：使 dashboard 查询失效并重新拉取。 */
  const refresh = useCallback(
    () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    [queryClient],
  )

  /** 单步推进世界并刷新仪表盘数据。 */
  const step = useCallback(async () => {
    setStepping(true)
    try {
      await stepTick()
      await refresh()
    } finally {
      setStepping(false)
    }
  }, [refresh])

  /** 创建居民并使当前看板查询重新获取世界和居民数据。 */
  const addAgent = useCallback(async (input: AgentCreateRequest) => {
    await createAgent(input)
    await refresh()
  }, [refresh])

  /** 更新已存在居民的信息并刷新仪表盘。 */
  const updateAgent = useCallback(async (agentId: string, input: AgentCreateRequest) => {
    await updateAgentApi(agentId, input)
    await refresh()
  }, [refresh])

  const updateWorldConfig = useCallback(async (input: WorldConfig) => {
    await updateWorldConfigApi(input)
    await refresh()
  }, [refresh])

  // 自动运行:每 autoSpeed 毫秒步进一步,当前步完成后才调度下一步
  useEffect(() => {
    if (!autoRunning) return
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | undefined

    const runOnce = async () => {
      try {
        await stepTick()
        await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      } catch (error) {
        console.error('Auto Run 步进失败', error)
        if (!cancelled) onAutoRunError?.()
        return
      }
      if (!cancelled) timer = setTimeout(runOnce, autoSpeed)
    }

    timer = setTimeout(runOnce, autoSpeed)
    return () => {
      cancelled = true
      if (timer) clearTimeout(timer)
    }
  }, [autoRunning, autoSpeed, queryClient, onAutoRunError])

  return {
    data: query.data,
    isFetching: query.isFetching,
    isLoading: query.isLoading,
    isError: query.isError,
    stepping,
    refresh,
    step,
    addAgent,
    updateAgent,
    updateWorldConfig,
  }
}
