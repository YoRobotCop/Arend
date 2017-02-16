package com.jetbrains.jetpad.vclang.typechecking.order;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.typechecking.Typecheckable;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckingUnit;

public class BaseDependencyListener implements DependencyListener {
  @Override
  public void sccFound(SCC scc) {

  }

  @Override
  public void unitFound(TypecheckingUnit unit, Recursion recursion) {

  }

  @Override
  public boolean needsOrdering(Abstract.Definition definition) {
    return true;
  }

  @Override
  public void alreadyTypechecked(Abstract.Definition definition) {

  }

  @Override
  public void dependsOn(Typecheckable unit, Abstract.Definition def) {

  }
}
