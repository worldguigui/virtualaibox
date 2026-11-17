package com.own.virtualaibox.secd.value;

/**
 * 布尔值。toString 为 LISP/Scheme 风格的 #t / #f。
 *
 * 真值规则（docs/secd-fusion-design-frozen.md §17.2）：仅 #f 为假，其余一切值
 * （含 0、""、AgentRef）为真。这使 if 与"无对象 → #f"的
 * 感知原语（如 (closest)）可直接组合。
 */
public class BoolValue implements Value {
    public final boolean val;

    public BoolValue(boolean val) {
        this.val = val;
    }

    // SECD 真值判定：仅 #f 为假，其余皆真。
    public static boolean isTruthy(Value v) {
        return !(v instanceof BoolValue b && !b.val);
    }

    @Override
    public String toString() {
        return val ? "#t" : "#f";
    }
}
