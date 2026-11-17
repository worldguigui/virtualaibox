package com.own.virtualaibox.secd;

import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.secd.value.Value;

import java.util.List;

/**
 * 操作符值（OpValue / PartialOpValue）的应用处理器。
 *
 * 默认实现 ArithmeticOpEvaluator 负责纯算术；
 * 世界运行时可注入世界原语处理器（move/speak/remember/ask-llm），这些原语
 * 通过返回 Effect 来表达世界副作用，而不是直接修改 World。
 */
public interface OpEvaluator {

    /**
     * 处理 func（OpValue 或 PartialOpValue）对 arg 的应用。
     *
     * @param state 当前机器状态（允许向 S 栈压入结果，读取 E 环境）
     * @param func  操作符值
     * @param arg   实参
     * @return 本次应用产生的副作用列表（纯算术实现返回空列表）
     */
    List<Effect> apply(MachineState state, Value func, Value arg);
}
