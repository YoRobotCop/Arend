package com.jetbrains.jetpad.vclang.core.context.binding.inference;

import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Concrete;
import com.jetbrains.jetpad.vclang.typechecking.error.local.ArgInferenceError;
import com.jetbrains.jetpad.vclang.typechecking.error.local.LocalTypeCheckingError;
import com.jetbrains.jetpad.vclang.typechecking.typeclass.pool.ClassViewInstancePool;

import java.util.Set;

public class TypeClassInferenceVariable<T> extends InferenceVariable<T> {
  private final Abstract.ClassView myClassView;
  private final boolean isView;

  public TypeClassInferenceVariable(String name, Expression type, Abstract.ClassView classView, boolean isView, Concrete.SourceNode<T> sourceNode, Set<Binding> bounds) {
    super(name, type, sourceNode, bounds);
    myClassView = classView;
    this.isView = isView;
  }

  public Abstract.ClassView getClassView() {
    return myClassView;
  }

  @Override
  public LocalTypeCheckingError<T> getErrorInfer(Expression... candidates) {
    return new ArgInferenceError<>(ArgInferenceError.typeClass(), getSourceNode(), candidates);
  }

  @Override
  public LocalTypeCheckingError<T> getErrorMismatch(Expression expectedType, Expression actualType, Expression candidate) {
    return new ArgInferenceError<>(ArgInferenceError.typeClass(), expectedType, actualType, getSourceNode(), candidate);
  }

  public Expression getInstance(ClassViewInstancePool pool, Expression classifyingExpression) {
    return pool.getInstance(classifyingExpression, myClassView, isView);
  }
}
