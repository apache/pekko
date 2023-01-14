/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.japi.pf

import FI.{ Apply, Predicate, UnitApply }

private[pf] object CaseStatement {
  def empty[F, T](): PartialFunction[F, T] = PartialFunction.empty
}

private[pf] class CaseStatement[-F, +P, T](predicate: Predicate, apply: Apply[P, T]) extends PartialFunction[F, T] {

  override def isDefinedAt(o: F) = predicate.defined(o)

  override def apply(o: F) = apply.apply(o.asInstanceOf[P])
}

private[pf] class UnitCaseStatement[F, P](predicate: Predicate, apply: UnitApply[P]) extends PartialFunction[F, Unit] {

  override def isDefinedAt(o: F) = predicate.defined(o)

  override def apply(o: F) = apply.apply(o.asInstanceOf[P])
}
