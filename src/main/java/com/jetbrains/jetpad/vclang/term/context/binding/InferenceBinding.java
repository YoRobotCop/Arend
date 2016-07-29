package com.jetbrains.jetpad.vclang.term.context.binding;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.LevelExpression;
import com.jetbrains.jetpad.vclang.error.ErrorReporter;


public abstract class InferenceBinding extends TypedBinding {
  public InferenceBinding(String name, Expression type) {
    super(name, type);
  }

  public void setType(Expression type) {
    myType = type;
  }

  public abstract Abstract.SourceNode getSourceNode();

  public abstract void reportErrorInfer(ErrorReporter errorReporter, Expression... candidates);
  public abstract void reportErrorLevelInfer(ErrorReporter errorReporter, LevelExpression... candidates);
  public abstract void reportErrorMismatch(ErrorReporter errorReporter, Expression expectedType, Expression actualType, Expression candidate);
}
