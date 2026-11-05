import { APP_DEFAULTS } from '../config'
import type { PersonalityProfile, WorldConfig } from '../types'

/**
 * 表单人格仅做裁剪后原样提交：空字段保持为空。
 *
 * 空字段的回落由后端三级解析唯一承担（同名默认居民人格 →
 * virtual-ai-box.runtime.default-personality，见 ADR-0005 决策 4）。
 * 前端不要预填兜底值：预填副本会把后端的第三级回落遮蔽掉，使其永远看不到空字段
 * （历史教训见 ADR-0005 第三次修订——原 normalizeProfile 预填英文兜底即属此类）。
 */
export function normalizeProfile(profile: PersonalityProfile): PersonalityProfile {
  return {
    role: profile.role?.trim() ?? '',
    temperament: profile.temperament?.trim() ?? '',
    speakingStyle: profile.speakingStyle?.trim() ?? '',
    motivations: profile.motivations?.trim() ?? '',
    values: profile.values?.trim() ?? '',
    knowledgeBoundary: profile.knowledgeBoundary?.trim() ?? '',
  }
}

/** 坐标输入允许的最大值（网格尺寸 - 1），尺寸以后端返回为准。 */
export function maxCoordinate(config: WorldConfig | null | undefined, axis: 'width' | 'height'): number {
  const size = config?.[axis] ?? APP_DEFAULTS.worldSize
  return Math.max(0, size - 1)
}
