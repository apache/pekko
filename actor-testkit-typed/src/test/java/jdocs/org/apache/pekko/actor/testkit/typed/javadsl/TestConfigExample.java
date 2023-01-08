/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.actor.testkit.typed.javadsl;

// #default-application-conf
import com.typesafe.config.ConfigFactory;

// #default-application-conf

public class TestConfigExample {

  void illustrateApplicationConfig() {

    // #default-application-conf
    ConfigFactory.load()
    // #default-application-conf
    ;

    // #parse-string
    ConfigFactory.parseString("pekko.loglevel = DEBUG \n" + "pekko.log-config-on-start = on \n")
    // #parse-string
    ;

    // #fallback-application-conf
    ConfigFactory.parseString("pekko.loglevel = DEBUG \n" + "pekko.log-config-on-start = on \n")
        .withFallback(ConfigFactory.load())
    // #fallback-application-conf
    ;
  }
}
