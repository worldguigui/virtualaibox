package com.own.virtualaibox.secd.value;

/**
 * 整数值。
 * 移植自 lambdaexpr 的 IntValue。
 */
public class IntValue implements Value {
    public final int val;

    public IntValue(int val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return String.valueOf(val);
    }
}
