package com.own.virtualaibox.secd.value;

/**
 * 未绑定变量的值（环境中无绑定时压入）。
 * 移植自 lambdaexpr 的 VarValue。
 */
public class VarValue implements Value {
    public final String var;

    public VarValue(String var) {
        this.var = var;
    }

    @Override
    public String toString() {
        return var;
    }
}
