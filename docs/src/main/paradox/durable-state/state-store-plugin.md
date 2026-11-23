<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Building a storage backend for Durable State

Storage backends for state stores are pluggable in the Pekko persistence extension.
This documentation describes how to build a new storage backend for durable state.

Applications can provide their own plugins by implementing a plugin API and activating them by configuration.
Plugin development requires the following imports:

Scala
:  @@snip [PersistenceStatePluginDocSpec.scala](/docs/src/test/scala/docs/persistence/state/PersistenceStatePluginDocSpec.scala) { #plugin-imports }

Java
:  @@snip [MyJavaStateStore.java](/docs/src/main/java/docs/persistence/state/MyJavaStateStore.java) { #plugin-imports }

## State Store plugin API

A durable state store plugin extends `DurableStateUpdateStore`.

`DurableStateUpdateStore` is an interface and the methods to be implemented are:

Scala
:  @@snip [MyStateStore.scala](/docs/src/main/scala/docs/persistence/state/MyStateStore.scala) { #plugin-api }

Java
:  @@snip [MyJavaStateStore.java](/docs/src/main/java/docs/persistence/state/MyJavaStateStore.java) { #state-store-plugin-api }

## State Store provider

A `DurableStateStoreProvider` needs to be implemented to be able to create the plugin itself:

Scala
:  @@snip [MyStateStore.scala](/docs/src/main/scala/docs/persistence/state/MyStateStore.scala) { #plugin-provider }

Java
:  @@snip [MyJavaStateStoreProvider.java](/docs/src/main/java/docs/persistence/state/MyJavaStateStoreProvider.java) { #plugin-provider }

## Configure the State Store

A durable state store plugin can be activated with the following minimal configuration:

Scala
:  @@snip [PersistenceStatePluginDocSpec.scala](/docs/src/test/scala/docs/persistence/state/PersistenceStatePluginDocSpec.scala) { #plugin-config-scala }

Java
:  @@snip [PersistenceStatePluginDocSpec.scala](/docs/src/test/scala/docs/persistence/state/PersistenceStatePluginDocSpec.scala) { #plugin-config-java }
