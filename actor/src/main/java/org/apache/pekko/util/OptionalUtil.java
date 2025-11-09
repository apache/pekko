/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.apache.pekko.annotation.InternalStableApi;
import scala.None$;
import scala.jdk.javaapi.OptionConverters;

/** INTERNAL API */
@InternalStableApi
public final class OptionalUtil {
  private static final scala.Option<?> noneValue = None$.MODULE$;

  public static <T> scala.Option<T> scalaNone() {
    return (scala.Option<T>) noneValue;
  }

  @SuppressWarnings("unchecked") // no support for covariance of option in Java
  // needed to provide covariant conversions that the Java interfaces don't provide automatically.
  // The alternative would be having to cast around everywhere instead of doing it here in a central
  // place.
  public static <U, T extends U> Optional<U> convertOption(scala.Option<T> o) {
    return (Optional<U>) (Object) OptionConverters.toJava(o);
  }

  @SuppressWarnings("unchecked") // contains an upcast
  public static <T, U extends T> scala.Option<U> convertOptionalToScala(Optional<T> o) {
    return OptionConverters.toScala((Optional<U>) o);
  }

  // This is needed to be used in Java source code that calls Scala code which expects scala.Long
  // since an implicit cast from java.lang.Long to scala.Long is not available in Java source
  public static scala.Option<Object> convertOptionalToScala(OptionalLong o) {
    if (o.isPresent()) {
      return new scala.Some(o.getAsLong());
    } else {
      return scala.Option.empty();
    }
  }

  // This is needed to be used in Java source code that calls Scala code which expects scala.Int
  // since an implicit cast from java.lang.Int to scala.Int is not available in Java source
  public static scala.Option<Object> convertOptionalToScala(OptionalInt o) {
    if (o.isPresent()) {
      return new scala.Some(o.getAsInt());
    } else {
      return scala.Option.empty();
    }
  }
}
