// 
// A starting version of IR1 interpreter. (For CS322 W15 Assignment 1)
//
//

import ir1.IR1;
import ir1.ir1Parser;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class IR1Interp {

    static class IntException extends Exception {
        public IntException(String msg) {
            super(msg);
        }
    }

    //-----------------------------------------------------------------
    // Value representation
    //-----------------------------------------------------------------
    //
    abstract static class Val {
        public static Val Undefined;
    }

    // Integer values
    //
    static class IntVal extends Val {
        int i;

        IntVal(int i) {
            this.i = i;
        }

        public String toString() {
            return "" + i;
        }
    }

    // Boolean values
    //
    static class BoolVal extends Val {
        boolean b;

        BoolVal(boolean b) {
            this.b = b;
        }

        public String toString() {
            return "" + b;
        }
    }

    // String values
    //
    static class StrVal extends Val {
        String s;

        StrVal(String s) {
            this.s = s;
        }

        public String toString() {
            return s;
        }
    }

    // A special "undefined" value
    //
    static class UndVal extends Val {
        public String toString() {
            return "UndVal";
        }
    }

    //-----------------------------------------------------------------
    // Environment representation
    //-----------------------------------------------------------------
    //
    // Think of how to organize environments.
    //
    // The following environments are shown in the lecture for use in
    // an IR0 interpreter:
    //
    //   HashMap<String,Integer> labelMap;  // label table
    //   HashMap<Integer,Val> tempMap;	  // temp table
    //   HashMap<String,Val> varMap;	  // var table
    //
    // For IR1, they need to be managed at per function level.
    //

    //This class used for handling environments
    static class Env {
        HashMap<String, Integer> labelMap = new HashMap<String, Integer>();  // label table
        HashMap<Integer, Val> tempMap = new HashMap<Integer, Val>();      // temp table
        HashMap<String, Val> varMap = new HashMap<String, Val>();      // var table

        public void addLabel(String s, Integer i) {
            labelMap.put(s, i);
        }

        public Integer getLabel(String s) {
            return labelMap.get(s);
        }

        public void addTemp(Integer i, Val v) {
            tempMap.put(i, v);
        }

        public Val getTemp(Integer i) {
            return tempMap.get(i);
        }

        public void addVar(String s, Val v) {
            varMap.put(s, v);
        }

        public Val getVar(String s) {
            return varMap.get(s);
        }
    }


    //-----------------------------------------------------------------
    // Global variables and constants
    //-----------------------------------------------------------------
    //
    // These variables and constants are for your reference only.
    // You may decide to use all of them, some of these, or not at all.
    //

    static Env env = new Env();
    static Env envFunc;

    static ArrayList<Val> storage;

    // Function lookup table
    // - maps function names to their AST nodes
    //
    static HashMap<String, IR1.Func> funcMap;

    // Heap memory
    // - for handling 'malloc'ed data
    // - you need to define alloc and access methods for it
    //
    static ArrayList<Val> heap = new ArrayList<Val>();

    // Return value
    // - for passing return value from callee to caller
    //
    static Val retVal;

    // Execution status
    // - tells whether to continue with the nest inst, to jump to
    //   a new target inst, or to return to the caller
    //
    static final int CONTINUE = 0;
    static final int RETURN = -1;

    //malloc returns s

    //-----------------------------------------------------------------
    // The main method
    //-----------------------------------------------------------------
    //
    // 1. Open an IR1 program file.
    // 2. Call the IR1 AST parser to read in the program and
    //    convert it to an AST (rooted at an IR1.Program node).
    // 3. Invoke the interpretation process on the root node.
    //
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            FileInputStream stream = new FileInputStream(args[0]);
            IR1.Program p = new ir1Parser(stream).Program();
            stream.close();
            IR1Interp.execute(p);
        } else {
            System.out.println("You must provide an input file name.");
        }
    }

    //-----------------------------------------------------------------
    // Top-level IR nodes
    //-----------------------------------------------------------------
    //

    // Program ---
    //  Func[] funcs;
    //
    // 1. Establish the function lookup map
    // 2. Lookup 'main' in funcMap, and
    // 3. start interpreting from main's AST node
    //
    public static void execute(IR1.Program n) throws Exception {
        funcMap = new HashMap<String, IR1.Func>();
        storage = new ArrayList<Val>();
        retVal = Val.Undefined;
        for (IR1.Func f : n.funcs)
            funcMap.put(f.name, f);
        execute(funcMap.get("main"));
    }

    // Func ---
    //  String name;
    //  Var[] params;
    //  Var[] locals;
    //  Inst[] code;
    //
    // 1. Collect label decls information and store them in
    //    a label-lookup table for later use.
    // 2. Execute the fetch-and-execute loop.
    //
    static void execute(IR1.Func n) throws Exception {

        for (Integer i = 0; i < n.code.length - 1; ++i) {
            if (n.code[i] instanceof IR1.LabelDec) {
                env.addLabel(n.code[i].toString().substring(0, n.code[i].toString().length() - 2), i);
            }
        }

        // The fetch-and-execute loop
        int idx = 0;
        while (idx < n.code.length) {
            int next = execute(n.code[idx]);
            if (next == CONTINUE)
                idx++;
            else if (next == RETURN)
                break;
            else
                idx = next;
        }
    }

    // Dispatch execution to an individual Inst node.
    //
    static int execute(IR1.Inst n) throws Exception {
        if (n instanceof IR1.Binop) return execute((IR1.Binop) n);
        if (n instanceof IR1.Unop) return execute((IR1.Unop) n);
        if (n instanceof IR1.Move) return execute((IR1.Move) n);
        if (n instanceof IR1.Load) return execute((IR1.Load) n);
        if (n instanceof IR1.Store) return execute((IR1.Store) n);
        if (n instanceof IR1.Jump) return execute((IR1.Jump) n);
        if (n instanceof IR1.CJump) return execute((IR1.CJump) n);
        if (n instanceof IR1.Call) return execute((IR1.Call) n);
        if (n instanceof IR1.Return) return execute((IR1.Return) n);
        if (n instanceof IR1.LabelDec) return CONTINUE;
        throw new IntException("Unknown Inst: " + n);
    }

    //-----------------------------------------------------------------
    // Execution routines for individual Inst nodes
    //-----------------------------------------------------------------
    //
    // - Each execute() routine returns CONTINUE, RETURN, or a new idx
    //   (target of jump).
    //

    // Binop ---
    //  BOP op;
    //  Dest dst;
    //  Src src1, src2;
    //
    static int execute(IR1.Binop n) throws Exception {

        Val val1 = evaluate(n.src1);
        Val val2 = evaluate(n.src2);
        Val res = null;
        if (n.op instanceof IR1.AOP) {
            if (n.op == IR1.AOP.ADD) {
                res = new IntVal(((IntVal) val1).i + ((IntVal) val2).i);
            }
            if (n.op == IR1.AOP.SUB) {
                res = new IntVal(((IntVal) val1).i - ((IntVal) val2).i);
            }
            if (n.op == IR1.AOP.DIV) {
                res = new IntVal(((IntVal) val1).i / ((IntVal) val2).i);
            }
            if (n.op == IR1.AOP.MUL) {
                res = new IntVal(((IntVal) val1).i * ((IntVal) val2).i);
            }
            if (n.op == IR1.AOP.OR) {
                res = new BoolVal(((BoolVal) val1).b || ((BoolVal) val2).b);
            }
            if (n.op == IR1.AOP.AND) {
                res = new BoolVal(((BoolVal) val1).b && ((BoolVal) val2).b);
            }
        }
        if (n.op instanceof IR1.ROP) {
            if (n.op == IR1.ROP.EQ) {
                if (val1 instanceof BoolVal && val2 instanceof BoolVal) {
                    res = new BoolVal(((BoolVal) val1).b == ((BoolVal) val2).b);
                } else {
                    res = new BoolVal(((IntVal) val1).i == ((IntVal) val2).i);
                }
            }
            if (n.op == IR1.ROP.GE) {
                res = new BoolVal(((IntVal) val1).i >= ((IntVal) val2).i);
            }
            if (n.op == IR1.ROP.GT) {
                res = new BoolVal(((IntVal) val1).i > ((IntVal) val2).i);
            }
            if (n.op == IR1.ROP.LE) {
                res = new BoolVal(((IntVal) val1).i <= ((IntVal) val2).i);
            }
            if (n.op == IR1.ROP.LT) {
                res = new BoolVal(((IntVal) val1).i > ((IntVal) val2).i);
            }
            if (n.op == IR1.ROP.NE) {
                if (val1 instanceof BoolVal && val2 instanceof BoolVal) {
                    res = new BoolVal(((BoolVal) val1).b != ((BoolVal) val2).b);
                } else {
                    res = new BoolVal(((IntVal) val1).i != ((IntVal) val2).i);
                }
            }
        }
        if (n.dst instanceof IR1.Id) {
            env.addVar((((IR1.Id) n.dst)).name, res);
        }
        if (n.dst instanceof IR1.Temp) {
            env.addTemp((((IR1.Temp) n.dst)).num, res);
        }

        return CONTINUE;
    }

    // Unop ---
    //  UOP op;
    //  Dest dst;
    //  Src src;
    //
    static int execute(IR1.Unop n) throws Exception {
        Val val = evaluate(n.src);
        Val res;
        if (n.op == IR1.UOP.NEG)
            res = new IntVal(-((IntVal) val).i);
        else if (n.op == IR1.UOP.NOT)
            res = new BoolVal(!((BoolVal) val).b);
        else
            throw new IntException("Wrong op in Unop inst: " + n.op);

        if (n.dst instanceof IR1.Id) {
            env.addVar((((IR1.Id) n.dst)).name, res);
        }
        if (n.dst instanceof IR1.Temp) {
            env.addTemp((((IR1.Temp) n.dst)).num, res);
        }

        return CONTINUE;
    }

    // Move ---
    //  Dest dst;
    //  Src src;
    //
    static int execute(IR1.Move n) throws Exception {
        if (n.dst instanceof IR1.Id) {
            env.addVar((((IR1.Id) n.dst)).name, evaluate(n.src));
        }
        if (n.dst instanceof IR1.Temp) {
            env.addTemp((((IR1.Temp) n.dst)).num, evaluate(n.src));
        }
        return CONTINUE;
    }

    // Load ---
    //  Dest dst;
    //  Addr addr;
    //
    static int execute(IR1.Load n) throws Exception {
        int dst = evaluate(n.addr);
        if (n.dst instanceof IR1.Id) {
            env.addVar((((IR1.Id) n.dst)).name, new IntVal(dst));
        }
        if (n.dst instanceof IR1.Temp) {
            env.addTemp((((IR1.Temp) n.dst)).num, new IntVal(dst));
        }
        return CONTINUE;
    }

    // Store ---
    //  Addr addr;
    //  Src src;
    //
    static int execute(IR1.Store n) throws Exception {
        int tempIndx = ((IR1.Temp) n.addr.base).num;
        Val tempVal = env.getTemp(tempIndx);
        if (tempVal instanceof IntVal) {
            int heapIdx = Integer.parseInt(tempVal.toString());
            heap.add(heapIdx, evaluate(n.src));
        }
        return CONTINUE;
    }

    // CJump ---
    //  ROP op;
    //  Src src1, src2;
    //  Src src1, src2;
    //  Label lab;
    //
    static int execute(IR1.CJump n) throws Exception {

        Val va11 = evaluate(n.src1);
        Val val2 = evaluate(n.src2);
        if (n.op == IR1.ROP.EQ) {
            if (va11 instanceof IntVal && val2 instanceof IntVal) {
                if (((IntVal) va11).i == ((IntVal) val2).i) {
                    return env.getLabel(n.lab.name);
                }
            }
            if (va11 instanceof BoolVal && val2 instanceof BoolVal) {
                if (((BoolVal) va11).b == ((BoolVal) val2).b) {
                    return env.getLabel(n.lab.name);
                }
            }
        }
        if (n.op == IR1.ROP.GE) {
            if (va11 instanceof IntVal && val2 instanceof IntVal) {
                if (((IntVal) va11).i >= ((IntVal) val2).i) {
                    return env.getLabel(n.lab.name);
                }
            }
        }
        if (n.op == IR1.ROP.GT) {
            if (va11 instanceof IntVal && val2 instanceof IntVal) {
                if (((IntVal) va11).i > ((IntVal) val2).i) {
                    return env.getLabel(n.lab.name);
                }
            }
        }
        if (n.op == IR1.ROP.LE) {
            if (va11 instanceof IntVal && val2 instanceof IntVal) {
                if (((IntVal) va11).i <= ((IntVal) val2).i) {
                    return env.getLabel(n.lab.name);
                }
            }
        }
        if (n.op == IR1.ROP.LT) {
            if (va11 instanceof IntVal && val2 instanceof IntVal) {
                if (((IntVal) va11).i < ((IntVal) val2).i) {
                    return env.getLabel(n.lab.name);
                }
            }
        }
        if (n.op == IR1.ROP.NE) {
            if (va11 instanceof IntVal && val2 instanceof IntVal) {
                if (((IntVal) va11).i != ((IntVal) val2).i) {
                    return env.getLabel(n.lab.name);
                }
            }
            if (va11 instanceof BoolVal && val2 instanceof BoolVal) {
                if (((BoolVal) va11).b != ((BoolVal) val2).b) {
                    return env.getLabel(n.lab.name);
                }
            }
        }

        return CONTINUE;
    }

    // Jump ---
    //  Label lab;
    //

    static int execute(IR1.Jump n) throws Exception {

        // ... code needed ...
        //return address
        return -1;

    }

    // Call ---
    //  String name;
    //  Src[] args;
    //  Dest rdst;
    //
    static int execute(IR1.Call n) throws Exception {
        if (n.name.equals("malloc")) {
            Integer currentSize = heap.size();
            heap = new ArrayList<Val>();
            for (int i = 0; i < Integer.parseInt(n.args[0].toString()); ++i) {
                heap.add(null);
            }
            env.addTemp(((IR1.Temp) n.rdst).num, new IntVal(currentSize));
            return currentSize;
        }

        printArgs(n);

        return CONTINUE;
    }

    static void printArgs(IR1.Call n) {
        if (n.args.length !=0 && n.args[0] instanceof IR1.StrLit) {
            System.out.println(n.args[0].toString().substring(1, n.args[0].toString().length() - 1));
        }
        if (n.args.length !=0 && n.args[0] instanceof IR1.IntLit) {
            System.out.println(n.args[0]);
        }
        if (n.args.length != 0 && n.args[0] instanceof IR1.BoolLit){
            System.out.println(n.args[0]);
        }
        if(n.args.length != 0 && n.args[0] instanceof IR1.Id){
            System.out.println(env.getVar(n.args[0].toString()));
        }
        if(n.args.length != 0 && n.args[0] instanceof IR1.Temp){
            System.out.println(env.getTemp(((IR1.Temp) n.args[0]).num));
        }
        if(n.name.equals("printStr") && n.args.length ==0){
            System.out.println();
        }
    }


    /*static void printArgs(IR1.Call n) {
        if (env.getVar(n.args[0].toString()) != null) {
            System.out.println(env.getVar(n.args[0].toString()));
        } else if(env.getTemp(((IR1.Temp) n.args[0]).num) != null ){
            System.out.println(env.getTemp(((IR1.Temp) n.args[0]).num));
        }
        else {

            if (n.name.equals("printStr")) {
                if (n.args.length == 0) {
                    System.out.println();
                } else {
                    System.out.println(n.args[0].toString().substring(1, n.args[0].toString().length() - 1));
                }
            }
            if (n.name.equals("printInt")) {
                System.out.println(n.args[0]);
            }
        }
    }
*/
    // Return ---
    //  Src val;
    //
    static int execute(IR1.Return n) throws Exception {

        // ... code needed ...

        return RETURN;
    }

    //-----------------------------------------------------------------
    // Evaluatation routines for address
    //-----------------------------------------------------------------
    //
    // - Returns an integer (representing index to the heap memory).
    //
    // Address ---
    //  Src base;
    //  int offset;
    //
    static int evaluate(IR1.Addr n) throws Exception {
        Val addrValObj = env.getTemp(((IR1.Temp) n.base).num);
        int addrVal = Integer.parseInt(addrValObj.toString());

        int heapVal = Integer.parseInt(heap.get(addrVal).toString());
        return heapVal;


    }

    //-----------------------------------------------------------------
    // Evaluatation routines for operands
    //-----------------------------------------------------------------
    //
    // - Each evaluate() routine returns a Val object.
    //
    static Val evaluate(IR1.Src n) throws Exception {
        Val val = null;
        if (n instanceof IR1.Temp) val = env.getTemp(((IR1.Temp) n).num);
        if (n instanceof IR1.Id) val = env.getVar(((IR1.Id) n).name);
        if (n instanceof IR1.IntLit) val = new IntVal(((IR1.IntLit) n).i);
        if (n instanceof IR1.BoolLit) val = new BoolVal(((IR1.BoolLit) n).b);
        if (n instanceof IR1.StrLit) val = new StrVal(((IR1.StrLit) n).s);
        return val;
    }

    static Val evaluate(IR1.Dest n) throws Exception {
        Val val = null;
        // if (n instanceof IR1.Temp) val =
        // if (n instanceof IR1.Id)   val =
        return val;
    }


}
