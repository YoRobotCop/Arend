package org.arend.module.serialization;

import com.google.protobuf.ByteString;
import org.arend.core.context.binding.Binding;
import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.TypedDependentLink;
import org.arend.core.definition.ClassField;
import org.arend.core.definition.Constructor;
import org.arend.core.elimtree.BranchElimTree;
import org.arend.core.elimtree.ElimTree;
import org.arend.core.elimtree.LeafElimTree;
import org.arend.core.expr.*;
import org.arend.core.expr.let.LetClause;
import org.arend.core.expr.let.LetClausePattern;
import org.arend.core.expr.type.Type;
import org.arend.core.expr.type.TypeExpression;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.error.Error;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ExpressionSerialization implements ExpressionVisitor<Void, ExpressionProtos.Expression> {
  private final CallTargetIndexProvider myCallTargetIndexProvider;
  private final List<Binding> myBindings = new ArrayList<>();  // de Bruijn indices
  private final Map<Binding, Integer> myBindingsMap = new HashMap<>();

  ExpressionSerialization(CallTargetIndexProvider callTargetIndexProvider) {
    myCallTargetIndexProvider = callTargetIndexProvider;
  }


  // Bindings

  private RollbackBindings checkpointBindings() {
    return new RollbackBindings(myBindings.size());
  }

  private class RollbackBindings implements AutoCloseable {
    private final int myTargetSize;

    private RollbackBindings(int targetSize) {
      myTargetSize = targetSize;
    }

    @Override
    public void close() {
      for (int i = myBindings.size() - 1; i >= myTargetSize; i--) {
        myBindingsMap.remove(myBindings.remove(i));
      }
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  private int registerBinding(Binding binding) {
    int index = myBindings.size();
    myBindings.add(binding);
    myBindingsMap.put(binding, index);
    return index;
  }

  private ExpressionProtos.Type writeType(Type type) {
    ExpressionProtos.Type.Builder builder = ExpressionProtos.Type.newBuilder();
    builder.setExpr(writeExpr(type.getExpr()));
    if (type instanceof TypeExpression) {
      builder.setSort(writeSort(type.getSortOfType()));
    }
    return builder.build();
  }

  private int writeBindingRef(Binding binding) {
    if (binding == null) {
      return 0;
    } else {
      Integer ref = myBindingsMap.get(binding);
      return ref + 1;  // zero is reserved for null
    }
  }

  // Sorts and levels

  LevelProtos.Level writeLevel(Level level) {
    // Level.INFINITY should be read with great care
    LevelProtos.Level.Builder builder = LevelProtos.Level.newBuilder();
    if (level.getVar() == null) {
      builder.setVariable(LevelProtos.Level.Variable.NO_VAR);
    } else if (level.getVar() == LevelVariable.PVAR) {
      builder.setVariable(LevelProtos.Level.Variable.PLVL);
    } else if (level.getVar() == LevelVariable.HVAR) {
      builder.setVariable(LevelProtos.Level.Variable.HLVL);
    } else {
      throw new IllegalStateException();
    }
    builder.setConstant(level.getConstant());
    builder.setMaxConstant(level.getMaxConstant());
    return builder.build();
  }

  LevelProtos.Sort writeSort(Sort sort) {
    LevelProtos.Sort.Builder builder = LevelProtos.Sort.newBuilder();
    builder.setPLevel(writeLevel(sort.getPLevel()));
    builder.setHLevel(writeLevel(sort.getHLevel()));
    return builder.build();
  }


  // Parameters

  List<ExpressionProtos.Telescope> writeParameters(DependentLink link) {
    List<ExpressionProtos.Telescope> out = new ArrayList<>();
    for (; link.hasNext(); link = link.getNext()) {
      out.add(writeSingleParameter(link));
      link = link.getNextTyped(null);
    }
    return out;
  }

  private ExpressionProtos.Telescope writeSingleParameter(DependentLink link) {
    ExpressionProtos.Telescope.Builder tBuilder = ExpressionProtos.Telescope.newBuilder();
    List<String> names = new ArrayList<>();
    TypedDependentLink typed = link.getNextTyped(names);
    List<String> fixedNames = new ArrayList<>(names.size());
    for (String name : names) {
      if (name != null && name.isEmpty()) {
        throw new IllegalArgumentException();
      }
      fixedNames.add(name == null ? "" : name);
    }
    tBuilder.addAllName(fixedNames);
    tBuilder.setIsNotExplicit(!typed.isExplicit());
    tBuilder.setIsHidden(typed.isHidden());
    if (typed.getType() != null) tBuilder.setType(writeType(typed.getType()));
    for (; link != typed; link = link.getNext()) {
      registerBinding(link);
    }
    registerBinding(typed);
    return tBuilder.build();
  }

  ExpressionProtos.SingleParameter writeParameter(DependentLink link) {
    ExpressionProtos.SingleParameter.Builder builder = ExpressionProtos.SingleParameter.newBuilder();
    if (link.getName() != null) {
      builder.setName(link.getName());
    }
    builder.setIsNotExplicit(!link.isExplicit());
    if (link instanceof TypedDependentLink) {
      builder.setType(writeType(link.getType()));
    }
    builder.setIsHidden(link.isHidden());
    registerBinding(link);
    return builder.build();
  }


  // Types, Expressions and ElimTrees

  ExpressionProtos.Expression writeExpr(Expression expr) {
    return expr.accept(this, null);
  }

  ExpressionProtos.ElimTree writeElimTree(ElimTree elimTree) {
    ExpressionProtos.ElimTree.Builder builder = ExpressionProtos.ElimTree.newBuilder();
    builder.addAllParam(writeParameters(elimTree.getParameters()));

    if (elimTree instanceof LeafElimTree) {
      ExpressionProtos.ElimTree.Leaf.Builder leafBuilder = ExpressionProtos.ElimTree.Leaf.newBuilder();
      leafBuilder.setExpr(((LeafElimTree) elimTree).getExpression().accept(this, null));
      builder.setLeaf(leafBuilder);
    } else {
      BranchElimTree branchElimTree = (BranchElimTree) elimTree;
      ExpressionProtos.ElimTree.Branch.Builder branchBuilder = ExpressionProtos.ElimTree.Branch.newBuilder();

      for (Map.Entry<Constructor, ElimTree> entry : branchElimTree.getChildren()) {
        if (entry.getKey() == null) {
          branchBuilder.setNullClause(writeElimTree(entry.getValue()));
        } else if (entry.getKey() instanceof BranchElimTree.TupleConstructor) {
          ExpressionProtos.ElimTree.Branch.TupleClause.Builder tupleClauseBuilder = ExpressionProtos.ElimTree.Branch.TupleClause.newBuilder();
          tupleClauseBuilder.setLength(((BranchElimTree.TupleConstructor) entry.getKey()).getLength());
          tupleClauseBuilder.setIsClassInstance(((BranchElimTree.TupleConstructor) entry.getKey()).isClassInstance());
          tupleClauseBuilder.setElimTree(writeElimTree(entry.getValue()));
          branchBuilder.setTupleClause(tupleClauseBuilder.build());
        } else {
          branchBuilder.putClauses(myCallTargetIndexProvider.getDefIndex(entry.getKey()), writeElimTree(entry.getValue()));
        }
      }

      builder.setBranch(branchBuilder);
    }

    return builder.build();
  }


  @Override
  public ExpressionProtos.Expression visitApp(AppExpression expr, Void params) {
    ExpressionProtos.Expression.App.Builder builder = ExpressionProtos.Expression.App.newBuilder();
    builder.setFunction(expr.getFunction().accept(this, null));
    builder.setArgument(expr.getArgument().accept(this, null));
    return ExpressionProtos.Expression.newBuilder().setApp(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitFunCall(FunCallExpression expr, Void params) {
    ExpressionProtos.Expression.FunCall.Builder builder = ExpressionProtos.Expression.FunCall.newBuilder();
    builder.setFunRef(myCallTargetIndexProvider.getDefIndex(expr.getDefinition()));
    builder.setPLevel(writeLevel(expr.getSortArgument().getPLevel()));
    builder.setHLevel(writeLevel(expr.getSortArgument().getHLevel()));
    for (Expression arg : expr.getDefCallArguments()) {
      builder.addArgument(arg.accept(this, null));
    }
    return ExpressionProtos.Expression.newBuilder().setFunCall(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitConCall(ConCallExpression expr, Void params) {
    ExpressionProtos.Expression.ConCall.Builder builder = ExpressionProtos.Expression.ConCall.newBuilder();
    builder.setConstructorRef(myCallTargetIndexProvider.getDefIndex(expr.getDefinition()));
    builder.setPLevel(writeLevel(expr.getSortArgument().getPLevel()));
    builder.setHLevel(writeLevel(expr.getSortArgument().getHLevel()));
    for (Expression arg : expr.getDataTypeArguments()) {
      builder.addDatatypeArgument(arg.accept(this, null));
    }
    for (Expression arg : expr.getDefCallArguments()) {
      builder.addArgument(arg.accept(this, null));
    }
    return ExpressionProtos.Expression.newBuilder().setConCall(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitDataCall(DataCallExpression expr, Void params) {
    ExpressionProtos.Expression.DataCall.Builder builder = ExpressionProtos.Expression.DataCall.newBuilder();
    builder.setDataRef(myCallTargetIndexProvider.getDefIndex(expr.getDefinition()));
    builder.setPLevel(writeLevel(expr.getSortArgument().getPLevel()));
    builder.setHLevel(writeLevel(expr.getSortArgument().getHLevel()));
    for (Expression arg : expr.getDefCallArguments()) {
      builder.addArgument(arg.accept(this, null));
    }
    return ExpressionProtos.Expression.newBuilder().setDataCall(builder).build();
  }

  private ExpressionProtos.Expression.ClassCall writeClassCall(ClassCallExpression expr) {
    ExpressionProtos.Expression.ClassCall.Builder builder = ExpressionProtos.Expression.ClassCall.newBuilder();
    builder.setClassRef(myCallTargetIndexProvider.getDefIndex(expr.getDefinition()));
    builder.setPLevel(writeLevel(expr.getSortArgument().getPLevel()));
    builder.setHLevel(writeLevel(expr.getSortArgument().getHLevel()));
    for (Map.Entry<ClassField, Expression> entry : expr.getImplementedHere().entrySet()) {
      builder.putFieldSet(myCallTargetIndexProvider.getDefIndex(entry.getKey()), writeExpr(entry.getValue()));
    }
    builder.setSort(writeSort(expr.getSort()));
    builder.setHasUniverses(expr.hasUniverses());
    return builder.build();
  }

  @Override
  public ExpressionProtos.Expression visitClassCall(ClassCallExpression expr, Void params) {
    return ExpressionProtos.Expression.newBuilder().setClassCall(writeClassCall(expr)).build();
  }

  @Override
  public ExpressionProtos.Expression visitReference(ReferenceExpression expr, Void params) {
    ExpressionProtos.Expression.Reference.Builder builder = ExpressionProtos.Expression.Reference.newBuilder();
    builder.setBindingRef(writeBindingRef(expr.getBinding()));
    return ExpressionProtos.Expression.newBuilder().setReference(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitInferenceReference(InferenceReferenceExpression expr, Void params) {
    throw new IllegalStateException();
  }

  @Override
  public ExpressionProtos.Expression visitLam(LamExpression expr, Void params) {
    ExpressionProtos.Expression.Lam.Builder builder = ExpressionProtos.Expression.Lam.newBuilder();
    builder.setResultSort(writeSort(expr.getResultSort()));
    builder.setParam(writeSingleParameter(expr.getParameters()));
    builder.setBody(expr.getBody().accept(this, null));
    return ExpressionProtos.Expression.newBuilder().setLam(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitPi(PiExpression expr, Void params) {
    ExpressionProtos.Expression.Pi.Builder builder = ExpressionProtos.Expression.Pi.newBuilder();
    builder.setResultSort(LevelProtos.Sort.newBuilder(writeSort(expr.getResultSort())));
    builder.setParam(writeSingleParameter(expr.getParameters()));
    builder.setCodomain(expr.getCodomain().accept(this, null));
    return ExpressionProtos.Expression.newBuilder().setPi(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitUniverse(UniverseExpression expr, Void params) {
    ExpressionProtos.Expression.Universe.Builder builder = ExpressionProtos.Expression.Universe.newBuilder();
    builder.setSort(writeSort(expr.getSort()));
    return ExpressionProtos.Expression.newBuilder().setUniverse(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitError(ErrorExpression expr, Void params) {
    ExpressionProtos.Expression.Error.Builder builder = ExpressionProtos.Expression.Error.newBuilder();
    if (expr.getExpression() != null && expr.getError() != null && expr.getError().level == Error.Level.GOAL) {
      builder.setExpression(expr.getExpression().accept(this, null));
    }
    return ExpressionProtos.Expression.newBuilder().setError(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitTuple(TupleExpression expr, Void params) {
    ExpressionProtos.Expression.Tuple.Builder builder = ExpressionProtos.Expression.Tuple.newBuilder();
    for (Expression field : expr.getFields()) {
      builder.addField(field.accept(this, null));
    }
    builder.setType(writeSigma(expr.getSigmaType()));
    return ExpressionProtos.Expression.newBuilder().setTuple(builder).build();
  }

  private ExpressionProtos.Expression.Sigma writeSigma(SigmaExpression sigma) {
    ExpressionProtos.Expression.Sigma.Builder builder = ExpressionProtos.Expression.Sigma.newBuilder();
    builder.setPLevel(LevelProtos.Level.newBuilder(writeLevel(sigma.getSort().getPLevel())).build());
    builder.setHLevel(LevelProtos.Level.newBuilder(writeLevel(sigma.getSort().getHLevel())).build());
    builder.addAllParam(writeParameters(sigma.getParameters()));
    return builder.build();
  }

  @Override
  public ExpressionProtos.Expression visitSigma(SigmaExpression expr, Void params) {
    return ExpressionProtos.Expression.newBuilder().setSigma(writeSigma(expr)).build();
  }

  @Override
  public ExpressionProtos.Expression visitProj(ProjExpression expr, Void params) {
    ExpressionProtos.Expression.Proj.Builder builder = ExpressionProtos.Expression.Proj.newBuilder();
    builder.setExpression(expr.getExpression().accept(this, null));
    builder.setField(expr.getField());
    return ExpressionProtos.Expression.newBuilder().setProj(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitNew(NewExpression expr, Void params) {
    ExpressionProtos.Expression.New.Builder builder = ExpressionProtos.Expression.New.newBuilder();
    builder.setClassCall(writeClassCall(expr.getExpression()));
    return ExpressionProtos.Expression.newBuilder().setNew(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitLet(LetExpression letExpression, Void params) {
    ExpressionProtos.Expression.Let.Builder builder = ExpressionProtos.Expression.Let.newBuilder();
    builder.setIsStrict(letExpression.isStrict());
    for (LetClause letClause : letExpression.getClauses()) {
      builder.addClause(ExpressionProtos.Expression.Let.Clause.newBuilder()
        .setName(letClause.getName())
        .setPattern(writeLetClausePattern(letClause.getPattern()))
        .setExpression(writeExpr(letClause.getExpression())));
      registerBinding(letClause);
    }
    builder.setExpression(letExpression.getExpression().accept(this, null));
    return ExpressionProtos.Expression.newBuilder().setLet(builder).build();
  }

  private ExpressionProtos.Expression.Let.Pattern writeLetClausePattern(LetClausePattern pattern) {
    ExpressionProtos.Expression.Let.Pattern.Builder builder = ExpressionProtos.Expression.Let.Pattern.newBuilder();
    if (pattern.isMatching()) {
      List<? extends ClassField> fields = pattern.getFields();
      if (fields != null) {
        builder.setKind(ExpressionProtos.Expression.Let.Pattern.Kind.RECORD);
        for (ClassField field : fields) {
          builder.addField(myCallTargetIndexProvider.getDefIndex(field));
        }
      } else {
        builder.setKind(ExpressionProtos.Expression.Let.Pattern.Kind.TUPLE);
      }
      for (LetClausePattern subPattern : pattern.getPatterns()) {
        builder.addPattern(writeLetClausePattern(subPattern));
      }
    } else {
      builder.setKind(ExpressionProtos.Expression.Let.Pattern.Kind.NAME);
      builder.setName(pattern.getName());
    }
    return builder.build();
  }

  @Override
  public ExpressionProtos.Expression visitCase(CaseExpression expr, Void params) {
    ExpressionProtos.Expression.Case.Builder builder = ExpressionProtos.Expression.Case.newBuilder();
    builder.setElimTree(writeElimTree(expr.getElimTree()));
    builder.addAllParam(writeParameters(expr.getParameters()));
    builder.setResultType(writeExpr(expr.getResultType()));
    if (expr.getResultTypeLevel() != null) {
      builder.setResultTypeLevel(writeExpr(expr.getResultTypeLevel()));
    }
    for (Expression argument : expr.getArguments()) {
      builder.addArgument(writeExpr(argument));
    }
    return ExpressionProtos.Expression.newBuilder().setCase(builder).build();
  }

  @Override
  public ExpressionProtos.Expression visitOfType(OfTypeExpression expr, Void params) {
    throw new IllegalStateException();
  }

  @Override
  public ExpressionProtos.Expression visitInteger(IntegerExpression expr, Void params) {
    if (expr instanceof SmallIntegerExpression) {
      ExpressionProtos.Expression.SmallInteger.Builder builder = ExpressionProtos.Expression.SmallInteger.newBuilder();
      builder.setValue(((SmallIntegerExpression) expr).getInteger());
      return ExpressionProtos.Expression.newBuilder().setSmallInteger(builder).build();
    } else {
      ExpressionProtos.Expression.BigInteger.Builder builder = ExpressionProtos.Expression.BigInteger.newBuilder();
      builder.setValue(ByteString.copyFrom(expr.getBigInteger().toByteArray()));
      return ExpressionProtos.Expression.newBuilder().setBigInteger(builder).build();
    }
  }

  @Override
  public ExpressionProtos.Expression visitFieldCall(FieldCallExpression expr, Void params) {
    ExpressionProtos.Expression.FieldCall.Builder builder = ExpressionProtos.Expression.FieldCall.newBuilder();
    builder.setFieldRef(myCallTargetIndexProvider.getDefIndex(expr.getDefinition()));
    builder.setPLevel(writeLevel(expr.getSortArgument().getPLevel()));
    builder.setHLevel(writeLevel(expr.getSortArgument().getHLevel()));
    builder.setExpression(expr.getArgument().accept(this, null));
    return ExpressionProtos.Expression.newBuilder().setFieldCall(builder).build();
  }
}
