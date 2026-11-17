package com.own.virtualaibox.behaviordsl;

import java.util.Map;

import com.own.virtualaibox.secd.Instruction;
import com.own.virtualaibox.secd.value.Value;

/**
 * 行为程序：一个 *.lambda 文件编译后的结果。
 *
 * 契约：定义入口程序、顶层环境和相遇处理行为。
 *   #plan()：入口程序（Instruction），直接内联引用自由变量
 *       dx/dy；运行时把 E 种子为 defs + {dx,dy} 后执行；
 *   #defs()：顶层 def 环境（λ 行为求值出的闭包值），运行时作为 E 基底；
 *   #onMeet()：onMeet def 的闭包值（相遇对话行为），无则 null，
 *       回退到内置的 com.own.virtualaibox.mind.DefaultPlanCompiler#compileOnMeet。
 */
public record BehaviorProgram(
        Instruction plan,
        Map<String, Value> defs,
        Value onMeet
) {
}
