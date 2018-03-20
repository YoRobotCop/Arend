package com.jetbrains.jetpad.vclang.classes;

import com.jetbrains.jetpad.vclang.core.context.binding.LevelVariable;
import com.jetbrains.jetpad.vclang.core.definition.*;
import com.jetbrains.jetpad.vclang.core.expr.*;
import com.jetbrains.jetpad.vclang.core.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.core.sort.Level;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.prelude.Prelude;
import com.jetbrains.jetpad.vclang.term.group.ChildGroup;
import com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jetpad.vclang.ExpressionFactory.Ref;
import static com.jetbrains.jetpad.vclang.ExpressionFactory.Universe;
import static com.jetbrains.jetpad.vclang.core.expr.ExpressionFactory.FieldCall;
import static com.jetbrains.jetpad.vclang.core.expr.ExpressionFactory.Nat;
import static org.junit.Assert.assertEquals;

public class RecordsTest extends TypeCheckingTestCase {
  @Test
  public void unknownExtTestError() {
    resolveNamesModule(
        "\\class Point { | x : Nat | y : Nat }\n" +
        "\\func C => Point { x => 0 | z => 0 | y => 0 }", 1);
  }

  @Test
  public void resultTypeMismatchTestError() {
    typeCheckModule(
        "\\class Point { | x : Nat | y : Nat }\n" +
        "\\func C => Point { x => \\lam (t : Nat) => t }", 1);
  }

  @Test
  public void parentCallTest() {
    resolveNamesModule(
        "\\class M {" +
        "  \\class A {\n" +
        "    | c : Nat -> Nat -> Nat\n" +
        "    | f : Nat -> Nat\n" +
        "  }\n" +
        "}\n" +
        "\\func B => M.A {\n" +
        "  f => \\lam n => c n n\n" +
        "}", 1);
  }

  @Test
  public void parentCallTypecheckingTest() {
    typeCheckModule(
        "\\class A {\n" +
        "  | c : Nat -> Nat -> Nat\n" +
        "  | f : Nat -> Nat\n" +
        "}\n" +
        "\\func B => A {\n" +
        "  f => \\lam n => c n n\n" +
        "}", 1);
  }

  @Test
  public void duplicateNameTestError() {
    typeCheckModule(
        "\\class A {\n" +
        "  | f : Nat\n" +
        "}\n" +
        "\\func B => A {\n" +
        "  | f => 0\n" +
        "  | f => 1\n" +
        "}", 1);
  }

  @Test
  public void overriddenFieldAccTest() {
    typeCheckModule(
        "\\class Point {\n" +
        "  | x : Nat\n" +
        "  | y : Nat\n" +
        "}\n" +
        "\\func diagonal => \\lam (d : Nat) => Point {\n" +
        "  | x => d\n" +
        "  | y => d\n" +
        "}\n" +
        "\\func test (p : diagonal 0) : p.x = 0 => path (\\lam _ => 0)");
  }

  @Test
  public void notImplementedTest() {
    typeCheckModule(
        "\\class Point {\n" +
        "  | x : Nat\n" +
        "  | y : Nat\n" +
        "}\n" +
        "\\func diagonal => Point { y => 0 }");
  }

  @Test
  public void notImplementedTestError() {
    typeCheckModule(
        "\\class Point {\n" +
        "  | x : Nat\n" +
        "  | y : x = x -> Nat\n" +
        "}\n" +
        "\\func diagonal => Point { y => \\lam _ => 0 }", 1);
  }

  @Test
  public void newAbstractTestError() {
    typeCheckModule(
        "\\class Point {\n" +
        "  | x : Nat\n" +
        "  | y : Nat\n" +
        "}\n" +
        "\\func diagonal => Point { x => 0 }\n" +
        "\\func test => \\new diagonal", 1);
  }

  @Test
  public void newTest() {
    typeCheckModule(
        "\\class Point {\n" +
        "  | x : Nat\n" +
        "  | y : Nat\n" +
        "}\n" +
        "\\func diagonal => \\lam (d : Nat) => Point {\n" +
        "  | x => d\n" +
        "  | y => d\n" +
        "}\n" +
        "\\func diagonal1 => Point {\n" +
        "  | x => 0\n" +
        "  | y => 0\n" +
        "}\n" +
        "\\func test : \\new diagonal1 {} = \\new diagonal 0 => path (\\lam _ => \\new Point { x => 0 | y => 0 })");
  }

  @Test
  public void mutualRecursionTestError() {
    resolveNamesModule(
        "\\class M {" +
        "  \\class Point {\n" +
        "    | x : Nat\n" +
        "    | y : Nat\n" +
        "  }\n" +
        "}\n" +
        "\\func test => M.Point {\n" +
        "  | x => y\n" +
        "  | y => x\n" +
        "}", 2);
  }

  @Test
  public void mutualRecursionTypecheckingError() {
    typeCheckModule(
        "\\class Point {\n" +
        "  | x : Nat\n" +
        "  | y : Nat\n" +
        "}\n" +
        "\\func test => Point {\n" +
        "  | x => y\n" +
        "  | y => x\n" +
        "}", 2);
  }

  @Test
  public void splitClassTestError() {
    resolveNamesModule(
        "\\class A \\where {\n" +
        "  \\func x => 0\n" +
        "}\n" +
        "\\class A \\where {\n" +
        "  \\func y => 0\n" +
        "}", 1);
  }

  @Test
  public void recordUniverseTest() {
    ChildGroup result = typeCheckModule(
        "\\class Point { | x : Nat | y : Nat }\n" +
        "\\func C => Point { x => 0 }");
    assertEquals(Sort.SET0, ((ClassDefinition) getDefinition(result, "Point")).getSort());
    assertEquals(Universe(Sort.SET0), getDefinition(result, "C").getTypeWithParams(new ArrayList<>(), Sort.STD));
  }

  @Test
  public void recordUniverseTest2() {
    ChildGroup result = typeCheckModule(
        "\\class Point { | x : Nat | y : Nat }\n" +
        "\\func C => Point { x => 0 | y => 1 }");
    assertEquals(Sort.SET0, ((ClassDefinition) getDefinition(result, "Point")).getSort());
    assertEquals(Universe(Sort.PROP), getDefinition(result, "C").getTypeWithParams(new ArrayList<>(), Sort.STD));
  }

  @Test
  public void recordUniverseTest3() {
    ChildGroup result = typeCheckModule(
        "\\class Point { | x : \\Type3 | y : \\Type1 }\n" +
        "\\func C => Point { x => Nat }");
    assertEquals(new Sort(new Level(4), new Level(LevelVariable.HVAR, 1)), ((ClassDefinition) getDefinition(result, "Point")).getSort());
    assertEquals(Universe(new Sort(new Level(2), new Level(LevelVariable.HVAR, 1))), getDefinition(result, "C").getTypeWithParams(new ArrayList<>(), Sort.STD));
  }

  @Test
  public void recordUniverseTest4() {
    ChildGroup result = typeCheckModule(
        "\\class Point { | x : \\Type3 | y : \\oo-Type1 }\n" +
        "\\func C => Point { x => Nat }");
    assertEquals(new Sort(new Level(4), Level.INFINITY), ((ClassDefinition) getDefinition(result, "Point")).getSort());
    assertEquals(Universe(new Sort(new Level(2), Level.INFINITY)), getDefinition(result, "C").getTypeWithParams(new ArrayList<>(), Sort.STD));
  }

  @Test
  public void recordUniverseTest5() {
    ChildGroup result = typeCheckModule(
        "\\class Point { | x : \\Type3 | y : \\Type1 }\n" +
        "\\func C => Point { x => \\Type2 }");
    assertEquals(new Sort(new Level(4), new Level(LevelVariable.HVAR, 1)), ((ClassDefinition) getDefinition(result, "Point")).getSort());
    assertEquals(Universe(new Sort(new Level(2), new Level(LevelVariable.HVAR, 2))), getDefinition(result, "C").getTypeWithParams(new ArrayList<>(), Sort.STD));
  }

  @Test
  public void recordConstructorsTest() {
    ChildGroup result = typeCheckModule(
        "\\class A {\n" +
        "  | x : Nat\n" +
        "  \\data Foo | foo (x = 0)\n" +
        "  \\func y : foo = foo => path (\\lam _ => foo)\n" +
        "}\n" +
        "\\func test (p : A) => p.y");
    FunctionDefinition testFun = (FunctionDefinition) getDefinition(result, "test");
    Expression function = testFun.getResultType().normalize(NormalizeVisitor.Mode.WHNF);
    assertEquals(Prelude.PATH, function.cast(DataCallExpression.class).getDefinition());
    List<? extends Expression> arguments = function.cast(DataCallExpression.class).getDefCallArguments();
    assertEquals(3, arguments.size());

    Constructor foo = ((DataDefinition) getDefinition(result, "A.Foo")).getConstructor("foo");

    ConCallExpression arg2 = arguments.get(2).cast(LamExpression.class).getBody().cast(ConCallExpression.class);
    assertEquals(1, arg2.getDataTypeArguments().size());
    assertEquals(Ref(testFun.getParameters()), arg2.getDataTypeArguments().get(0));
    assertEquals(foo, arg2.getDefinition());

    ConCallExpression arg1 = arguments.get(1).cast(LamExpression.class).getBody().cast(ConCallExpression.class);
    assertEquals(1, arg1.getDataTypeArguments().size());
    assertEquals(Ref(testFun.getParameters()), arg1.getDataTypeArguments().get(0));
    assertEquals(foo, arg1.getDefinition());

    Expression domFunction = arguments.get(0).cast(LamExpression.class).getBody().cast(PiExpression.class).getParameters().getTypeExpr().normalize(NormalizeVisitor.Mode.WHNF);
    assertEquals(Prelude.PATH, domFunction.cast(DataCallExpression.class).getDefinition());
    List<? extends Expression> domArguments = domFunction.cast(DataCallExpression.class).getDefCallArguments();
    assertEquals(3, domArguments.size());
    assertEquals(Prelude.NAT, domArguments.get(0).cast(LamExpression.class).getBody().cast(DefCallExpression.class).getDefinition());
    assertEquals(FieldCall((ClassField) getDefinition(result, "A.x"), Ref(testFun.getParameters())), domArguments.get(1));
    assertEquals(Prelude.ZERO, domArguments.get(2).cast(ConCallExpression.class).getDefinition());
  }

  @Test
  public void recordConstructorsParametersTest() {
    ChildGroup result = typeCheckModule(
        "\\class A {\n" +
        "  | x : Nat\n" +
        "  \\data Foo (p : x = x) | foo (p = p)\n" +
        "  \\func y (_ : foo (path (\\lam _ => path (\\lam _ => x))) = foo (path (\\lam _ => path (\\lam _ => x)))) => 0\n" +
        "}\n" +
        "\\func test (q : A) => q.y");
    FunctionDefinition testFun = (FunctionDefinition) getDefinition(result, "test");
    Expression xCall = FieldCall((ClassField) getDefinition(result, "A.x"), Ref(testFun.getParameters()));
    Expression function = testFun.getResultType().cast(PiExpression.class).getParameters().getTypeExpr().normalize(NormalizeVisitor.Mode.NF);
    assertEquals(Prelude.PATH, function.cast(DataCallExpression.class).getDefinition());
    List<? extends Expression> arguments = function.cast(DataCallExpression.class).getDefCallArguments();
    assertEquals(3, arguments.size());

    DataDefinition Foo = (DataDefinition) getDefinition(result, "A.Foo");
    Constructor foo = Foo.getConstructor("foo");

    ConCallExpression arg2Fun = arguments.get(2).cast(ConCallExpression.class);
    assertEquals(2, arg2Fun.getDataTypeArguments().size());
    assertEquals(Ref(testFun.getParameters()), arg2Fun.getDataTypeArguments().get(0));
    ConCallExpression expr1 = arg2Fun.getDataTypeArguments().get(1).cast(ConCallExpression.class);
    assertEquals(Prelude.PATH_CON, expr1.getDefinition());
    assertEquals(xCall, expr1.getDefCallArguments().get(0).cast(LamExpression.class).getBody());

    assertEquals(foo, arg2Fun.getDefinition());
    ConCallExpression expr2 = arg2Fun.getDefCallArguments().get(0).cast(ConCallExpression.class);
    assertEquals(Prelude.PATH_CON, expr2.getDefinition());
    ConCallExpression expr3 = expr2.getDefCallArguments().get(0).cast(LamExpression.class).getBody().cast(ConCallExpression.class);
    assertEquals(Prelude.PATH_CON, expr3.getDefinition());
    assertEquals(xCall, expr3.getDefCallArguments().get(0).cast(LamExpression.class).getBody());

    ConCallExpression arg1Fun = arguments.get(1).cast(ConCallExpression.class);
    assertEquals(2, arg1Fun.getDataTypeArguments().size());
    assertEquals(Ref(testFun.getParameters()), arg1Fun.getDataTypeArguments().get(0));
    assertEquals(expr1, arg1Fun.getDataTypeArguments().get(1));
    assertEquals(foo, arg1Fun.getDefinition());
    ConCallExpression expr4 = arg1Fun.getDefCallArguments().get(0).cast(ConCallExpression.class);
    assertEquals(Prelude.PATH_CON, expr4.getDefinition());
    ConCallExpression expr5 = expr4.getDefCallArguments().get(0).cast(LamExpression.class).getBody().cast(ConCallExpression.class);
    assertEquals(Prelude.PATH_CON, expr5.getDefinition());
    assertEquals(xCall, expr5.getDefCallArguments().get(0).cast(LamExpression.class).getBody());

    LamExpression arg0 = arguments.get(0).cast(LamExpression.class);
    assertEquals(Foo, arg0.getBody().cast(DataCallExpression.class).getDefinition());
    assertEquals(Ref(testFun.getParameters()), arg0.getBody().cast(DataCallExpression.class).getDefCallArguments().get(0));
    ConCallExpression paramConCall = arg0.getBody().cast(DataCallExpression.class).getDefCallArguments().get(1).cast(ConCallExpression.class);
    assertEquals(Prelude.PATH_CON, paramConCall.getDefinition());
    assertEquals(1, paramConCall.getDefCallArguments().size());
    assertEquals(xCall, paramConCall.getDefCallArguments().get(0).cast(LamExpression.class).getBody());

    List<? extends Expression> parameters = paramConCall.getDataTypeArguments();
    assertEquals(3, parameters.size());
    assertEquals(Nat(), parameters.get(0).cast(LamExpression.class).getBody());
    assertEquals(xCall, parameters.get(1).normalize(NormalizeVisitor.Mode.WHNF));
    assertEquals(xCall, parameters.get(2).normalize(NormalizeVisitor.Mode.WHNF));
  }
}
