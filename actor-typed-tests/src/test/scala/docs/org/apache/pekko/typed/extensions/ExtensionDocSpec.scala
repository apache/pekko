/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.typed.extensions

import com.typesafe.config.ConfigFactory

import scala.annotation.nowarn
import scala.concurrent.Future

import org.apache.pekko
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.{ ActorSystem, Behavior, Extension, ExtensionId }

//#shared-resource
class ExpensiveDatabaseConnection {
  def executeQuery(query: String): Future[Any] = ???
}
//#shared-resource

//#extension-id
object DatabasePool extends ExtensionId[DatabasePool] {
  // will only be called once
  def createExtension(system: ActorSystem[_]): DatabasePool = new DatabasePool(system)

  // Java API
  def get(system: ActorSystem[_]): DatabasePool = apply(system)
}
//#extension-id

@nowarn
//#extension
class DatabasePool(system: ActorSystem[_]) extends Extension {
  // database configuration can be loaded from config
  // from the actor system
  private val _connection = new ExpensiveDatabaseConnection()

  def connection(): ExpensiveDatabaseConnection = _connection
}
//#extension

@nowarn
object ExtensionDocSpec {
  val config = ConfigFactory.parseString("""
      #config      
      pekko.actor.typed.extensions = ["org.apache.pekko.pekko.extensions.DatabasePool"]
      #config
                                         """)

  val initialBehavior: Behavior[Any] = Behaviors.empty[Any]

  // #usage
  Behaviors.setup[Any] { ctx =>
    DatabasePool(ctx.system).connection().executeQuery("insert into...")
    initialBehavior
  }
  // #usage
}
