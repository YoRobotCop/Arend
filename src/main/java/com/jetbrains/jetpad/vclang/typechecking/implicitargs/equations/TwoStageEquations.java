package com.jetbrains.jetpad.vclang.typechecking.implicitargs.equations;

import com.jetbrains.jetpad.vclang.error.ErrorReporter;
import com.jetbrains.jetpad.vclang.error.ListErrorReporter;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Preprelude;
import com.jetbrains.jetpad.vclang.term.context.binding.Variable;
import com.jetbrains.jetpad.vclang.term.context.binding.inference.DerivedInferenceVariable;
import com.jetbrains.jetpad.vclang.term.context.binding.inference.InferenceVariable;
import com.jetbrains.jetpad.vclang.term.context.binding.inference.LevelInferenceVariable;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.expr.*;
import com.jetbrains.jetpad.vclang.term.expr.sort.Level;
import com.jetbrains.jetpad.vclang.term.expr.sort.Sort;
import com.jetbrains.jetpad.vclang.term.expr.sort.SortMax;
import com.jetbrains.jetpad.vclang.term.expr.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.term.expr.type.Type;
import com.jetbrains.jetpad.vclang.term.expr.visitor.CompareVisitor;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.typechecking.error.SolveEquationsError;
import com.jetbrains.jetpad.vclang.typechecking.error.SolveLevelEquationsError;
import com.jetbrains.jetpad.vclang.typechecking.error.TypeCheckingError;

import java.util.*;

public class TwoStageEquations implements Equations {
  private List<Equation> myEquations;
  private final Map<LevelInferenceVariable, Variable> myBases;
  private final LevelEquations<LevelInferenceVariable> myLevelEquations;
  private final ListErrorReporter myErrorReporter = new ListErrorReporter();

  public TwoStageEquations() {
    myEquations = new ArrayList<>();
    myBases = new HashMap<>();
    myLevelEquations = new LevelEquations<>();
  }

  @Override
  public boolean add(Equations equations) {
    if (equations.isEmpty()) {
      return true;
    }
    if (!(equations instanceof TwoStageEquations)) {
      return false;
    }
    TwoStageEquations eq = (TwoStageEquations) equations;

    myEquations.addAll(eq.myEquations);
    myLevelEquations.add(eq.myLevelEquations);
    for (Map.Entry<LevelInferenceVariable, Variable> entry : eq.myBases.entrySet()) {
      addBase(entry.getKey(), entry.getValue(), entry.getKey().getSourceNode());
    }
    eq.myErrorReporter.reportTo(myErrorReporter);
    return true;
  }

  private void addEquation(Type type, Expression expr, CMP cmp, Abstract.SourceNode sourceNode) {
    InferenceVariable inf1 = type instanceof Expression && ((Expression) type).toInferenceReference() != null ? ((Expression) type).toInferenceReference().getVariable() : null;
    InferenceVariable inf2 = expr.toInferenceReference() != null ? expr.toInferenceReference().getVariable() : null;

    if (inf1 == inf2 && inf1 != null) {
      return;
    }

    if (inf1 != null && inf2 == null || inf2 != null && inf1 == null) {
      InferenceVariable cInf = inf1 != null ? inf1 : inf2;
      Type cType = inf1 != null ? expr : type;

      if (cType instanceof Expression) {
        Expression cExpr = (Expression) cType;
        // TODO: set cmp to CMP.EQ only if cExpr is not stuck on a meta-variable
        if (cExpr.toPi() == null && cExpr.toUniverse() == null && cExpr.toClassCall() == null) {
          cmp = CMP.EQ;
        }
      }

      if (cmp == CMP.EQ) {
        assert cType instanceof Expression;
        solve(cInf, (Expression) cType);
        return;
      }

      if (inf1 != null) {
        cmp = cmp.not();
      }

      DependentLink piParams = cType.getPiParameters();
      if (piParams.hasNext()) {
        Expression newRef = new InferenceReferenceExpression(new DerivedInferenceVariable(cInf.getName() + "-cod", cInf));
        solve(cInf, new PiExpression(piParams, newRef));
        addEquation(cType.getPiCodomain(), newRef, cmp, sourceNode);
        return;
      }

      SortMax sorts = cType.toSorts();
      if (sorts != null) {
        LevelInferenceVariable lpInf = new LevelInferenceVariable(cInf.getName() + "-lp", new DataCallExpression(Preprelude.LVL), cInf.getSourceNode());
        LevelInferenceVariable lhInf = new LevelInferenceVariable(cInf.getName() + "-lh", new DataCallExpression(Preprelude.CNAT), cInf.getSourceNode());
        myLevelEquations.addVariable(lpInf);
        myLevelEquations.addVariable(lhInf);
        Level lp = new Level(lpInf);
        Level lh = new Level(lhInf);
        solve(cInf, new UniverseExpression(new Sort(lp, lh)));
        if (cmp == CMP.LE) {
          sorts.getPLevel().isLessOrEquals(lp, this, sourceNode);
          sorts.getHLevel().isLessOrEquals(lh, this, sourceNode);
        } else {
          Sort sort = sorts.toSort();
          if (!sort.getPLevel().isInfinity()) {
            addLevelEquation(lpInf, sort.getPLevel().getVar(), sort.getPLevel().getConstant(), sourceNode);
          }
          if (!sort.getHLevel().isInfinity()) {
            addLevelEquation(lhInf, sort.getHLevel().getVar(), sort.getHLevel().getConstant(), sourceNode);
          }
        }
        return;
      }
    }

    if (expr.toInferenceReference() == null && type instanceof Expression && ((Expression) type).toInferenceReference() != null) {
      myEquations.add(new Equation(expr, (Expression) type, cmp.not(), sourceNode));
    } else {
      myEquations.add(new Equation(type, expr, cmp, sourceNode));
    }
  }

  private void addBase(LevelInferenceVariable var, Variable base, Abstract.SourceNode sourceNode) {
    Variable base1 = myBases.get(var);
    if (base1 == null) {
      myBases.put(var, base);
    } else {
      if (base != base1) {
        List<LevelEquation<Variable>> equations = new ArrayList<>(2);
        equations.add(new LevelEquation<>(base, var, 0));
        equations.add(new LevelEquation<>(base1, var, 0));
        myErrorReporter.report(new SolveLevelEquationsError(equations, sourceNode));
      }
    }
  }

  private void addLevelEquation(Variable var1, Variable var2, int constant, Abstract.SourceNode sourceNode) {
    if (!(var1 instanceof LevelInferenceVariable) && !(var2 instanceof LevelInferenceVariable)) {
      if (var1 != var2 || constant < 0) {
        myErrorReporter.report(new SolveLevelEquationsError(Collections.singletonList(new LevelEquation<>(var1, var2, constant)), sourceNode));
      }
      return;
    }

    if (var1 != null && var2 instanceof LevelInferenceVariable) {
      Variable base = var1 instanceof LevelInferenceVariable ? myBases.get(var1) : var1;
      if (base != null) {
        addBase((LevelInferenceVariable) var2, base, sourceNode);
      }
    }

    myLevelEquations.addEquation(new LevelEquation<>(var1 instanceof LevelInferenceVariable ? (LevelInferenceVariable) var1 : null, var2 instanceof LevelInferenceVariable ? (LevelInferenceVariable) var2 : null, constant));
  }

  private void addLevelEquation(Variable var, Abstract.SourceNode sourceNode) {
    if (var instanceof LevelInferenceVariable) {
      myLevelEquations.addEquation(new LevelEquation<>((LevelInferenceVariable) var));
    } else {
      myErrorReporter.report(new SolveLevelEquationsError(Collections.singletonList(new LevelEquation<>(var)), sourceNode));
    }
  }

  @Override
  public boolean add(Expression expr1, Expression expr2, CMP cmp, Abstract.SourceNode sourceNode) {
    addEquation(expr1, expr2, cmp, sourceNode);
    return true;
  }

  @Override
  public boolean add(Level level1, Level level2, CMP cmp, Abstract.SourceNode sourceNode) {
    if (level1.isInfinity() && level2.isInfinity() || level1.isInfinity() && cmp == CMP.GE || level2.isInfinity() && cmp == CMP.LE) {
      return true;
    }
    if (level1.isInfinity()) {
      addLevelEquation(level2.getVar(), sourceNode);
      return true;
    }
    if (level2.isInfinity()) {
      addLevelEquation(level1.getVar(), sourceNode);
      return true;
    }

    if (cmp == CMP.LE || cmp == CMP.EQ) {
      addLevelEquation(level1.getVar(), level2.getVar(), level2.getConstant() - level1.getConstant(), sourceNode);
    }
    if (cmp == CMP.GE || cmp == CMP.EQ) {
      addLevelEquation(level2.getVar(), level1.getVar(), level1.getConstant() - level2.getConstant(), sourceNode);
    }
    return true;
  }

  @Override
  public boolean add(Type type, Expression expr, Abstract.SourceNode sourceNode) {
    addEquation(type, expr, CMP.LE, sourceNode);
    return true;
  }

  @Override
  public boolean addVariable(LevelInferenceVariable var) {
    myLevelEquations.addVariable(var);
    return true;
  }

  @Override
  public void clear() {
    myEquations.clear();
    myBases.clear();
    myLevelEquations.clear();
  }

  @Override
  public boolean isEmpty() {
    return myEquations.isEmpty() && myLevelEquations.isEmpty() && myBases.isEmpty() && myErrorReporter.getErrorList().isEmpty();
  }

  @Override
  public Equations newInstance() {
    return new TwoStageEquations();
  }

  @Override
  public LevelSubstitution solve() {
    updateEquations();
    if (solveClassCalls()) {
      updateEquations();
    }

    Map<LevelInferenceVariable, Integer> solution = new HashMap<>();
    LevelInferenceVariable var = myLevelEquations.solve(solution);
    if (var != null) {
      myErrorReporter.report(new SolveLevelEquationsError(new ArrayList<LevelEquation<? extends Variable>>(myLevelEquations.getEquations()), var.getSourceNode()));
    }

    LevelSubstitution result = new LevelSubstitution();
    for (Map.Entry<LevelInferenceVariable, Integer> entry : solution.entrySet()) {
      Integer constant = entry.getValue();
      result.add(entry.getKey(), constant == null ? Level.INFINITY : new Level(myBases.get(entry.getKey()), -constant));
    }
    return result;
  }

  private void updateEquations() {
    List<Equation> equations = myEquations;
    myEquations = new ArrayList<>();
    List<Equation> badEquations = new ArrayList<>();
    for (Equation equation : equations) {
      boolean ok;
      if (equation.cmp == CMP.LE) {
        ok = equation.type.normalize(NormalizeVisitor.Mode.NF).isLessOrEquals(equation.expr.normalize(NormalizeVisitor.Mode.NF), this, equation.sourceNode);
      } else {
        ok = CompareVisitor.compare(this, equation.cmp, (Expression) equation.type.normalize(NormalizeVisitor.Mode.NF), equation.expr.normalize(NormalizeVisitor.Mode.NF), equation.sourceNode);
      }
      if (!ok) {
        badEquations.add(equation);
      }
    }
    if (!badEquations.isEmpty()) {
      myErrorReporter.report(new SolveEquationsError(badEquations, badEquations.get(0).sourceNode));
    }
  }

  private boolean solveClassCalls() {
    List<Equation> lowerBounds = new ArrayList<>(myEquations.size());
    for (Iterator<Equation> iterator = myEquations.iterator(); iterator.hasNext(); ) {
      Equation equation = iterator.next();
      if (equation.expr.toInferenceReference() != null && equation.expr.toInferenceReference().getSubstExpression() == null && equation.type instanceof Expression) {
        Expression expr = (Expression) equation.type;
        if (expr.toInferenceReference() != null && expr.toInferenceReference().getSubstExpression() == null || expr.toClassCall() != null && !(equation.cmp == CMP.GE && expr.toClassCall() != null)) {
          if (equation.cmp == CMP.LE) {
            lowerBounds.add(equation);
          } else if (equation.cmp == CMP.GE) {
            lowerBounds.add(new Equation(equation.expr, expr, CMP.LE, equation.sourceNode));
          } else {
            lowerBounds.add(new Equation(equation.type, equation.expr, CMP.LE, equation.sourceNode));
            lowerBounds.add(new Equation(equation.expr, expr, CMP.LE, equation.sourceNode));
          }
          iterator.remove();
        }
      }
    }

    boolean updated = solveClassCallLowerBounds(lowerBounds);

    Map<InferenceVariable, Expression> result = new HashMap<>();
    for (Iterator<Equation> iterator = myEquations.iterator(); iterator.hasNext(); ) {
      Equation equation = iterator.next();
      if (equation.expr.toInferenceReference() != null && equation.expr.toInferenceReference().getSubstExpression() == null && equation.type instanceof Expression) {
        Expression newResult = (Expression) equation.type;
        if (newResult.toInferenceReference() != null && newResult.toInferenceReference().getSubstExpression() == null || newResult.toClassCall() != null && equation.cmp == CMP.GE && newResult.toClassCall() != null) {
          InferenceVariable var = equation.expr.toInferenceReference().getVariable();
          Expression oldResult = result.get(var);
          if (oldResult == null || newResult.isLessOrEquals(oldResult, DummyEquations.getInstance(), var.getSourceNode())) {
            result.put(var, newResult);
          } else
          if (!oldResult.isLessOrEquals(newResult, DummyEquations.getInstance(), var.getSourceNode())) {
            List<Equation> eqs = new ArrayList<>(2);
            eqs.add(new Equation(equation.expr, oldResult, CMP.LE, var.getSourceNode()));
            eqs.add(new Equation(equation.expr, newResult, CMP.LE, var.getSourceNode()));
            myErrorReporter.report(new SolveEquationsError(eqs, var.getSourceNode()));
          }
          iterator.remove();
        }
      }
    }

    for (Map.Entry<InferenceVariable, Expression> entry : result.entrySet()) {
      solve(entry.getKey(), entry.getValue());
    }

    return updated || !result.isEmpty();
  }

  private boolean solveClassCallLowerBounds(List<Equation> lowerBounds) {
    Map<InferenceVariable, Expression> solutions = new HashMap<>();
    while (true) {
      boolean updated = false;
      for (Equation equation : lowerBounds) {
        Expression newSolution = (Expression) equation.type;
        if (newSolution.toInferenceReference() != null && newSolution.toInferenceReference().getSubstExpression() == null) {
          newSolution = solutions.get(newSolution.toInferenceReference().getVariable());
        }
        if (newSolution != null) {
          InferenceVariable var = equation.expr.toInferenceReference().getVariable();
          Expression oldSolution = solutions.get(var);
          if (oldSolution == null) {
            solutions.put(var, newSolution);
            updated = true;
          } else {
            if (!newSolution.isLessOrEquals(oldSolution, DummyEquations.getInstance(), var.getSourceNode())) {
              if (oldSolution.isLessOrEquals(newSolution, DummyEquations.getInstance(), var.getSourceNode())) {
                solutions.put(var, newSolution);
                updated = true;
              } else {
                List<Equation> eqs = new ArrayList<>(2);
                eqs.add(new Equation(oldSolution, equation.expr, CMP.LE, var.getSourceNode()));
                eqs.add(new Equation(newSolution, equation.expr, CMP.LE, var.getSourceNode()));
                myErrorReporter.report(new SolveEquationsError(eqs, var.getSourceNode()));
              }
            }
          }
        }
      }
      if (!updated) {
        break;
      }
    }

    for (Map.Entry<InferenceVariable, Expression> entry : solutions.entrySet()) {
      solve(entry.getKey(), entry.getValue());
    }

    return !solutions.isEmpty();
  }

  private boolean solve(InferenceVariable var, Expression expr) {
    if (expr.findBinding(var)) {
      TypeCheckingError error = var.getErrorInfer(expr);
      myErrorReporter.report(error);
      var.getReference().setSubstExpression(new ErrorExpression(expr, error));
      return false;
    }

    Expression expectedType = var.getType();
    Type actualType = expr.getType();
    if (!actualType.isLessOrEquals(expectedType.normalize(NormalizeVisitor.Mode.NF), this, var.getSourceNode())) {
      actualType = actualType.normalize(NormalizeVisitor.Mode.HUMAN_NF);
      TypeCheckingError error = var.getErrorMismatch(expectedType.normalize(NormalizeVisitor.Mode.HUMAN_NF), actualType, expr);
      myErrorReporter.report(error);
      var.getReference().setSubstExpression(new ErrorExpression(expr, error));
      return false;
    } else {
      var.getReference().setSubstExpression(new OfTypeExpression(expr, expectedType));
      return true;
    }
  }

  @Override
  public void reportErrors(ErrorReporter errorReporter, Abstract.SourceNode sourceNode) {
    myErrorReporter.reportTo(errorReporter);

    if (!myEquations.isEmpty()) {
      errorReporter.report(new SolveEquationsError(new ArrayList<>(myEquations), sourceNode));
    }

    myErrorReporter.getErrorList().clear();
    clear();
  }
}
