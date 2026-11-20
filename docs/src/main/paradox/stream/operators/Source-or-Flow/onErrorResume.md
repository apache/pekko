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

# onErrorResume

Allows transforming a failure signal into a stream of elements provided by a factory function.

@ref[Error handling](../index.md#error-handling)

This method is Java API only, use @ref[recoverWith](recoverWith.md) in Scala.

## Signature

@apidoc[Source.onErrorResume](Source) { java="#onErrorResume(org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Source.onErrorResume](Source) { java="#onErrorResume(java.lang.Class,org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Source.onErrorResume](Source) { java="#onErrorResume(org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Flow.onErrorResume](Flow) { java="#onErrorResume(org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Flow.onErrorResume](Flow) { java="#onErrorResume(java.lang.Class,org.apache.pekko.japi.function.Function)" }<br>
@apidoc[Flow.onErrorResume](Flow) { java="#onErrorResume(org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Function)" }


## Description

Transform a failure signal into a stream of elements provided by a factory function.

## Reactive Streams semantics

@@@div { .callout }

**emits** element is available from the upstream or upstream is failed and fallback Source produces an element

**backpressures** downstream backpressures

**completes** upstream completes or upstream failed with exception and fallback Source completes

**Cancels when** downstream cancels
@@@