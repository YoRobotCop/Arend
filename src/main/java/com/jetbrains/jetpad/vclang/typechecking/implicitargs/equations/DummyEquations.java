package com.jetbrains.jetpad.vclang.typechecking.implicitargs.equations;

import com.jetbrains.jetpad.vclang.core.context.binding.inference.InferenceLevelVariable;
import com.jetbrains.jetpad.vclang.core.context.binding.inference.InferenceVariable;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.sort.Level;
import com.jetbrains.jetpad.vclang.core.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.term.Concrete;

public class DummyEquations<T> implements Equations<T> {
  private static final DummyEquations INSTANCE = new DummyEquations();

  private DummyEquations() {}

  public static <T> DummyEquations<T> getInstance() {
    //noinspection unchecked
    return INSTANCE;
  }

  @Override
  public boolean add(Expression expr1, Expression expr2, CMP cmp, Concrete.SourceNode<T> sourceNode, InferenceVariable<T> stuckVar) {
    return false;
  }

  @Override
  public boolean solve(Expression type, Expression expr, CMP cmp, Concrete.SourceNode<T> sourceNode) {
    return false;
  }

  @Override
  public boolean add(Level expr1, Level expr2, CMP cmp, Concrete.SourceNode<T> sourceNode) {
    return false;
  }

  @Override
  public boolean addVariable(InferenceLevelVariable var) {
    return false;
  }

  @Override
  public void bindVariables(InferenceLevelVariable pVar, InferenceLevelVariable hVar) {

  }

  @Override
  public void remove(Equation equation) {

  }

  @Override
  public LevelSubstitution solve(Concrete.SourceNode<T> sourceNode) {
    return LevelSubstitution.EMPTY;
  }
}
