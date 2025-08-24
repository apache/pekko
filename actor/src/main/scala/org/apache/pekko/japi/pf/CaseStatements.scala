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

package org.apache.pekko.japi.pf

import org.apache.pekko
import pekko.japi.function.{ Function, Predicate, Procedure }

private[pf] object CaseStatement {
  def empty[F, T](): PartialFunction[F, T] = PartialFunction.empty
}

private[pf] class CaseStatement[-F, +P, T](predicate: Predicate[F], apply: Function[P, T])
    extends PartialFunction[F, T] {

  override def isDefinedAt(o: F): Boolean = predicate.test(o)

  override def apply(o: F): T = apply.apply(o.asInstanceOf[P])
}

private[pf] class UnitCaseStatement[F, P](predicate: Predicate[F], apply: Procedure[P])
    extends PartialFunction[F, Unit] {

  override def isDefinedAt(o: F): Boolean = predicate.test(o)

  override def apply(o: F): Unit = apply.apply(o.asInstanceOf[P])
}
