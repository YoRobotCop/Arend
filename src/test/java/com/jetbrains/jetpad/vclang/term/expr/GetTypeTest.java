package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.term.Preprelude;
import com.jetbrains.jetpad.vclang.term.context.binding.Binding;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.definition.*;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.LeafElimTreeNode;
import com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase.TypeCheckClassResult;
import org.junit.Test;

import java.util.EnumSet;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase.typeCheckClass;
import static com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase.typeCheckDef;
import static org.junit.Assert.assertEquals;

public class GetTypeTest {
  private static void testType(Expression expected, TypeCheckClassResult result) {
    assertEquals(expected, ((FunctionDefinition) result.getDefinition("test")).getResultType());
    assertEquals(expected, ((LeafElimTreeNode) ((FunctionDefinition) result.getDefinition("test")).getElimTree()).getExpression().getType());
  }

  @Test
  public void constructorTest() {
    TypeCheckClassResult result = typeCheckClass("\\static \\data List (A : \\Type0) | nil | cons A (List A) \\static \\function test => cons 0 nil");
    testType(Apps(DataCall((DataDefinition) result.getDefinition("List")), Nat()), result);
  }

  @Test
  public void nilConstructorTest() {
    TypeCheckClassResult result = typeCheckClass("\\static \\data List (A : \\Type0) | nil | cons A (List A) \\static \\function test => (List Nat).nil");
    testType(Apps(DataCall((DataDefinition) result.getDefinition("List")), Nat()), result);
  }

  @Test
  public void classExtTest() {
    TypeCheckClassResult result = typeCheckClass("\\static \\class Test { \\abstract A : \\Type0 \\abstract a : A } \\static \\function test => Test { A => Nat }");
    assertEquals(Universe(1), result.getDefinition("Test").getType());
    assertEquals(Universe(TypeUniverse.SetOfLevel(0)), result.getDefinition("test").getType());
    testType(Universe(TypeUniverse.SetOfLevel(0)), result);
  }

  @Test
  public void lambdaTest() {
    TypeCheckClassResult result = typeCheckClass("\\static \\function test => \\lam (f : Nat -> Nat) => f 0");
    testType(Pi(Pi(Nat(), Nat()), Nat()), result);
  }

  @Test
  public void lambdaTest2() {
    TypeCheckClassResult result = typeCheckClass("\\function test => \\lam (A : \\Type0) (x : A) => x");
    DependentLink A = param("A", Universe(0));
    Expression expectedType = Pi(params(A, param("x", Reference(A))), Reference(A));
    testType(expectedType, result);
  }

  @Test
  public void fieldAccTest() {
    TypeCheckClassResult result = typeCheckClass("\\static \\class C { \\abstract x : Nat \\function f (p : 0 = x) => p } \\static \\function test (p : Nat -> C) => (p 0).f");
    DependentLink p = param("p", Pi(Nat(), ClassCall((ClassDefinition) result.getDefinition("C"))));
    Expression type = FunCall(Prelude.PATH_INFIX).applyLevelSubst(new LevelSubstitution(Prelude.LP, new LevelExpression(0), Prelude.LH, new LevelExpression(1)))
      .addArgument(Nat(), EnumSet.noneOf(AppExpression.Flag.class))
      .addArgument(Zero(), AppExpression.DEFAULT)
      .addArgument(Apps(FieldCall((ClassField) result.getDefinition("C.x")), Apps(Reference(p), Zero())), AppExpression.DEFAULT);
    assertEquals(Pi(p, Pi(type, type)).normalize(NormalizeVisitor.Mode.NF), result.getDefinition("test").getType().normalize(NormalizeVisitor.Mode.NF));
  }

  @Test
  public void tupleTest() {
    TypeCheckClassResult result = typeCheckClass("\\function test : \\Sigma (x y : Nat) (x = y) => (0, 0, path (\\lam _ => 0))");
    DependentLink xy = param(true, vars("x", "y"), Nat());
    testType(Sigma(params(xy, param(Apps(FunCall(Prelude.PATH_INFIX)
        .applyLevelSubst(new LevelSubstitution(Prelude.LP, new LevelExpression(0), Prelude.LH, new LevelExpression(1)))
        .addArgument(Nat(), EnumSet.noneOf(AppExpression.Flag.class)), Reference(xy), Reference(xy.getNext()))))),
        result);
  }

  @Test
  public void letTest() {
    Definition def = typeCheckDef("\\function test => \\lam (F : Nat -> \\Type0) (f : \\Pi (x : Nat) -> F x) => \\let | x => 0 \\in f x");
    DependentLink F = param("F", Pi(Nat(), Universe(0)));
    DependentLink x = param("x", Nat());
    DependentLink f = param("f", Pi(x, Apps(Reference(F), Reference(x))));
    assertEquals(Pi(params(F, f), Apps(Reference(F), Zero())), ((LeafElimTreeNode) ((FunctionDefinition) def).getElimTree()).getExpression().getType().normalize(NormalizeVisitor.Mode.NF));
  }

  @Test
  public void patternConstructor1() {
    TypeCheckClassResult result = typeCheckClass(
        "\\static \\data C (n : Nat) | C (zero) => c1 | C (suc n) => c2 Nat");
    DataDefinition data = (DataDefinition) result.getDefinition("C");
    assertEquals(Apps(DataCall(data), Zero()), data.getConstructor("c1").getType());
    DependentLink params = data.getConstructor("c2").getDataTypeParameters();
    assertEquals(
        Pi(params, Pi(param(Nat()), Apps(DataCall(data), Suc(Reference(params))))),
        data.getConstructor("c2").getType()
    );
  }

  @Test
  public void patternConstructor2() {
    TypeCheckClassResult result = typeCheckClass(
        "\\static \\data Vec \\Type0 Nat | Vec A zero => Nil | Vec A (suc n) => Cons A (Vec A n)" +
        "\\static \\data D (n : Nat) (Vec Nat n) | D zero _ => dzero | D (suc n) _ => done");
    DataDefinition vec = (DataDefinition) result.getDefinition("Vec");
    DataDefinition d = (DataDefinition) result.getDefinition("D");
    assertEquals(
        Pi(d.getConstructor("dzero").getDataTypeParameters(), Apps(DataCall(d), Zero(), Reference(d.getConstructor("dzero").getDataTypeParameters()))),
        d.getConstructor("dzero").getType()
    );
    DependentLink doneParams = d.getConstructor("done").getDataTypeParameters();
    assertEquals(
        Pi(d.getConstructor("done").getDataTypeParameters(), Apps(DataCall(d), Suc(Reference(doneParams)), Reference(doneParams.getNext()))),
        d.getConstructor("done").getType()
    );
    DependentLink consParams = vec.getConstructor("Cons").getDataTypeParameters();
    assertEquals(
        Pi(consParams, Pi(Reference(consParams), Pi(Apps(DataCall(vec), Reference(consParams), Reference(consParams.getNext())), Apps(DataCall(vec), Reference(consParams), Suc(Reference(consParams.getNext())))))),
        vec.getConstructor("Cons").getType()
    );
  }

  @Test
  public void patternConstructor3() {
    TypeCheckClassResult result = typeCheckClass(
        "\\static \\data D | d \\Type0\n" +
        "\\static \\data C D | C (d A) => c A");
    DataDefinition d = (DataDefinition) result.getDefinition("D");
    DataDefinition c = (DataDefinition) result.getDefinition("C");
    DependentLink A = c.getConstructor("c").getDataTypeParameters();
    assertEquals(
        Pi(c.getConstructor("c").getDataTypeParameters(), Pi(Reference(A), Apps(DataCall(c), Apps(ConCall(d.getConstructor("d")), Reference(A))))),
        c.getConstructor("c").getType()
    );
  }

  @Test
  public void patternConstructorDep() {
    TypeCheckClassResult result = typeCheckClass(
        "\\static \\data Box (n : Nat) | box\n" +
        "\\static \\data D (n : Nat) (Box n) | D (zero) _ => d");
    DataDefinition d = (DataDefinition) result.getDefinition("D");
    assertEquals(
        Pi(d.getConstructor("d").getDataTypeParameters(), Apps(DataCall(d), Zero(), Reference(d.getConstructor("d").getDataTypeParameters()))),
        d.getConstructor("d").getType()
    );
  }
}
