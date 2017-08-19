package com.jetbrains.jetpad.vclang.typechecking;

import com.jetbrains.jetpad.vclang.term.Concrete;

public class Typecheckable<T> {
  private final Concrete.Definition<T> myDefinition;
  private final boolean myHeader;

  public Typecheckable(Concrete.Definition<T> definition, boolean isHeader) {
    assert !isHeader || hasHeader(definition);
    this.myDefinition = definition;
    this.myHeader = isHeader;
  }

  public Concrete.Definition<T> getDefinition() {
    return myDefinition;
  }

  public boolean isHeader() {
    return myHeader;
  }

  public static boolean hasHeader(Concrete.Definition definition) {
    return definition instanceof Concrete.FunctionDefinition || definition instanceof Concrete.DataDefinition;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Typecheckable that = (Typecheckable) o;
    return myHeader == that.myHeader && myDefinition.equals(that.myDefinition);
  }

  @Override
  public int hashCode() {
    int result = myDefinition.hashCode();
    result = 31 * result + (myHeader ? 1 : 0);
    return result;
  }
}