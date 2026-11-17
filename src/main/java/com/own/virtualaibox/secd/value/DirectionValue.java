package com.own.virtualaibox.secd.value;

/**
 * 方向常量值：north/east/south/west。
 *
 * 方向→delta 的映射在 WorldOpEvaluator（DSL 原语层）完成，
 * 统一产出 MoveEffect(deltaX, deltaY)，Effect 层不新增方向重载（docs §17.4）。
 */

public class DirectionValue implements Value {
    public final String dir;

    public DirectionValue(String dir) {
        this.dir = dir;
    }

    @Override
    public String toString() {
        return dir;
    }
}
