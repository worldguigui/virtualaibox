package com.own.virtualaibox.secd.value;

/**
 * 柯里化原语中间态（如 (add 10) 尚未应用第二个参数）。
 * 移植自 lambdaexpr 的 PartialOpValue。
 */
public class PartialOpValue implements Value {
    public final String op;
    public final Value firstArg;

    public PartialOpValue(String op, Value firstArg) {
        this.op = op;
        this.firstArg = firstArg;
    }

    @Override
    public String toString() {
        return "(" + op + " " + firstArg + ")";
    }
}
