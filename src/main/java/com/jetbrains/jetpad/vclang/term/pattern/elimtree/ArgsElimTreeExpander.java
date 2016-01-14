package com.jetbrains.jetpad.vclang.term.pattern.elimtree;

import com.jetbrains.jetpad.vclang.term.context.binding.Binding;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.param.Utils;
import com.jetbrains.jetpad.vclang.term.pattern.Pattern;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.ElimTreeExpander.Branch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.jetpad.vclang.term.pattern.Utils.expandPatternSubstitute;
import static com.jetbrains.jetpad.vclang.term.pattern.elimtree.ElimTreeExpander.recalcIndices;

public class ArgsElimTreeExpander {
  public static class ArgsBranch {
    public final LeafElimTreeNode leaf;
    public final List<Expression> expressions;
    public final List<Binding> context;
    public final List<Integer> indicies;

    private ArgsBranch(LeafElimTreeNode leaf, List<Expression> expressions, List<Binding> context, List<Integer> indicies) {
      this.leaf = leaf;
      this.expressions = expressions;
      this.context = context;
      this.indicies = indicies;
    }
  }

  public static class ArgsExpansionResult {
    public final ElimTreeNode tree;
    public final List<ArgsBranch> branches;

    private ArgsExpansionResult(ElimTreeNode tree, List<ArgsBranch> branches) {
      this.tree = tree;
      this.branches = branches;
    }
  }

  private final List<Binding> myLocalContext;
  private final int myOldContextSize;

  private List<List<Pattern>> myNestedPatterns;
  private int myIndex;

  private final List<Branch> currentBranches = new ArrayList<>();

  private List<ArgsBranch> resultBranches;

  ArgsElimTreeExpander(List<Binding> localContext) {
    this.myLocalContext = localContext;
    this.myOldContextSize = localContext.size();
  }

  public static ArgsExpansionResult expandElimTree(List<Binding> context, List<List<Pattern>> patterns) {
    if (patterns.isEmpty())
      return new ArgsExpansionResult(EmptyElimTreeNode.getInstance(), Collections.<ArgsBranch>emptyList());

    List<Expression> types = new ArrayList<>(patterns.get(0).size());
    for (int i = patterns.get(0).size() - 1; i >= 0; i--) {
      types.add(context.get(context.size() - 1 - i).getType());
    }

    List<Binding> tail = new ArrayList<>(context.subList(context.size() - types.size(), context.size()));
    context.subList(context.size() - types.size(), context.size()).clear();
    ArgsElimTreeExpander.ArgsExpansionResult treeExpansionResult = new ArgsElimTreeExpander(context).expandElimTree(0, types, patterns);
    context.addAll(tail);

    return treeExpansionResult;
  }

  ArgsExpansionResult expandElimTree(int index, List<Expression> types, List<List<Pattern>> patterns) {
    if (patterns.isEmpty())
      return new ArgsExpansionResult(EmptyElimTreeNode.getInstance(), Collections.<ArgsBranch>emptyList());
    myIndex = index;
    myNestedPatterns = patterns;
    List<Integer> valid = new ArrayList<>();
    for (int i = 0; i < patterns.size(); i++)
      valid.add(i);
    resultBranches = new ArrayList<>();

    return new ArgsExpansionResult(expandElimTreeRecurse(valid, types), resultBranches);
  }

  private ElimTreeNode expandElimTreeRecurse(List<Integer> valid, List<Expression> types) {
    if (types.isEmpty()) {
      List<Expression> expressions = new ArrayList<>(currentBranches.size());
      for (int lift = 0, i = currentBranches.size() - 1; i >= 0; lift += currentBranches.get(i).context.size(), --i) {
        expressions.add(currentBranches.get(i).expression.liftIndex(0, lift));
      }
      Collections.reverse(expressions);

      LeafElimTreeNode leaf = new LeafElimTreeNode();
      resultBranches.add(new ArgsBranch(leaf, expressions, new ArrayList<>(myLocalContext.subList(myOldContextSize, myLocalContext.size())), valid));
      return leaf;
    }

    List<Pattern> patterns = new ArrayList<>();
    for (int i : valid)
      patterns.add(myNestedPatterns.get(i).get(myNestedPatterns.get(i).size() - types.size()));
    ElimTreeExpander.ExpansionResult nestedResult = new ElimTreeExpander(myLocalContext).expandElimTree(myIndex + types.size() - 1, patterns, types.get(0));
    ElimTreeNode resultTree = nestedResult.result;

    for (Branch branch : nestedResult.branches) {
      try (Utils.MultiContextSaver ignore = new Utils.MultiContextSaver(myLocalContext, currentBranches)) {
        myLocalContext.addAll(branch.context);
        currentBranches.add(branch);
        resultTree = branch.leaf.replaceWith(resultTree, expandElimTreeRecurse(recalcIndices(valid, branch.indices), substituteInTypes(types, branch)));
      }
    }

    return resultTree;
  }

  private List<Expression> substituteInTypes(List<Expression> types, Branch branch) {
    List<Expression> newTypes = new ArrayList<>();
    for (int i = 1; i < types.size(); i++)
      newTypes.add(expandPatternSubstitute(branch.context.size(), i - 1, branch.expression, types.get(i)));
    return newTypes;
  }
}
