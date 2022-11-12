/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor;

import org.apache.pekko.japi.Creator;

public class NonStaticCreator implements Creator<UntypedAbstractActor> {
  @Override
  public UntypedAbstractActor create() throws Exception {
    return null;
  }
}
