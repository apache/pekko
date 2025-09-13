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

package docs.config

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

//#imports
import org.apache.pekko.actor.typed.ActorSystem
import org.ekrich.config.ConfigFactory
//#imports

class ConfigDocSpec extends AnyWordSpec with Matchers {
  val rootBehavior = Behaviors.empty[String]

  def compileOnlyCustomConfig(): Unit = {
    // #custom-config
    val customConf = ConfigFactory.parseString("""
      pekko.log-config-on-start = on
    """)
    // ConfigFactory.load sandwiches customConfig between default reference
    // config and default overrides, and then resolves it.
    val system = ActorSystem(rootBehavior, "MySystem", ConfigFactory.load(customConf))
    // #custom-config
  }

  def compileOnlyPrintConfig(): Unit = {
    // #dump-config
    val system = ActorSystem(rootBehavior, "MySystem")
    system.logConfiguration()
    // #dump-config
  }

  def compileOnlySeparateApps(): Unit = {
    // #separate-apps
    val config = ConfigFactory.load()
    val app1 = ActorSystem(rootBehavior, "MyApp1", config.getConfig("myapp1").withFallback(config))
    val app2 =
      ActorSystem(rootBehavior, "MyApp2", config.getConfig("myapp2").withOnlyPath("pekko").withFallback(config))
    // #separate-apps
  }

  def moreCustomConfig(): Unit = {
    // #custom-config-2
    // make a Config with just your special setting
    val myConfig = ConfigFactory.parseString("something=somethingElse");
    // load the normal config stack (system props,
    // then application.conf, then reference.conf)
    val regularConfig = ConfigFactory.load();
    // override regular stack with myConfig
    val combined = myConfig.withFallback(regularConfig);
    // put the result in between the overrides
    // (system props) and defaults again
    val complete = ConfigFactory.load(combined);
    // create ActorSystem
    val system = ActorSystem(rootBehavior, "myname", complete);
    // #custom-config-2
  }

  "deployment section" in {
    val conf =
      ConfigFactory.parseString("""
  #//#deployment-section
  pekko.actor.deployment {
  
    # '/user/actorA/actorB' is a remote deployed actor
    /actorA/actorB {
      remote = "pekko://sampleActorSystem@127.0.0.1:7356"
    }
    
    # all direct children of '/user/actorC' have a dedicated dispatcher 
    "/actorC/*" {
      dispatcher = my-dispatcher
    }

    # all descendants of '/user/actorC' (direct children, and their children recursively)
    # have a dedicated dispatcher
    "/actorC/**" {
      dispatcher = my-dispatcher
    }
    
    # '/user/actorD/actorE' has a special priority mailbox
    /actorD/actorE {
      mailbox = prio-mailbox
    }
    
    # '/user/actorF/actorG/actorH' is a random pool
    /actorF/actorG/actorH {
      router = random-pool
      nr-of-instances = 5
    }
  }
  
  my-dispatcher {
    fork-join-executor.parallelism-min = 10
    fork-join-executor.parallelism-max = 10
  }
  prio-mailbox {
    mailbox-type = "a.b.MyPrioMailbox"
  }
  #//#deployment-section
  """)
    val system = ActorSystem(rootBehavior, "MySystem", conf)
    ActorTestKit.shutdown(system)
  }
}
