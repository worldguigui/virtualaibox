package com.own.virtualaibox.secd;

import com.own.virtualaibox.secd.value.AgentRefValue;
import com.own.virtualaibox.secd.value.BoolValue;
import com.own.virtualaibox.secd.value.ClosureValue;
import com.own.virtualaibox.secd.value.DirectionValue;
import com.own.virtualaibox.secd.value.InfiniteValue;
import com.own.virtualaibox.secd.value.IntValue;
import com.own.virtualaibox.secd.value.OpValue;
import com.own.virtualaibox.secd.value.StringValue;
import com.own.virtualaibox.secd.value.Value;
import com.own.virtualaibox.secd.value.VarValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证程序化构造的 λ 表达式和 SECD 归约正确性。
 *
 * 用例对应 lambdaexpr LambdaExprTest 中的代表性场景：
 * 算术柯里化、一元原语、闭包应用、闭包环境捕获、纯 λ 丘奇数、发散检测。
 * 不依赖 ANTLR —— 指令树直接用 InstApp/InstLam/... 手工构造。
 */
class SECDTest {

    // ---------- 指令构造助手 ----------

    private static InstConst c(int v) {
        return new InstConst(new IntValue(v));
    }

    private static InstConst op(String name) {
        return new InstConst(new OpValue(name));
    }

    private static InstVar v(String name) {
        return new InstVar(name);
    }

    private static InstLam lam(String arg, Instruction body) {
        return new InstLam(arg, body);
    }

    private static InstApp app(Instruction rator, Instruction rand) {
        return new InstApp(rator, rand);
    }

    private static Reduction eval(Instruction program) {
        return new SECD().run(new MachineState(), program);
    }

    private static int intResult(Reduction r) {
        assertInstanceOf(IntValue.class, r.result(), "expected IntValue but got: " + r.result());
        return ((IntValue) r.result()).val;
    }

    // ---------- 用例 ----------

    @Test
    void addCurrying() {
        // ((add 10) 20) -> 30
        Reduction r = eval(app(app(op("add"), c(10)), c(20)));
        assertEquals(30, intResult(r));
        assertTrue(r.effects().isEmpty(), "纯算术不应产生副作用");
    }

    @Test
    void mulCurrying() {
        // ((mul 3) 4) -> 12
        assertEquals(12, intResult(eval(app(app(op("mul"), c(3)), c(4)))));
    }

    @Test
    void unarySqr() {
        // (sqr 10) -> 100
        assertEquals(100, intResult(eval(app(op("sqr"), c(10)))));
    }

    @Test
    void unarySucc() {
        // (succ 10) -> 11
        assertEquals(11, intResult(eval(app(op("succ"), c(10)))));
    }

    @Test
    void closureApply() {
        // ((lambda x.((add x) 5)) 10) -> 15
        assertEquals(15, intResult(eval(app(lam("x", app(app(op("add"), v("x")), c(5))), c(10)))));
    }

    @Test
    void nestedClosures() {
        // ((lambda a.((lambda b.((add ((mul a) b)) ((mul b) a))) ((add 5) 7))) 2)
        //   a=2, b=12 -> add(mul 2 12)(mul 12 2) = 24 + 24 = 48
        Instruction inner = app(
                app(op("add"),
                        app(app(op("mul"), v("a")), v("b"))),
                app(app(op("mul"), v("b")), v("a")));
        Instruction expr = app(
                lam("a", app(lam("b", inner), app(app(op("add"), c(5)), c(7)))),
                c(2));
        assertEquals(48, intResult(eval(expr)));
    }

    @Test
    void closureCapturesEnv() {
        // (((lambda y.(lambda x.(add x y))) 3) 4) -> add 4 3 = 7
        // 验证内层闭包捕获外层 y 的绑定
        Instruction expr = app(
                app(lam("y", lam("x", app(app(op("add"), v("x")), v("y")))), c(3)),
                c(4));
        assertEquals(7, intResult(eval(expr)));
    }

    @Test
    void identityOnVar() {
        // ((lambda x.x) y) -> y（未绑定变量返回 VarValue）
        Reduction r = eval(app(lam("x", v("x")), v("y")));
        assertInstanceOf(VarValue.class, r.result());
        assertEquals("y", r.result().toString());
    }

    @Test
    void identityOnInt() {
        // ((lambda x.x) 20) -> 20
        assertEquals(20, intResult(eval(app(lam("x", v("x")), c(20)))));
    }

    @Test
    void churchSuccAppliedTwice() {
        // (((lambda f.lambda x.(f (f x))) succ) 1) -> succ(succ 1) = 3
        Instruction twice = lam("f", lam("x", app(v("f"), app(v("f"), v("x")))));
        assertEquals(3, intResult(eval(app(app(twice, op("succ")), c(1)))));
    }

    @Test
    void higherOrderAdd() {
        // (((lambda x.(lambda y.(add x y))) 3) 4) -> 7
        assertEquals(7, intResult(eval(
                app(app(lam("x", lam("y", app(app(op("add"), v("x")), v("y")))), c(3)), c(4)))));
    }

    @Test
    void nestedSucc() {
        // ((lambda x.(succ (succ x))) 5) -> 7
        assertEquals(7, intResult(eval(
                app(lam("x", app(op("succ"), app(op("succ"), v("x")))), c(5)))));
    }

    @Test
    void selfApplicationPartial() {
        // (((lambda x.(x x)) sqr) 4)
        // (sqr sqr) 是非整数实参的一元应用 -> 得到延迟闭包 λx.(sqr x)，
        // 再应用到 4 -> sqr(4) = 16（不是 256，因 x 在闭包应用时被覆盖）
        Instruction selfApp = lam("x", app(v("x"), v("x")));
        assertEquals(16, intResult(eval(app(app(selfApp, op("sqr")), c(4)))));
    }

    @Test
    void shadowingReturnsClosure() {
        // lambda x.lambda x.x -> 闭包（内层 x 遮蔽外层）
        Reduction r = eval(lam("x", lam("x", v("x"))));
        assertInstanceOf(ClosureValue.class, r.result());
        assertEquals("(lambda x.(lambda x.x))", r.result().toString());
    }

    @Test
    void infiniteReductionDetected() {
        // (lambda x.(x x)) (lambda x.(x x)) -> 发散，应返回 InfiniteValue
        Instruction omega = app(lam("x", app(v("x"), v("x"))), lam("x", app(v("x"), v("x"))));
        Reduction r = eval(omega);
        assertTrue(r.infiniteReduction(), "应检测到无限归约");
        assertInstanceOf(InfiniteValue.class, r.result());
        assertTrue(r.result().toString().startsWith("inf"));
    }

    @Test
    void stateIsTerminatedAfterRun() {
        MachineState state = new MachineState();
        new SECD().run(state, app(app(op("add"), c(10)), c(20)));
        assertTrue(state.isTerminated(), "运行结束后 C/D 栈应为空");
    }

    // ---------------------------------------------------------- 条件/比较

    private static Instruction ifThenElse(Instruction cond, Instruction then, Instruction otherwise) {
        return new InstSeq(List.of(cond, new InstIfThenElse(then, otherwise)));
    }

    private static boolean boolResult(Reduction r) {
        assertInstanceOf(BoolValue.class, r.result(), "expected BoolValue but got: " + r.result());
        return ((BoolValue) r.result()).val;
    }

    @Test
    void ifTrueBranch() {
        // if #t then 10 else 20 -> 10
        Reduction r = eval(ifThenElse(new InstConst(new BoolValue(true)), c(10), c(20)));
        assertEquals(10, intResult(r));
    }

    @Test
    void ifFalseBranch() {
        // if #f then 10 else 20 -> 20
        Reduction r = eval(ifThenElse(new InstConst(new BoolValue(false)), c(10), c(20)));
        assertEquals(20, intResult(r));
    }

    @Test
    void ifZeroIsTruthy() {
        // Scheme 真值：0 为真 → if 0 then 1 else 2 -> 1
        assertEquals(1, intResult(eval(ifThenElse(c(0), c(1), c(2)))));
    }

    @Test
    void ifEmptyStringIsTruthy() {
        // "" 为真
        Reduction r = eval(ifThenElse(new InstConst(new StringValue("")), c(1), c(2)));
        assertEquals(1, intResult(r));
    }

    @Test
    void ifAgentRefIsTruthy() {
        // AgentRef（任意存在的引用）为真
        Reduction r = eval(ifThenElse(
                new InstConst(new AgentRefValue("a1", "Alice")), c(1), c(2)));
        assertEquals(1, intResult(r));
    }

    @Test
    void ifWithCompareCondition() {
        // if (gt 3 2) then 1 else 2 -> 1
        Reduction r = eval(ifThenElse(app(app(op("gt"), c(3)), c(2)), c(1), c(2)));
        assertEquals(1, intResult(r));
    }

    @Test
    void ifConsumesCondNoStrayValue() {
        // if 只弹一次 S 顶，条件值不残留；if 嵌在序列中，结果应恰好是分支值
        Reduction r = eval(new InstSeq(List.of(
                app(app(op("add"), c(1)), c(1)),
                ifThenElse(c(0), c(3), c(4)))));
        assertEquals(3, intResult(r), "条件值被 InstIfThenElse 消耗，S 顶应是 then 分支值 3");
    }

    @Test
    void compareEq() {
        assertTrue(boolResult(eval(app(app(op("eq"), c(3)), c(3)))));
        assertFalse(boolResult(eval(app(app(op("eq"), c(3)), c(4)))));
    }

    @Test
    void compareLtGt() {
        assertTrue(boolResult(eval(app(app(op("lt"), c(1)), c(2)))));
        assertFalse(boolResult(eval(app(app(op("lt"), c(2)), c(1)))));
        assertTrue(boolResult(eval(app(app(op("gt"), c(3)), c(2)))));
    }

    @Test
    void compareStringEq() {
        assertTrue(boolResult(eval(app(app(op("eq"),
                new InstConst(new StringValue("hi"))),
                new InstConst(new StringValue("hi"))))));
        assertFalse(boolResult(eval(app(app(op("eq"),
                new InstConst(new StringValue("hi"))),
                new InstConst(new StringValue("yo"))))));
    }

    @Test
    void compareCrossTypeEqIsFalse() {
        // 跨类型 eq（(eq 1 "1")）→ #f
        assertFalse(boolResult(eval(app(app(op("eq"),
                c(1)), new InstConst(new StringValue("1"))))));
    }

    @Test
    void compareDirectionEq() {
        assertTrue(boolResult(eval(app(app(op("eq"),
                new InstConst(new DirectionValue("north"))),
                new InstConst(new DirectionValue("north"))))));
        assertFalse(boolResult(eval(app(app(op("eq"),
                new InstConst(new DirectionValue("north"))),
                new InstConst(new DirectionValue("east"))))));
    }

    // ------------------------------------------------- 算术补全（ADR-0006：回迁 lambdaexpr 全集）

    @Test
    void subCurrying() {
        // ((sub 10) 3) -> 7
        assertEquals(7, intResult(eval(app(app(op("sub"), c(10)), c(3)))));
    }

    @Test
    void divCurrying() {
        // ((div 20) 4) -> 5
        assertEquals(5, intResult(eval(app(app(op("div"), c(20)), c(4)))));
    }

    @Test
    void unaryPred() {
        // (pred 5) -> 4
        assertEquals(4, intResult(eval(app(op("pred"), c(5)))));
    }

    @Test
    void unaryZeropReturnsBool() {
        // (zerop 0) -> #t；(zerop 3) -> #f
        // （与 lambdaexpr 的 1/0 有意分歧：本机谓词一律 BoolValue，真值规则仅 #f 为假）
        assertTrue(boolResult(eval(app(op("zerop"), c(0)))));
        assertFalse(boolResult(eval(app(op("zerop"), c(3)))));
    }

    @Test
    void divByZeroThrowsRichMessage() {
        // ((div 1) 0) -> SecdEvalException，富消息含原语名与操作数
        SecdEvalException ex = assertThrows(SecdEvalException.class,
                () -> eval(app(app(op("div"), c(1)), c(0))));
        assertTrue(ex.getMessage().contains("div"), "消息应含原语名: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("0"), "消息应含操作数: " + ex.getMessage());
    }

    @Test
    void unknownBinaryOpThrowsRichMessage() {
        // ((gcd 1) 2) -> SecdEvalException，消息含未知原语名与操作数（第一现场）
        SecdEvalException ex = assertThrows(SecdEvalException.class,
                () -> eval(app(app(op("gcd"), c(1)), c(2))));
        assertTrue(ex.getMessage().contains("gcd"), "消息应含原语名: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("1"), "消息应含操作数: " + ex.getMessage());
    }

    @Test
    void applyNonFunctionThrowsRichMessage() {
        // ((5 6))：把整数当函数应用 -> SecdEvalException，消息含该值
        SecdEvalException ex = assertThrows(SecdEvalException.class,
                () -> eval(app(c(5), c(6))));
        assertTrue(ex.getMessage().contains("5"), "消息应含被应用的值: " + ex.getMessage());
    }

    // ------------------------------------------------- trace（ADR-0006：lambdaexpr setTrace 回迁）

    @Test
    void traceEmitsPerStepLines() {
        List<String> lines = new ArrayList<>();
        SECD secd = new SECD();
        secd.setTrace(lines::add);
        secd.run(new MachineState(), app(app(op("add"), c(1)), c(2)));

        assertFalse(lines.isEmpty(), "trace 应每步转移输出一行");
        assertTrue(lines.stream().anyMatch(l -> l.contains("const")), "应含 const 规则行: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("app")), "应含 app 规则行: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("apply")), "应含 apply 规则行: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("halt")), "结束应有 halt 行: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("S=[3]")), "终态 S 顶应为 3: " + lines);
    }

    @Test
    void traceDisabledByDefaultIsSilent() {
        // 默认（未挂观察者）运行行为不变——开关关闭零开销
        assertEquals(3, intResult(eval(app(app(op("add"), c(1)), c(2)))));
    }
}
