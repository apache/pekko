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

package org.apache.pekko.stream.javadsl;

import java.util.Optional;
import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.function.Function;

public class SourceUnfoldTest {

  public static // #signature
  <S, E> Source<E, NotUsed> unfold(S zero, Function<S, Optional<Pair<S, E>>> f)
        // #signature
      {
    return Source.unfold(zero, f);
  }
}
