package com.jetbrains.jetpad.vclang.term.definition;

import com.jetbrains.jetpad.vclang.module.Namespace;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.FunCallExpression;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.ElimTreeNode;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.FunCall;

public class FunctionDefinition extends Definition implements Function {
  // TODO: myArguments should have type List<TypeArguments>
  private DependentLink myParameters;
  private Expression myResultType;
  private ElimTreeNode myElimTree;
  private boolean myTypeHasErrors;

  public FunctionDefinition(Namespace parentNamespace, Name name, Abstract.Definition.Precedence precedence) {
    super(parentNamespace, name, precedence);
    myTypeHasErrors = true;
  }

  public FunctionDefinition(Namespace parentNamespace, Name name, Abstract.Definition.Precedence precedence, DependentLink parameters, Expression resultType, ElimTreeNode elimTree) {
    super(parentNamespace, name, precedence);
    setUniverse(new Universe.Type(0, Universe.Type.PROP));
    hasErrors(false);
    myParameters = parameters;
    myResultType = resultType;
    myTypeHasErrors = false;
    myElimTree = elimTree;
  }

  public Namespace getStaticNamespace() {
    return getParentNamespace().getChild(getName());
  }

  @Override
  public ElimTreeNode getElimTree() {
    return myElimTree;
  }

  @Override
  public boolean isAbstract() {
    return myElimTree == null;
  }

  public void setElimTree(ElimTreeNode elimTree) {
    myElimTree = elimTree;
  }

  @Override
  public DependentLink getParameters() {
    return myParameters;
  }

  public void setParameters(DependentLink parameters) {
    myParameters = parameters;
  }

  @Override
  public Expression getResultType() {
    return myResultType;
  }

  public void setResultType(Expression resultType) {
    myResultType = resultType;
  }

  public boolean typeHasErrors() {
    return myTypeHasErrors;
  }

  public void typeHasErrors(boolean has) {
    myTypeHasErrors = has;
  }

  @Override
  public Expression getBaseType() {
    if (myTypeHasErrors) {
      return null;
    }
    return Function.Helper.getFunctionType(this);
  }

  @Override
  public FunCallExpression getDefCall() {
    return FunCall(this);
  }
}
