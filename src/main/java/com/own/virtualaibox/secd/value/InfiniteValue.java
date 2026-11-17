package com.own.virtualaibox.secd.value;

/**
 * 无限归约的标记结果（活锁/发散检测产物）。
 * 移植自 lambdaexpr 的 InfiniteValue。
 */
public class InfiniteValue implements Value {
    private final String message;

    public InfiniteValue(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "inf (" + message + ")";
    }
}
