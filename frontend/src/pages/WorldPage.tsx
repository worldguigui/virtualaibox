import { SimulationControls } from '../components/SimulationControls'
import { WorldMap } from '../components/WorldMap'
import type { SimulationPageProps } from './pageTypes'
import { BilingualText } from '../components/i18n'
import { WorldConfigPanel } from '../components/WorldConfigPanel'
import { APP_DEFAULTS } from '../config'

/** 提供世界地图与模拟控制的专注视图，便于后续加入地形、资源和视野图层。 */
export function WorldPage(props: SimulationPageProps) {
  const { data, visibleAgents, selectedAgentId, onUpdateWorldConfig } = props

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <span className="section-kicker">空间观察</span>
          <h1><BilingualText primary="World Map" secondary="世界地图" /></h1>
          <p>观察小镇空间、居民位置和每个刻度的移动结果。</p>
        </div>
        <div className="heading-stat"><strong>{data?.world.width ?? APP_DEFAULTS.worldSize} × {data?.world.height ?? APP_DEFAULTS.worldSize}</strong><BilingualText primary="world grid" secondary="世界网格" /></div>
      </section>
      <section className="panel"><SimulationControls {...props} /></section>
      <section className="single-stage">
        <WorldMap agents={visibleAgents} tick={data?.tick ?? 0} world={data?.world} selectedAgentId={selectedAgentId} status="运行中" />
      </section>
      {data?.worldConfig && <WorldConfigPanel config={data.worldConfig} onSave={onUpdateWorldConfig} />}
    </div>
  )
}
