/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.japi

import org.apache.pekko.persistence.japi.journal.JavaJournalSpec

import org.scalatest.DoNotDiscover

import com.typesafe.config.ConfigFactory

/* Only checking that compilation works with the constructor here as expected (no other abstract fields leaked) */
@DoNotDiscover
class JavaJournalSpecSpec extends JavaJournalSpec(ConfigFactory.parseString(""))
