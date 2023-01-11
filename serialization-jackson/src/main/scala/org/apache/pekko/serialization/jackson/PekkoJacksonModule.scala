/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization.jackson

/**
 * Complete module with support for all custom serializers.
 */
class PekkoJacksonModule extends JacksonModule with ActorRefModule with AddressModule with FiniteDurationModule {
  override def getModuleName = "PekkoJacksonModule"
}

object PekkoJacksonModule extends PekkoJacksonModule

class PekkoTypedJacksonModule extends JacksonModule with TypedActorRefModule {
  override def getModuleName = "PekkoTypedJacksonModule"
}

object PekkoTypedJacksonModule extends PekkoJacksonModule

class PekkoStreamJacksonModule extends JacksonModule with StreamRefModule {
  override def getModuleName = "PekkoStreamJacksonModule"
}

object PekkoStreamJacksonModule extends PekkoJacksonModule
