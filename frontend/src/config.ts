/**
 * 前端默认配置集中地。
 *
 * 一致性约定：
 * - 世界网格尺寸等运行数据的唯一事实源是后端 /api/dashboard 返回的 world 字段；
 * - 这里的 worldSize 仅在接口尚未返回时作为显示兜底，
 *   必须与后端 application.yml 的 virtual-ai-box.world.width/height 保持一致；
 * - eventLimit / memoryLimit / pollInterval 等纯前端 UI 默认值也集中在这里，
 *   后端的对应默认值见 virtual-ai-box.dashboard 配置段。
 */
export const APP_DEFAULTS = {
  /** 世界网格默认尺寸兜底（与后端 virtual-ai-box.world.width/height 默认值一致） */
  worldSize: 37,
  /** 事件流条数控件初始值 */
  eventLimit: 40,
  /** 居民记忆条数控件初始值 */
  memoryLimit: 8,
  /** 自动运行步进间隔(ms) 初始值 */
  autoSpeed: 650,
  /** 空闲（未自动运行）时仪表盘轮询间隔(ms) */
  pollIntervalMs: 5000,
  /** 世界地图画布基准尺寸（网格在 canvasBase × canvasBase 内等比缩放） */
  canvasBase: 960,
} as const
