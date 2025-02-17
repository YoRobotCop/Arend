package org.arend.frontend.parser;

import org.arend.error.GeneralError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.ModuleReferable;

import java.util.function.BiConsumer;

public class ParserError extends GeneralError {
  public final Position position;

  public ParserError(Position position, String message) {
    super(Level.ERROR, message);
    this.position = position;
  }

  @Override
  public Position getCause() {
    return position;
  }

  @Override
  public void forAffectedDefinitions(BiConsumer<GlobalReferable, GeneralError> consumer) {
    consumer.accept(new ModuleReferable(position.module), this);
  }
}
