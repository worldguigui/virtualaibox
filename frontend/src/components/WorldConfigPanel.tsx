import { useEffect, useState } from 'react'
import type { WorldConfig, WorldItemDefinition } from '../types'
import { BilingualText } from './i18n'

interface WorldConfigPanelProps {
  config: WorldConfig
  onSave: (config: WorldConfig) => Promise<void>
}

const RULE_LABELS: Record<string, string> = {
  movement: '允许移动',
  socialInteraction: '允许社交',
  itemSpawning: '允许物品生成',
  combat: '允许战斗',
  trading: '允许交易',
}

const emptyItem = (): WorldItemDefinition => ({
  id: '', name: '', type: 'resource', maxCount: 1, spawnWeight: 1, description: '',
})

export function WorldConfigPanel({ config, onSave }: WorldConfigPanelProps) {
  const [draft, setDraft] = useState(config)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => setDraft(config), [config])

  const updateItem = (index: number, patch: Partial<WorldItemDefinition>) => {
    setDraft((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item),
    }))
  }

  const save = async () => {
    setError('')
    if (!draft.name.trim() || draft.width < 1 || draft.height < 1) {
      setError('世界名称和尺寸必须有效')
      return
    }
    if (draft.items.some((item) => !item.id.trim() || !item.name.trim())) {
      setError('每个物品都需要填写唯一标识和名称')
      return
    }
    setSaving(true)
    try {
      await onSave(draft)
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel world-config-panel">
      <div className="panel-header">
        <div>
          <BilingualText primary="World Configuration" secondary="世界配置" className="panel-title" />
          <div className="panel-subtitle">修改后立即应用于运行中的世界，现有居民位置会经过边界校验。</div>
        </div>
        <button className="button button-primary" type="button" onClick={() => void save()} disabled={saving}>
          {saving ? '保存中...' : '保存配置'}
        </button>
      </div>

      <div className="form-grid">
        <label className="field"><span>世界名称</span><input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></label>
        <label className="field" style={{ gridColumn: '1 / -1' }}><span>世界描述 / 居民行动提示词</span><textarea rows={4} value={draft.worldDescription} onChange={(event) => setDraft({ ...draft, worldDescription: event.target.value })} /></label>
        <label className="field"><span>宽度</span><input type="number" min="1" max="1000" value={draft.width} onChange={(event) => setDraft({ ...draft, width: Number(event.target.value) })} /></label>
        <label className="field"><span>高度</span><input type="number" min="1" max="1000" value={draft.height} onChange={(event) => setDraft({ ...draft, height: Number(event.target.value) })} /></label>
      </div>

      <div className="config-section">
        <h3>世界规则</h3>
        <div className="rule-grid">
          {Object.entries(draft.rules).map(([key, enabled]) => (
            <label className="checkbox-field" key={key}>
              <input type="checkbox" checked={enabled} onChange={(event) => setDraft({ ...draft, rules: { ...draft.rules, [key]: event.target.checked } })} />
              <span>{RULE_LABELS[key] ?? key}</span>
            </label>
          ))}
        </div>
      </div>

      <div className="config-section">
        <div className="section-row"><h3>物品定义</h3><button className="button" type="button" onClick={() => setDraft({ ...draft, items: [...draft.items, emptyItem()] })}>添加物品</button></div>
        {draft.items.length === 0 && <p className="muted">尚未配置物品。物品定义是规则层，后续可由生成器创建运行时物品。</p>}
        {draft.items.map((item, index) => (
          <div className="item-editor" key={`${item.id}-${index}`}>
            <input aria-label="物品标识" placeholder="id" value={item.id} onChange={(event) => updateItem(index, { id: event.target.value })} />
            <input aria-label="物品名称" placeholder="名称" value={item.name} onChange={(event) => updateItem(index, { name: event.target.value })} />
            <input aria-label="物品类型" placeholder="类型" value={item.type} onChange={(event) => updateItem(index, { type: event.target.value })} />
            <input aria-label="最大数量" type="number" min="0" value={item.maxCount} onChange={(event) => updateItem(index, { maxCount: Number(event.target.value) })} />
            <input aria-label="生成权重" type="number" min="0" value={item.spawnWeight} onChange={(event) => updateItem(index, { spawnWeight: Number(event.target.value) })} />
            <input aria-label="物品描述" placeholder="描述" value={item.description} onChange={(event) => updateItem(index, { description: event.target.value })} />
            <button className="button button-danger" type="button" onClick={() => setDraft({ ...draft, items: draft.items.filter((_, itemIndex) => itemIndex !== index) })}>删除</button>
          </div>
        ))}
      </div>
      {error && <div className="form-error">{error}</div>}
    </section>
  )
}
