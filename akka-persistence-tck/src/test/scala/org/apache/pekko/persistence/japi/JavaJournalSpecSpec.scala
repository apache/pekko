/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.japi

import com.typesafe.config.ConfigFactory
import org.scalatest.DoNotDiscover

import org.apache.pekko.persistence.japi.journal.JavaJournalSpec

/* Only checking that compilation works with the constructor here as expected (no other abstract fields leaked) */
@DoNotDiscover
class JavaJournalSpecSpec extends JavaJournalSpec(ConfigFactory.parseString(""))
