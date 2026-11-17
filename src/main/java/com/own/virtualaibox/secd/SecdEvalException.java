package com.own.virtualaibox.secd;

/**
 * SECD 求值期错误：未知原语、类型不符、非函数应用、除零等。
 *
 * lambdaexpr 类型化异常（TypeException/NotAFunctionException）的
 * 轻量回迁（ADR-0006）：收敛为单一类型，但保留其"富消息"风格——消息包含原语名与
 * 实际操作数（如 div 除以零: 1 / 0），是调试 LLM 编译的行为程序的第一现场。
 *
 * 与 IllegalStateException 的分诊：本异常表达运行期数据不确定性
 * （LLM/编译产物可能给出机器不认识的程序），调用方（AgentRuntime）应 fail-soft——
 * 降级单个 Agent 的本 tick，不终止整个 tick；装配/生命周期错误（如感知未注入）仍用
 * IllegalStateException fail-fast（见 WorldOpEvaluator.perceptionRequired）。
 */
public class SecdEvalException extends RuntimeException {

    public SecdEvalException(String message) {
        super(message);
    }
}
