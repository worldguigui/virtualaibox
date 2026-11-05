import { useEffect, useState, type FormEvent } from 'react'
import type { Agent, AgentCreateRequest, PersonalityProfile, WorldConfig } from '../types'
import { maxCoordinate, normalizeProfile } from '../lib/personality'
import { BilingualText } from './i18n'

interface AgentEditorModalProps {
  agent: Agent | null
  onClose: () => void
  onSave: (agentId: string, input: AgentCreateRequest) => Promise<void>
  /** 后端世界配置：人格兜底与坐标上限以它为准（缺省时用前端默认值）。 */
  worldConfig?: WorldConfig | null
}

const createBlankProfile = (): PersonalityProfile => ({
  role: '',
  temperament: '',
  speakingStyle: '',
  motivations: '',
  values: '',
  knowledgeBoundary: '',
})

export function AgentEditorModal({ agent, onClose, onSave, worldConfig }: AgentEditorModalProps) {
  const [name, setName] = useState('')
  const [x, setX] = useState('')
  const [y, setY] = useState('')
  const [profile, setProfile] = useState<PersonalityProfile>(createBlankProfile())
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!agent) return
    setName(agent.name ?? '')
    setX(String(agent.x ?? ''))
    setY(String(agent.y ?? ''))
    setProfile(agent.personality ?? createBlankProfile())
  }, [agent])

  if (!agent) return null

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')

    try {
      await onSave(agent.id, {
        name: name.trim() || undefined,
        x: x === '' ? undefined : Number(x),
        y: y === '' ? undefined : Number(y),
        personality: Object.values(profile).some((value) => value && value.trim())
          ? normalizeProfile(profile)
          : undefined,
      })
      onClose()
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '更新居民失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="resident-modal-overlay" onClick={onClose}>
      <div className="resident-modal" onClick={(event) => event.stopPropagation()}>
        <div className="panel-header">
          <BilingualText primary="Edit Resident" secondary="修改居民" className="panel-title" />
          <button className="ghost" type="button" onClick={onClose}>关闭</button>
        </div>

        <form className="resident-form" onSubmit={submit}>
          <label className="full-width">
            <BilingualText primary="Name" secondary="姓名" />
            <input value={name} onChange={(event) => setName(event.target.value)} />
          </label>

          <label>
            <BilingualText primary="X" secondary="横坐标" />
            <input type="number" min="0" max={maxCoordinate(worldConfig, 'width')} value={x} onChange={(event) => setX(event.target.value)} />
          </label>

          <label>
            <BilingualText primary="Y" secondary="纵坐标" />
            <input type="number" min="0" max={maxCoordinate(worldConfig, 'height')} value={y} onChange={(event) => setY(event.target.value)} />
          </label>

          <label className="full-width">
            <BilingualText primary="Role" secondary="角色" />
            <input value={profile.role ?? ''} onChange={(event) => setProfile((current) => ({ ...current, role: event.target.value }))} />
          </label>

          <label className="full-width">
            <BilingualText primary="Temperament" secondary="性情" />
            <input value={profile.temperament ?? ''} onChange={(event) => setProfile((current) => ({ ...current, temperament: event.target.value }))} />
          </label>

          <label className="full-width">
            <BilingualText primary="Speaking Style" secondary="表达方式" />
            <textarea value={profile.speakingStyle ?? ''} onChange={(event) => setProfile((current) => ({ ...current, speakingStyle: event.target.value }))} />
          </label>

          <label className="full-width">
            <BilingualText primary="Motivations" secondary="动机" />
            <textarea value={profile.motivations ?? ''} onChange={(event) => setProfile((current) => ({ ...current, motivations: event.target.value }))} />
          </label>

          <label className="full-width">
            <BilingualText primary="Values" secondary="价值" />
            <textarea value={profile.values ?? ''} onChange={(event) => setProfile((current) => ({ ...current, values: event.target.value }))} />
          </label>

          <label className="full-width">
            <BilingualText primary="Knowledge Boundary" secondary="知识边界" />
            <textarea value={profile.knowledgeBoundary ?? ''} onChange={(event) => setProfile((current) => ({ ...current, knowledgeBoundary: event.target.value }))} />
          </label>

          <div className="modal-actions full-width">
            <button className="ghost" type="button" onClick={onClose}>
              <BilingualText primary="Cancel" secondary="取消" />
            </button>
            <button className="primary" type="submit" disabled={submitting}>
              <BilingualText primary={submitting ? 'Saving' : 'Save'} secondary={submitting ? '保存中' : '保存修改'} />
            </button>
          </div>
        </form>

        {error ? <div className="form-error">{error}</div> : null}
      </div>
    </div>
  )
}
