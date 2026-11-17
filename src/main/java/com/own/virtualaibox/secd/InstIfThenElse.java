package com.own.virtualaibox.secd;

/**
 * 条件分支指令：弹出 S 顶作为条件，真则压入 then，假则压入 otherwise。
 *
 * 编译形式（docs/secd-fusion-design-frozen.md §17.5）：if c then a else b →
 * InstSeq([c, InstIfThenElse(a, b)])。与 InstSeq / C 栈机制同构：不产生额外
 * D 帧、不残留条件值、不改变 E，与 D 栈中断/恢复天然兼容。
 */
public class InstIfThenElse implements Instruction {
    public final Instruction then;
    public final Instruction otherwise;

    public InstIfThenElse(Instruction then, Instruction otherwise) {
        this.then = then;
        this.otherwise = otherwise;
    }

    @Override
    public String toString() {
        return "(if? then " + then + " else " + otherwise + ")";
    }
}
