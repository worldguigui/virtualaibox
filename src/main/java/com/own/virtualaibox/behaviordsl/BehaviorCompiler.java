package com.own.virtualaibox.behaviordsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import com.own.virtualaibox.secd.InstApp;
import com.own.virtualaibox.secd.InstConst;
import com.own.virtualaibox.secd.InstIfThenElse;
import com.own.virtualaibox.secd.InstLam;
import com.own.virtualaibox.secd.InstSeq;
import com.own.virtualaibox.secd.InstVar;
import com.own.virtualaibox.secd.Instruction;
import com.own.virtualaibox.secd.MachineState;
import com.own.virtualaibox.secd.Reduction;
import com.own.virtualaibox.secd.SECD;
import com.own.virtualaibox.secd.value.DirectionValue;
import com.own.virtualaibox.secd.value.IntValue;
import com.own.virtualaibox.secd.value.OpValue;
import com.own.virtualaibox.secd.value.StringValue;
import com.own.virtualaibox.secd.value.Value;
import com.own.virtualaibox.secd.value.VoidValue;

/**
 * 行为 DSL 编译器：把 {@code *.lambda} 源文本编译为 {@link BehaviorProgram}。
 *
 * <p>管线（docs §10）：{@code .lambda → ANTLR 解析 → 本类编译 → SECD → Effect → World}。
 * 语法由 {@code src/main/antlr4/.../BehaviorDSL.g4} 生成解析器，本类只做
 * "语法树 → SECD Instruction" 的语义编译与静态校验。</p>
 *
 * <p>语义要点：</p>
 * <ul>
 *   <li>原语（move/speak/remember/ask-llm/add/mul/succ/sqr/比较/感知）编译为
 *       {@code InstConst(OpValue)}，柯里化应用；应用 {@code (f a b)} 左结合展开为
 *       {@code ((f a) b)}；零参调用 {@code (f)} 应用一次到 unit（VoidValue）；</li>
 *   <li>{@code let x = e in b} 编译为 {@code apply(λx.b, e)}（β 归约），不占新 D 帧；</li>
 *   <li>{@code if/then/else} 编译为 {@code [cond, InstIfThenElse(then, else)]}，
 *       条件只弹 S 顶一次并把选中分支压入 C，不占新 D 帧（docs §17.5）；真值遵循
 *       Scheme 风格——仅 {@code #f} 为假，其余（0、""、AgentRef 等）皆真；</li>
 *   <li>def 的顶层引用在编译期以自由变量形式记录，编译期按依赖序把 def 求值为闭包值
 *       （{@link SECD} 运行编译指令），运行时作为 E 基底；循环/未知引用在加载期报错。</li>
 * </ul>
 */
public class BehaviorCompiler {

    /** 运行时原语集合（对应 WorldOpEvaluator / ArithmeticOpEvaluator，含感知和比较）。 */
    private static final Set<String> OPS = Set.of(
            "move", "speak", "remember", "ask-llm",
            "add", "mul", "sub", "div", "succ", "sqr", "pred", "zerop",
            "eq", "lt", "gt", "le", "ge",
            "self", "closest", "dist-to", "name-of", "direction-of");

    /** plan 中允许的自由变量（运行时由 LLM Oracle 注入 E）。 */
    private static final Set<String> RUNTIME_VARS = Set.of("dx", "dy");

    /**
     * 编译一个 DSL 源文本。
     *
     * @param source     文件内容
     * @param sourceName 文件名（仅用于错误信息）
     * @return 编译结果
     * @throws IllegalArgumentException 语法错误 / 语义错误（未知名、循环、缺 plan、if 未实现）
     */
    public BehaviorProgram compile(String source, String sourceName) {
        BehaviorDSLParser.ProgContext prog = parse(source, sourceName);

        Map<String, DefDef> defs = new LinkedHashMap<>();
        Instruction plan = null;
        Set<String> planFreeNames = Set.of();
        for (BehaviorDSLParser.TopLevelContext tl : prog.topLevel()) {
            if (tl.defDecl() != null) {
                String name = tl.defDecl().NAME().getText();
                if (defs.containsKey(name)) {
                    throw error(sourceName, "重复定义 '" + name + "'");
                }
                CompiledExpr ce = compileExpr(tl.defDecl().expr(), new HashSet<>());
                defs.put(name, new DefDef(name, ce.code(), ce.freeTopNames()));
            } else if (tl.planDecl() != null) {
                if (plan != null) {
                    throw error(sourceName, "plan 只能定义一次");
                }
                CompiledExpr ce = compileExpr(tl.planDecl().expr(), new HashSet<>());
                plan = ce.code();
                planFreeNames = ce.freeTopNames();
            }
        }
        if (plan == null) {
            throw error(sourceName, "缺少 plan 定义（plan 是运行时入口）");
        }

        // 静态校验：plan 自由变量 ⊆ {dx,dy} ∪ defs；def 自由变量 ⊆ defs
        for (String name : planFreeNames) {
            if (!defs.containsKey(name) && !RUNTIME_VARS.contains(name)) {
                throw error(sourceName, "plan 引用了未知名称 '" + name
                        + "'（可用：def 名、运行时变量 dx/dy）");
            }
        }
        for (Map.Entry<String, DefDef> entry : defs.entrySet()) {
            for (String name : entry.getValue().freeTopNames()) {
                if (!defs.containsKey(name)) {
                    throw error(sourceName, "def '" + entry.getKey()
                            + "' 引用了未知名称 '" + name + "'");
                }
            }
        }

        Map<String, Value> env = resolveDefs(defs, sourceName);
        return new BehaviorProgram(plan, env, env.get("onMeet"));
    }

    // ------------------------------------------------------------------ 解析

    private BehaviorDSLParser.ProgContext parse(String source, String sourceName) {
        BehaviorDSLLexer lexer = new BehaviorDSLLexer(CharStreams.fromString(source));
        BehaviorDSLParser parser = new BehaviorDSLParser(new CommonTokenStream(lexer));
        SyntaxErrors errors = new SyntaxErrors();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        BehaviorDSLParser.ProgContext prog = parser.prog();
        if (!errors.errors.isEmpty()) {
            throw error(sourceName, "语法错误：\n" + String.join("\n", errors.errors));
        }
        return prog;
    }

    /** ANTLR 语法错误收集：带行列号，便于定位 .lambda 文件错误。 */
    private static class SyntaxErrors extends BaseErrorListener {
        final List<String> errors = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            errors.add("第" + line + "行第" + (charPositionInLine + 1) + "列：" + msg);
        }
    }

    // ---------------------------------------------------------- 语义编译

    /** 一段编译产物：指令 + 顶层自由名称（非局部、非原语的名字引用）。 */
    private record CompiledExpr(Instruction code, Set<String> freeTopNames) {
    }

    private record DefDef(String name, Instruction code, Set<String> freeTopNames) {
    }

    private CompiledExpr compileExpr(BehaviorDSLParser.ExprContext ctx, Set<String> scope) {
        if (ctx.lambda() != null) {
            BehaviorDSLParser.LambdaContext lam = ctx.lambda();
            String param = lam.NAME().getText();
            Set<String> bodyScope = new HashSet<>(scope);
            bodyScope.add(param);
            CompiledExpr body = compileExpr(lam.expr(), bodyScope);
            return new CompiledExpr(new InstLam(param, body.code()), body.freeTopNames());
        }
        if (ctx.let() != null) {
            BehaviorDSLParser.LetContext let = ctx.let();
            String name = let.NAME().getText();
            CompiledExpr bound = compileExpr(let.expr(0), scope);
            Set<String> bodyScope = new HashSet<>(scope);
            bodyScope.add(name);
            CompiledExpr body = compileExpr(let.expr(1), bodyScope);
            Set<String> free = new HashSet<>(bound.freeTopNames());
            free.addAll(body.freeTopNames());
            free.remove(name);
            return new CompiledExpr(
                    new InstApp(new InstLam(name, body.code()), bound.code()), free);
        }
        if (ctx.ifExpr() != null) {
            // 条件分支：cond 求值后 InstIfThenElse 弹 S 顶，
            // 按真值把选中分支压入 C；不占新 D 帧，与中断/恢复兼容。
            BehaviorDSLParser.IfExprContext ie = ctx.ifExpr();
            CompiledExpr cond = compileExpr(ie.expr(0), scope);
            CompiledExpr then = compileExpr(ie.expr(1), scope);
            CompiledExpr otherwise = compileExpr(ie.expr(2), scope);
            Set<String> free = new HashSet<>(cond.freeTopNames());
            free.addAll(then.freeTopNames());
            free.addAll(otherwise.freeTopNames());
            return new CompiledExpr(
                    new InstSeq(List.of(cond.code(),
                            new InstIfThenElse(then.code(), otherwise.code()))), free);
        }
        if (ctx.application() != null) {
            BehaviorDSLParser.ApplicationContext app = ctx.application();
            // 文法限定参数为 callArg（原子或括号表达式），杜绝 (f a b) 被误解析为 (f (a b))。
            // 头：并列形式 f a b 是裸名字（callArg 全为实参）；括号形式 (f a b) 是 callArg[0]。
            List<BehaviorDSLParser.CallArgContext> args = app.callArg();
            CompiledExpr head;
            int start;
            if (app.NAME() != null) {
                head = compileName(app.NAME().getText(), scope);
                start = 0;
            } else {
                head = compileCallArg(args.get(0), scope);
                start = 1;
            }
            Instruction code = head.code();
            Set<String> free = new HashSet<>(head.freeTopNames());
            for (int i = start; i < args.size(); i++) {
                CompiledExpr arg = compileCallArg(args.get(i), scope);
                code = new InstApp(code, arg.code());
                free.addAll(arg.freeTopNames());
            }
            // 零参调用 (f)：callArg 仅头本身 → 应用一次到 unit（VoidValue），
            // 使一元感知原语（self/closest）被实际调用而不是只压入操作符值。
            if (app.NAME() == null && args.size() == 1) {
                code = new InstApp(code, new InstConst(new VoidValue()));
            }
            return new CompiledExpr(code, free);
        }
        if (ctx.LPAREN() != null) {
            // 括号分组 (expr)：用于包裹 if/let/lambda 等非 callArg 表达式
            return compileGrouped(ctx.expr(), scope);
        }
        if (ctx.seq() != null) {
            List<Instruction> list = new ArrayList<>();
            Set<String> free = new HashSet<>();
            for (BehaviorDSLParser.ExprContext e : ctx.seq().expr()) {
                CompiledExpr ce = compileExpr(e, scope);
                list.add(ce.code());
                free.addAll(ce.freeTopNames());
            }
            return new CompiledExpr(new InstSeq(list), free);
        }
        return compileAtom(ctx.atom(), scope);
    }

    private CompiledExpr compileAtom(BehaviorDSLParser.AtomContext atom, Set<String> scope) {
        if (atom.INT() != null) {
            return new CompiledExpr(
                    new InstConst(new IntValue(Integer.parseInt(atom.INT().getText()))), Set.of());
        }
        if (atom.STRING() != null) {
            return new CompiledExpr(
                    new InstConst(new StringValue(unescape(atom.STRING().getText()))), Set.of());
        }
        if (atom.direction() != null) {
            return new CompiledExpr(
                    new InstConst(new DirectionValue(atom.direction().getText())), Set.of());
        }
        return compileName(atom.NAME().getText(), scope);
    }

    /** 应用实参：原子 → compileAtom；括号表达式 → compileGrouped。 */
    private CompiledExpr compileCallArg(BehaviorDSLParser.CallArgContext c, Set<String> scope) {
        if (c.atom() != null) {
            return compileAtom(c.atom(), scope);
        }
        return compileGrouped(c.expr(), scope);
    }

    /** 括号分组 (expr)：inner 为单个原子原语 → 零参调用；否则原样编译。 */
    private CompiledExpr compileGrouped(BehaviorDSLParser.ExprContext inner, Set<String> scope) {
        if (inner.atom() != null && inner.atom().NAME() != null
                && OPS.contains(inner.atom().NAME().getText())) {
            CompiledExpr head = compileAtom(inner.atom(), scope);
            return new CompiledExpr(
                    new InstApp(head.code(), new InstConst(new VoidValue())), head.freeTopNames());
        }
        return compileExpr(inner, scope);
    }

    /** 裸名字的编译：局部变量 → InstVar；原语 → 常量操作符；否则顶层自由变量。 */
    private CompiledExpr compileName(String name, Set<String> scope) {
        if (scope.contains(name)) {
            return new CompiledExpr(new InstVar(name), Set.of());
        }
        if (OPS.contains(name)) {
            return new CompiledExpr(new InstConst(new OpValue(name)), Set.of());
        }
        // 顶层名字（def 或运行时变量 dx/dy）：留作自由变量，编译期解析
        return new CompiledExpr(new InstVar(name), Set.of(name));
    }

    // ---------------------------------------------------------- def 解析

    /**
     * 按依赖序把 def 求值为闭包值。被引用的 def 先求值并注入 E，
     * 使闭包捕获其依赖；循环引用报错。
     */
    private Map<String, Value> resolveDefs(Map<String, DefDef> defs, String sourceName) {
        Map<String, Value> env = new HashMap<>();
        Set<String> resolving = new HashSet<>();
        for (String name : defs.keySet()) {
            resolve(name, defs, env, resolving, sourceName);
        }
        return env;
    }

    private Value resolve(String name, Map<String, DefDef> defs, Map<String, Value> env,
                          Set<String> resolving, String sourceName) {
        Value existing = env.get(name);
        if (existing != null) {
            return existing;
        }
        DefDef def = defs.get(name);
        if (def == null) {
            throw error(sourceName, "未定义的顶层名称 '" + name + "'");
        }
        if (resolving.contains(name)) {
            throw error(sourceName, "def 循环引用：涉及 '" + name + "'");
        }
        resolving.add(name);
        // 依赖先求值，作为本 def 闭包的捕获环境
        Map<String, Value> depEnv = new HashMap<>();
        for (String dep : def.freeTopNames()) {
            if (defs.containsKey(dep)) {
                depEnv.put(dep, resolve(dep, defs, env, resolving, sourceName));
            }
        }
        Value value = eval(def.code(), depEnv);
        env.put(name, value);
        resolving.remove(name);
        return value;
    }

    /** 用纯算术求值器运行一段指令，得到 def 的值（λ def 得到闭包）。 */
    private Value eval(Instruction code, Map<String, Value> env) {
        MachineState ms = new MachineState(new java.util.Stack<>(), new HashMap<>(env),
                new java.util.Stack<>(), new java.util.Stack<>());
        Reduction reduction = new SECD().run(ms, code);
        if (reduction.infiniteReduction()) {
            throw new IllegalArgumentException("def 求值发散（无限归约）：" + code);
        }
        if (ms.getS().isEmpty()) {
            throw new IllegalArgumentException("def 求值结果为空：" + code);
        }
        return ms.getS().peek();
    }

    private static IllegalArgumentException error(String sourceName, String msg) {
        return new IllegalArgumentException(sourceName + "：" + msg);
    }

    /** 字符串字面量去引号 + 极简转义。 */
    private static String unescape(String raw) {
        if (raw.length() < 2) {
            return raw;
        }
        return raw.substring(1, raw.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }
}
