package org.arend.typechecking.implicitargs.equations;

import org.arend.core.context.binding.inference.InferenceLevelVariable;
import org.arend.core.context.binding.inference.InferenceVariable;
import org.arend.core.expr.Expression;
import org.arend.core.sort.Level;
import org.arend.core.subst.LevelSubstitution;
import org.arend.term.concrete.Concrete;

public interface Equations {
  boolean addEquation(Expression expr1, Expression expr2, CMP cmp, Concrete.SourceNode sourceNode, InferenceVariable stuckVar1, InferenceVariable stuckVar2);
  boolean solve(Expression type, Expression expr, CMP cmp, Concrete.SourceNode sourceNode);
  boolean addEquation(Level level1, Level level2, CMP cmp, Concrete.SourceNode sourceNode);
  boolean addVariable(InferenceLevelVariable var);
  void bindVariables(InferenceLevelVariable pVar, InferenceLevelVariable hVar);
  boolean remove(Equation equation);
  LevelSubstitution solve(Concrete.SourceNode sourceNode);
  boolean isDummy();

  enum CMP {
    LE, EQ, GE;

    public CMP not() {
      if (this == LE) return GE;
      if (this == GE) return LE;
      return this;
    }
  }
}
