/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.typed.extensions

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.Extension
import pekko.actor.typed.ExtensionId
import pekko.actor.typed.scaladsl.Behaviors
import scala.annotation.nowarn
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

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
      akka.actor.typed.extensions = ["org.apache.pekko.akka.extensions.DatabasePool"]
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
