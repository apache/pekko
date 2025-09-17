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

package docs.extension

//#imports
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.{
  ActorSystem,
  ClassicActorSystemProvider,
  ExtendedActorSystem,
  Extension,
  ExtensionId,
  ExtensionIdProvider
}

//#imports

import pekko.actor.Actor
import pekko.testkit.PekkoSpec

//#extension
class SettingsImpl(config: Config) extends Extension {
  val DbUri: String = config.getString("myapp.db.uri")
  val CircuitBreakerTimeout: Duration =
    Duration(config.getDuration("myapp.circuit-breaker.timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
}
//#extension

//#extensionid
object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {

  override def lookup = Settings

  override def createExtension(system: ExtendedActorSystem) =
    new SettingsImpl(system.settings.config)

  /**
   * Java API: retrieve the Settings extension for the given system.
   */
  override def get(system: ActorSystem): SettingsImpl = super.get(system)
  override def get(system: ClassicActorSystemProvider): SettingsImpl = super.get(system)
}
//#extensionid

object SettingsExtensionDocSpec {

  val config = """
    //#config
    myapp {
      db {
        uri = "mongodb://example1.com:27017,example2.com:27017"
      }
      circuit-breaker {
        timeout = 30 seconds
      }
    }
    //#config
    """

  // #extension-usage-actor

  class MyActor extends Actor {
    val settings = Settings(context.system)
    val connection = connect(settings.DbUri, settings.CircuitBreakerTimeout)

    // #extension-usage-actor
    def receive = {
      case someMessage =>
    }

    def connect(dbUri: String, circuitBreakerTimeout: Duration) = {
      "dummy"
    }
  }

}

class SettingsExtensionDocSpec extends PekkoSpec(SettingsExtensionDocSpec.config) {

  "demonstrate how to create application specific settings extension in Scala" in {
    // #extension-usage
    val dbUri = Settings(system).DbUri
    val circuitBreakerTimeout = Settings(system).CircuitBreakerTimeout
    // #extension-usage
  }

}
