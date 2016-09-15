package com.jetbrains.jetpad.vclang.naming.namespace;

import com.jetbrains.jetpad.vclang.term.Abstract;

import java.util.*;

import static com.jetbrains.jetpad.vclang.term.Abstract.DefineStatement.StaticMod.STATIC;

public class SimpleDynamicNamespaceProvider implements DynamicNamespaceProvider {
  private final Map<Abstract.ClassDefinition, SimpleNamespace> classCache = new HashMap<>();

  @Override
  public SimpleNamespace forClass(final Abstract.ClassDefinition classDefinition) {
    SimpleNamespace ns = classCache.get(classDefinition);
    if (ns != null) return ns;

    ns = forStatements(classDefinition.getStatements());
    for (final Abstract.SuperClass superClass : classDefinition.getSuperClasses()) {
      Abstract.ClassDefinition superDef = Abstract.getUnderlyingClassDef(superClass.getSuperClass());
      if (superDef == null) continue;

      SimpleNamespace namespace = forClass(superDef);

      Map<String, String> renamings = new HashMap<>();
      for (Abstract.IdPair idPair : superClass.getRenamings()) {
        renamings.put(idPair.getFirstName(), idPair.getSecondName());
      }
      Set<String> hiding = new HashSet<>();
      for (Abstract.Identifier identifier : superClass.getHidings()) {
        hiding.add(identifier.getName());
      }

      for (Map.Entry<String, Abstract.Definition> entry : namespace.getEntrySet()) {
        if (hiding.contains(entry.getKey())) continue;
        String newName = renamings.get(entry.getKey());
        ns.addDefinition(newName != null ? newName : entry.getKey(), entry.getValue());
      }
    }

    classCache.put(classDefinition, ns);
    return ns;
  }

  private static SimpleNamespace forData(Abstract.DataDefinition def) {
    SimpleNamespace ns = new SimpleNamespace();
    for (Abstract.Constructor constructor : def.getConstructors()) {
      ns.addDefinition(constructor);
    }
    return ns;
  }

  private static SimpleNamespace forStatements(Collection<? extends Abstract.Statement> statements) {
    SimpleNamespace ns = new SimpleNamespace();
    for (Abstract.Statement statement : statements) {
      if (!(statement instanceof Abstract.DefineStatement)) continue;
      Abstract.DefineStatement defst = (Abstract.DefineStatement) statement;
      if (defst.getDefinition() instanceof Abstract.ImplementDefinition) continue;  // HACK[impldef]
      if (!STATIC.equals(defst.getStaticMod())) {
        ns.addDefinition(defst.getDefinition());
        if (defst.getDefinition() instanceof Abstract.DataDefinition) {
          ns.addAll(forData((Abstract.DataDefinition) defst.getDefinition()));  // constructors
        }
      }
    }
    return ns;
  }
}
