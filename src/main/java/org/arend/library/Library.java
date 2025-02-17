package org.arend.library;

import org.arend.module.ModulePath;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.term.group.ChildGroup;
import org.arend.typechecking.TypecheckerState;
import org.arend.typechecking.order.Ordering;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Represents a library which can be loaded from some external source such as a file system.
 * The scopes of loaded modules can be obtained from {@link #getModuleScopeProvider}.
 */
public interface Library {
  /**
   * Gets the name of the library.
   *
   * @return the name of this library.
   */
  @Nonnull
  String getName();

  /**
   * Loads the library and its dependencies.
   * This method must register all of the library's dependencies using {@link LibraryManager#registerDependency}
   * Do not invoke this method directly; use {@link LibraryManager#loadLibrary(Library)} instead.
   *
   * @param libraryManager  a library manager containing the information necessary for the loading.
   *
   * @return true if loading succeeded, false otherwise.
   */
  boolean load(LibraryManager libraryManager);

  /**
   * Unloads the library.
   * Do not invoke this method directly; use {@link LibraryManager#unloadLibrary(Library)} instead.
   * A library can fail to unload; for example, prelude can never be unloaded.
   *
   * @return true if the library was successfully unloaded, false otherwise.
   */
  boolean unload();

  /**
   * Resets typechecked definitions from this library.
   */
  void reset();

  /**
   * Checks if the library is loaded.
   *
   * @return true if the library is loaded, false otherwise.
   */
  boolean isLoaded();

  /**
   * Gets the underling typechecker state of this library.
   *
   * @return the typechecker state.
   */
  @Nonnull
  TypecheckerState getTypecheckerState();

  /**
   * Gets the list of loaded modules of this library.
   *
   * @return the list of loaded modules.
   */
  @Nonnull
  Collection<? extends ModulePath> getLoadedModules();

  /**
   * Gets the list of dependencies of this library.
   *
   * @return the list of dependencies.
   */
  @Nonnull
  Collection<? extends LibraryDependency> getDependencies();

  /**
   * Gets the group of a module.
   *
   * @param modulePath  the path to a module.
   *
   * @return the group of a module or null if the module is not found.
   */
  @Nullable
  ChildGroup getModuleGroup(ModulePath modulePath);

  /**
   * Checks if this library contains a specified module.
   *
   * @param modulePath  the path to a module.
   *
   * @return true if the library contains the module, false otherwise.
   */
  boolean containsModule(ModulePath modulePath);

  /**
   * Gets a module scope provider that can be used to get scopes of modules in this library.
   * This method may be invoked only after the library is successfully loaded.
   *
   * @return a scope provider for modules in this library.
   */
  @Nonnull
  ModuleScopeProvider getModuleScopeProvider();

  /**
   * Checks if this library supports typechecking.
   *
   * @return true if this library can be typechecked, false if this library is read-only.
   */
  boolean supportsTypechecking();

  /**
   * Checks if this library needs typechecking.
   * If the library does not support typechecking (that is, {@link #supportsTypechecking} returns false), this method should always return false.
   *
   * @return true if the typechecking is needed, false otherwise.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  boolean needsTypechecking();

  /**
   * Runs an ordering on modules of this library that require typechecking.
   *
   * @param ordering  an ordering.
   *
   * @return true if the ordering was finished, false if it was interrupted.
   */
  boolean orderModules(Ordering ordering);
}
