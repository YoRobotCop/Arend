package org.arend.frontend.library;

import org.arend.error.ErrorReporter;
import org.arend.frontend.source.FileRawSource;
import org.arend.library.LibraryDependency;
import org.arend.library.LibraryHeader;
import org.arend.library.UnmodifiableSourceLibrary;
import org.arend.module.ModulePath;
import org.arend.source.BinarySource;
import org.arend.source.FileBinarySource;
import org.arend.source.GZIPStreamBinarySource;
import org.arend.source.Source;
import org.arend.typechecking.TypecheckerState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class FileSourceLibrary extends UnmodifiableSourceLibrary {
  protected Path mySourceBasePath;
  protected Path myBinaryBasePath;
  protected Set<ModulePath> myModules;
  protected List<LibraryDependency> myDependencies;
  protected boolean myComplete;

  /**
   * Creates a new {@code UnmodifiableFileSourceLibrary}
   *
   * @param name              the name of this library.
   * @param sourceBasePath    a path to the directory with raw source files.
   * @param binaryBasePath    a path to the directory with binary source files.
   * @param modules           the list of modules of this library.
   * @param isComplete        true if {@code modules} contains all modules of this library, false otherwise.
   * @param dependencies      the list of dependencies of this library.
   * @param typecheckerState  a typechecker state in which the result of loading of cached modules will be stored.
   */
  public FileSourceLibrary(String name, Path sourceBasePath, Path binaryBasePath, Set<ModulePath> modules, boolean isComplete, List<LibraryDependency> dependencies, TypecheckerState typecheckerState) {
    super(name, typecheckerState);
    mySourceBasePath = sourceBasePath;
    myBinaryBasePath = binaryBasePath;
    myModules = modules;
    myComplete = isComplete;
    myDependencies = dependencies;
  }

  public Path getSourceBasePath() {
    return mySourceBasePath;
  }

  public Path getBinaryBasePath() {
    return myBinaryBasePath;
  }

  @Nullable
  @Override
  public final Source getRawSource(ModulePath modulePath) {
    return mySourceBasePath == null ? null : new FileRawSource(mySourceBasePath, modulePath);
  }

  @Nullable
  @Override
  public BinarySource getBinarySource(ModulePath modulePath) {
    return myBinaryBasePath == null ? null : new GZIPStreamBinarySource(new FileBinarySource(myBinaryBasePath, modulePath));
  }

  @Nullable
  @Override
  protected LibraryHeader loadHeader(ErrorReporter errorReporter) {
    return new LibraryHeader(myModules, myDependencies);
  }

  @Override
  public boolean supportsTypechecking() {
    return mySourceBasePath != null;
  }

  @Override
  public boolean needsTypechecking() {
    return supportsTypechecking() && super.needsTypechecking();
  }

  @Override
  public boolean supportsPersisting() {
    return myBinaryBasePath != null;
  }

  @Nonnull
  @Override
  public List<? extends LibraryDependency> getDependencies() {
    return myDependencies;
  }

  @Override
  public boolean containsModule(ModulePath modulePath) {
    if (myComplete) {
      return myModules.contains(modulePath);
    }

    Source source = getRawSource(modulePath);
    if (source != null) {
      return source.isAvailable();
    }

    source = getBinarySource(modulePath);
    return source != null && source.isAvailable();
  }
}