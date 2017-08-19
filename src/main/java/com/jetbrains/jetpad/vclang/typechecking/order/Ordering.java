package com.jetbrains.jetpad.vclang.typechecking.order;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Concrete;
import com.jetbrains.jetpad.vclang.typechecking.Typecheckable;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckableProvider;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckingUnit;
import com.jetbrains.jetpad.vclang.typechecking.typeclass.provider.InstanceProviderSet;

import java.util.*;

public class Ordering<T> {
  private static class DefState {
    int index, lowLink;
    boolean onStack;

    public DefState(int currentIndex) {
      index = currentIndex;
      lowLink = currentIndex;
      onStack = true;
    }
  }

  private int myIndex = 0;
  private final Stack<TypecheckingUnit<T>> myStack = new Stack<>();
  private final Map<Typecheckable<T>, DefState> myVertices = new HashMap<>();
  private final InstanceProviderSet myInstanceProviderSet;
  private final TypecheckableProvider<T> myTypecheckableProvider;
  private final DependencyListener<T> myListener;
  private final boolean myRefToHeaders;

  public Ordering(InstanceProviderSet instanceProviderSet, TypecheckableProvider<T> typecheckableProvider, DependencyListener<T> listener, boolean refToHeaders) {
    myInstanceProviderSet = instanceProviderSet;
    myTypecheckableProvider = typecheckableProvider;
    myListener = listener;
    myRefToHeaders = refToHeaders;
  }

  private Concrete.ClassDefinition<T> getEnclosingClass(Concrete.Definition<T> definition) {
    Concrete.Definition<T> parent = definition.getParentDefinition();
    if (parent == null) {
      return null;
    }
    if (parent instanceof Concrete.ClassDefinition && !definition.isStatic()) {
      return (Concrete.ClassDefinition<T>) parent;
    }
    return getEnclosingClass(parent);
  }

  public void doOrder(Concrete.Definition<T> definition) {
    if (definition instanceof Abstract.ClassView || definition instanceof Abstract.ClassViewField) {
      return;
    }
    if (!myListener.needsOrdering(definition)) {
      myListener.alreadyTypechecked(definition);
      return;
    }

    Typecheckable<T> typecheckable = new Typecheckable<>(definition, myRefToHeaders);
    if (!myVertices.containsKey(typecheckable)) {
      doOrderRecursively(typecheckable);
    }
  }

  private enum OrderResult { REPORTED, NOT_REPORTED, RECURSION_ERROR }

  private OrderResult updateState(DefState currentState, Typecheckable<T> dependency) {
    if (!myListener.needsOrdering(dependency.getDefinition())) {
      return OrderResult.REPORTED;
    }

    OrderResult ok = OrderResult.REPORTED;
    DefState state = myVertices.get(dependency);
    if (state == null) {
      ok = doOrderRecursively(dependency);
      currentState.lowLink = Math.min(currentState.lowLink, myVertices.get(dependency).lowLink);
    } else if (state.onStack) {
      currentState.lowLink = Math.min(currentState.lowLink, state.index);
    }
    return ok;
  }

  private OrderResult doOrderRecursively(Typecheckable<T> typecheckable) {
    Concrete.Definition<T> definition = typecheckable.getDefinition();
    Concrete.ClassDefinition<T> enclosingClass = getEnclosingClass(definition);
    TypecheckingUnit<T> unit = new TypecheckingUnit<>(typecheckable, enclosingClass);
    DefState currentState = new DefState(myIndex);
    myVertices.put(typecheckable, currentState);
    myIndex++;
    myStack.push(unit);

    Typecheckable<T> header = null;
    if (!typecheckable.isHeader() && Typecheckable.hasHeader(definition)) {
      header = new Typecheckable<>(definition, true);
      OrderResult result = updateState(currentState, header);

      if (result == OrderResult.RECURSION_ERROR) {
        myStack.pop();
        currentState.onStack = false;
        myListener.unitFound(unit, DependencyListener.Recursion.IN_HEADER);
        return OrderResult.REPORTED;
      }

      if (result == OrderResult.REPORTED) {
        header = null;
      }
    }

    Set<Abstract.GlobalReferableSourceNode> dependencies = new LinkedHashSet<>();
    if (enclosingClass != null) {
      dependencies.add(enclosingClass);
    }

    DependencyListener.Recursion recursion = DependencyListener.Recursion.NO;
    definition.accept(new DefinitionGetDepsVisitor(myInstanceProviderSet.getInstanceProvider(definition), myTypecheckableProvider, dependencies), typecheckable.isHeader());
    if (typecheckable.isHeader() && dependencies.contains(definition)) {
      myStack.pop();
      currentState.onStack = false;
      return OrderResult.RECURSION_ERROR;
    }

    for (Abstract.GlobalReferableSourceNode referable : dependencies) {
      Concrete.Definition<T> dependency = myTypecheckableProvider.forReferable(referable);
      if (dependency instanceof Concrete.ClassField) {
        dependency = ((Concrete.ClassField<T>) dependency).getParentDefinition();
      } else if (dependency instanceof Concrete.Constructor) {
        dependency = ((Concrete.Constructor<T>) dependency).getDataType();
      }

      if (dependency.equals(definition)) {
        if (!(referable instanceof Abstract.ClassField)) {
          recursion = DependencyListener.Recursion.IN_BODY;
        }
      } else {
        myListener.dependsOn(typecheckable, dependency);
        updateState(currentState, new Typecheckable<>(dependency, myRefToHeaders));
      }
    }

    SCC<T> scc = null;
    if (currentState.lowLink == currentState.index) {
      List<TypecheckingUnit<T>> units = new ArrayList<>();
      do {
        unit = myStack.pop();
        myVertices.get(unit.getTypecheckable()).onStack = false;
        units.add(unit);
      } while (!unit.getTypecheckable().equals(typecheckable));
      Collections.reverse(units);
      scc = new SCC<>(units);

      if (myRefToHeaders) {
        myListener.sccFound(scc);
        return OrderResult.REPORTED;
      }

      if (typecheckable.isHeader() && units.size() == 1) {
        return OrderResult.NOT_REPORTED;
      }

      if (units.size() == 1) {
        myListener.unitFound(unit, recursion);
        return OrderResult.REPORTED;
      }
    }

    if (header != null) {
      myListener.sccFound(new SCC<>(Collections.singletonList(new TypecheckingUnit<>(header, enclosingClass))));
    }
    if (scc != null) {
      myListener.sccFound(scc);
    }

    return OrderResult.REPORTED;
  }
}
