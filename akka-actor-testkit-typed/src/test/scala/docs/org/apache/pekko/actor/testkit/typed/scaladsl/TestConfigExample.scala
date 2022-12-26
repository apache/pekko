/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.actor.testkit.typed.scaladsl

object TestConfigExample {

  def illustrateApplicationConfig(): Unit = {

    // #default-application-conf
    import com.typesafe.config.ConfigFactory

    ConfigFactory.load()
    // #default-application-conf

    // #parse-string
    ConfigFactory.parseString("""
      pekko.loglevel = DEBUG
      pekko.log-config-on-start = on
      """)
    // #parse-string

    // #fallback-application-conf
    ConfigFactory.parseString("""
      pekko.loglevel = DEBUG
      pekko.log-config-on-start = on
      """).withFallback(ConfigFactory.load())
    // #fallback-application-conf
  }
}
