package com.own.virtualaibox.secd;

import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.secd.value.Value;

import java.util.List;

/**
 * 一次归约运行的结果：最终值 + 运行过程中产生的副作用 + 是否发散。
 *
 * @param result            最终值（C/D 清空后 S 栈顶；发散时为 InfiniteValue）
 * @param effects           运行期间产生的副作用（纯算术为空）
 * @param infiniteReduction 是否检测到无限归约（活锁/发散）
 */
public record Reduction(Value result, List<Effect> effects, boolean infiniteReduction) {
}
