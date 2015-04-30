package com.jetbrains.jetpad.vclang.term.error;

import com.jetbrains.jetpad.vclang.term.Abstract;

public class TypeMismatchError extends TypeCheckingError {
  private final Object expected;
  private final Abstract.Expression actual;

  public TypeMismatchError(Object expected, Abstract.Expression actual, Abstract.Expression expression) {
    super(null, expression);
    this.expected = expected;
    this.actual = actual;
  }

  @Override
  public String toString() {
    String message = "Type mismatch:\n" +
        "Expected type: " + expected + "\n" +
        "Actual type: " + actual;
    if (getExpression() != null) {
      message += "\n" +
          "In expression: " + getExpression();
    }
    return message;
  }
}
