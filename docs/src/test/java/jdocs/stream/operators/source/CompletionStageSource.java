/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.source;

// #sourceCompletionStageSource
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import java.util.concurrent.CompletionStage;

public class CompletionStageSource {

  public static void sourceCompletionStageSource() {
    UserRepository userRepository = null; // an abstraction over the remote service
    Source<User, CompletionStage<NotUsed>> userCompletionStageSource =
        Source.completionStageSource(userRepository.loadUsers());
    // ...
  }

  interface UserRepository {
    CompletionStage<Source<User, NotUsed>> loadUsers();
  }

  static class User {}
}
// #sourceCompletionStageSource
