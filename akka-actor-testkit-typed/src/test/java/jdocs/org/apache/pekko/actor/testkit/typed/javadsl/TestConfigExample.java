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
