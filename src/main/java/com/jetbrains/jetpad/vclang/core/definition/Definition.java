package com.jetbrains.jetpad.vclang.core.definition;

import com.jetbrains.jetpad.vclang.core.context.binding.Variable;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.EmptyDependentLink;
import com.jetbrains.jetpad.vclang.core.expr.DefCallExpression;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.term.Concrete;

import java.util.List;

public abstract class Definition implements Variable {
  private ClassDefinition myThisClass;
  private Concrete.Definition<?> myConcreteDefinition;
  private TypeCheckingStatus myStatus;

  public Definition(Concrete.Definition<?> abstractDef, TypeCheckingStatus status) {
    myConcreteDefinition = abstractDef;
    myStatus = status;
  }

  @Override
  public String getName() {
    return myConcreteDefinition.getName();
  }

  public Concrete.Definition<?> getConcreteDefinition() {
    return myConcreteDefinition;
  }

  public DependentLink getParameters() {
    return EmptyDependentLink.getInstance();
  }

  public abstract Expression getTypeWithParams(List<? super DependentLink> params, Sort sortArgument);

  public abstract DefCallExpression getDefCall(Sort sortArgument, Expression thisExpr, List<Expression> args);

  public ClassDefinition getThisClass() {
    return myThisClass;
  }

  public void setThisClass(ClassDefinition enclosingClass) {
    myThisClass = enclosingClass;
  }

  public enum TypeCheckingStatus {
    HEADER_HAS_ERRORS, BODY_HAS_ERRORS, HEADER_NEEDS_TYPE_CHECKING, BODY_NEEDS_TYPE_CHECKING, HAS_ERRORS, NO_ERRORS;

    public boolean bodyIsOK() {
      return this == HAS_ERRORS || this == NO_ERRORS;
    }

    public boolean headerIsOK() {
      return this != HEADER_HAS_ERRORS && this != HEADER_NEEDS_TYPE_CHECKING;
    }

    public boolean needsTypeChecking() {
      return this == HEADER_NEEDS_TYPE_CHECKING || this == BODY_NEEDS_TYPE_CHECKING;
    }
  }

  public TypeCheckingStatus status() {
    return myStatus;
  }

  public void setStatus(TypeCheckingStatus status) {
    myStatus = status;
  }

  @Override
  public String toString() {
    return myConcreteDefinition.toString();
  }

  public static Definition newDefinition(Concrete.Definition<?> definition) {
    if (definition instanceof Concrete.DataDefinition) {
      return new DataDefinition((Concrete.DataDefinition) definition);
    }
    if (definition instanceof Concrete.FunctionDefinition || definition instanceof Concrete.ClassViewInstance) {
      return new FunctionDefinition(definition);
    }
    if (definition instanceof Concrete.ClassDefinition) {
      return new ClassDefinition((Concrete.ClassDefinition) definition);
    }
    return null;
  }
}
