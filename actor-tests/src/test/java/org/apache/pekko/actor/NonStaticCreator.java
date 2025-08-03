/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor;

import org.apache.pekko.japi.function.Creator;

public class NonStaticCreator implements Creator<UntypedAbstractActor> {
  @Override
  public UntypedAbstractActor create() throws Exception {
    return null;
  }
}
