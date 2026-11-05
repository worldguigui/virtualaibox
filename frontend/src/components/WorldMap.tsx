import { useEffect, useRef } from 'react'
import type { Agent, WorldInfo, WorldItem } from '../types'
import { BilingualText } from './i18n'

/** 世界地图组件的输入属性。 */
interface WorldMapProps {
  agents: Agent[]
  tick: number
  world?: WorldInfo
  selectedAgentId: string | null
  status: string
}

/** 画布基准尺寸:网格在 960x960 内等比缩放 */
const CANVAS_BASE = 960
const AGENT_COLORS = ['#6d7f5d', '#b66b52', '#c49552', '#8b6f7f', '#78909a', '#9c8064']

/** 绘制世界地图所需的画布和 Agent 参数。 */
interface DrawOptions {
  agents: Agent[]
  tick: number
  selectedAgentId: string | null
  width: number
  height: number
  cellSize: number
  itemInstances: WorldItem[]
}

/** 按给定世界尺寸和 Agent 状态绘制网格地图。 */
function drawWorld(ctx: CanvasRenderingContext2D, { agents, tick, selectedAgentId, width, height, cellSize, itemInstances }: DrawOptions) {
  ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height)

  const gridColor = 'rgba(75, 91, 70, 0.16)'
  const majorGridColor = 'rgba(75, 91, 70, 0.3)'

  for (let x = 0; x <= width; x++) {
    ctx.beginPath()
    ctx.moveTo(x * cellSize + 0.5, 0)
    ctx.lineTo(x * cellSize + 0.5, ctx.canvas.height)
    ctx.strokeStyle = x % 5 === 0 ? majorGridColor : gridColor
    ctx.lineWidth = x % 5 === 0 ? 1.2 : 0.7
    ctx.stroke()
  }

  for (let y = 0; y <= height; y++) {
    ctx.beginPath()
    ctx.moveTo(0, y * cellSize + 0.5)
    ctx.lineTo(ctx.canvas.width, y * cellSize + 0.5)
    ctx.strokeStyle = y % 5 === 0 ? majorGridColor : gridColor
    ctx.lineWidth = y % 5 === 0 ? 1.2 : 0.7
    ctx.stroke()
  }

  itemInstances.filter((item) => !item.ownerId).forEach((item) => {
    const x = item.x * cellSize + cellSize / 2
    const y = item.y * cellSize + cellSize / 2
    ctx.fillStyle = '#d6a84f'
    ctx.fillRect(x - 4, y - 4, 8, 8)
    ctx.strokeStyle = '#694f24'
    ctx.lineWidth = 1
    ctx.strokeRect(x - 4, y - 4, 8, 8)
  })

  const occupied = new Map<string, number>()
  agents.forEach((agent) => {
    const key = `${agent.x},${agent.y}`
    occupied.set(key, (occupied.get(key) ?? 0) + 1)
  })

  agents.forEach((agent, index) => {
    const color = AGENT_COLORS[index % AGENT_COLORS.length]
    const x = agent.x * cellSize + cellSize / 2
    const y = agent.y * cellSize + cellSize / 2
    const stack = occupied.get(`${agent.x},${agent.y}`) ?? 1
    const radius = stack > 1 ? 8 : 7

    // 居民位置标记
    ctx.beginPath()
    ctx.arc(x, y, radius * 2.1, 0, Math.PI * 2)
    ctx.fillStyle = `${color}22`
    ctx.fill()

    // 居民主体
    ctx.beginPath()
    ctx.arc(x, y, radius, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()

    ctx.strokeStyle = 'rgba(255,255,255,0.7)'
    ctx.lineWidth = 1.2
    ctx.stroke()

    // 选中高亮圈
    if (agent.id === selectedAgentId) {
      ctx.beginPath()
      ctx.arc(x, y, radius + 4, 0, Math.PI * 2)
      ctx.strokeStyle = '#ffffff'
      ctx.lineWidth = 1.5
      ctx.stroke()
    }

    // 名字标签
    ctx.fillStyle = '#3f493b'
    ctx.font = '11px "Noto Serif SC", "Songti SC", serif'
    ctx.textAlign = 'center'
    ctx.fillText(agent.name, x, y - 13)

    // 堆叠计数角标
    if (stack > 1) {
      ctx.fillStyle = '#fffaf0'
      ctx.beginPath()
      ctx.arc(x + 14, y - 14, 9, 0, Math.PI * 2)
      ctx.fill()
      ctx.fillStyle = '#3f493b'
      ctx.font = '10px "Noto Serif SC", "Songti SC", serif'
      ctx.fillText(String(stack), x + 14, y - 11)
    }
  })

  // 刻度水印
  ctx.fillStyle = 'rgba(63,73,59,0.65)'
  ctx.font = '12px "Noto Serif SC", "Songti SC", serif'
  ctx.textAlign = 'left'
  ctx.fillText(`第 ${tick} 刻`, 14, 20)
}

/** 渲染 Canvas 世界地图、状态标签和当前 tick。 */
export function WorldMap({ agents, tick, world, selectedAgentId, status }: WorldMapProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const width = world?.width ?? 37
    const height = world?.height ?? 37
    const cellSize = Math.floor(Math.min(CANVAS_BASE / width, CANVAS_BASE / height))
    canvas.width = width * cellSize
    canvas.height = height * cellSize

    drawWorld(ctx, { agents, tick, selectedAgentId, width, height, cellSize, itemInstances: world?.itemInstances ?? [] })
  }, [agents, tick, world, selectedAgentId])

  return (
    <div className="panel">
      <div className="panel-header">
        <div>
          <BilingualText primary="World Map" secondary="世界地图" className="panel-title" />
          <div className="panel-subtitle">37 × 37 网格世界的实时状态</div>
        </div>
          <div className="badge">{status}</div>
      </div>
      <div className="canvas-shell">
        <div className="canvas-wrap">
          <div className="canvas-overlay">
            <span className="overlay-tag"><BilingualText primary={`Step ${tick}`} secondary={`第 ${tick} 刻`} /></span>
            <span className="overlay-tag"><BilingualText primary={`World ${world?.width ?? 37} × ${world?.height ?? 37}`} secondary="世界尺寸" /></span>
            <span className="overlay-tag"><BilingualText primary={`${agents.length} Residents`} secondary={`${agents.length} 位居民`} /></span>
          </div>
          <canvas ref={canvasRef} width={CANVAS_BASE} height={CANVAS_BASE} />
        </div>
        <div className="footer-line">
          <span>网格颜色、Agent 光晕和轨迹都用于强调可读性,后续可切换到资源地图、地形图或视野图层。</span>
        </div>
      </div>
    </div>
  )
}
