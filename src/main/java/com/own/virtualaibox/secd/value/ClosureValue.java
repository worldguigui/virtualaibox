package com.own.virtualaibox.secd.value;

import com.own.virtualaibox.secd.InstApp;
import com.own.virtualaibox.secd.InstConst;
import com.own.virtualaibox.secd.InstLam;
import com.own.virtualaibox.secd.InstVar;
import com.own.virtualaibox.secd.Instruction;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 闭包（函数 + 捕获的环境）。
 * 移植自 lambdaexpr 的 ClosureValue，保留递归打印与防环逻辑。
 */
public class ClosureValue implements Value {
    final String arg;
    Instruction body;
    final Map<String, Value> env;

    public ClosureValue(String arg, Instruction body, Map<String, Value> env) {
        this.arg = arg;
        this.body = body;
        this.env = env;
    }

    public String getArg() {
        return arg;
    }

    public Instruction getBody() {
        return body;
    }

    public Map<String, Value> getEnv() {
        return env;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClosureValue && toString().equals(o.toString());
    }

    @Override
    public String toString() {
        return toString(new HashSet<>());
    }

    private String toString(Set<ClosureValue> seen) {
        if (seen.contains(this)) {
            return "recursive";
        }
        seen.add(this);

        String bodyStr = instructionToString(body, seen);

        seen.remove(this);
        return "(lambda " + arg + "." + bodyStr + ")";
    }

    private String instructionToString(Instruction inst, Set<ClosureValue> seen) {
        if (inst instanceof InstVar) {
            String varName = ((InstVar) inst).name;
            if (varName.equals(arg)) {
                return varName;
            }
            if (env.containsKey(varName)) {
                Value val = env.get(varName);
                if (val instanceof ClosureValue) {
                    return ((ClosureValue) val).toString(seen);
                }
                return val.toString();
            }
            return varName;

        } else if (inst instanceof InstConst) {
            return ((InstConst) inst).value.toString();

        } else if (inst instanceof InstLam) {
            InstLam lam = (InstLam) inst;
            return "(lambda " + lam.arg + "." + instructionToString(lam.body, seen) + ")";

        } else if (inst instanceof InstApp) {
            InstApp app = (InstApp) inst;
            String rator = instructionToString(app.rator, seen);
            String rand = instructionToString(app.rand, seen);
            return "(" + rator + " " + rand + ")";

        } else {
            return inst.toString();
        }
    }
}
