package com.own.virtualaibox.effect;

/**
 * 副作用描述（意图），SECD 计算与 World 副作用之间的隔离边界。
 *
 * 每个实现描述一种由行为运行时产生、再交给执行器落地的副作用意图：
 *   SECD = 计算           → 产生 Effect（意图）
 *   EffectExecutor = 真正执行副作用 → 修改 World
 *
 * 具体副作用类型包括移动、发言、记忆和 LLM 请求。
 */
public interface Effect {

    /**
     * 人类可读描述，用于事件日志与前端展示。
     */
    String describe();
}
