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

# doOnCancel

Run the given function when the downstream cancels.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.doOnCancel](Source) { scala="#doOnCancel(f:(Throwable,Boolean)=&gt;Unit):FlowOps.this.Repr[Out]" java="#doOnCancel(org.apache.pekko.japi.function.Procedure2)" }
@apidoc[Flow.doOnCancel](Flow) { scala="#doOnCancel(f:(Throwable,Boolean)=&gt;Unit):FlowOps.this.Repr[Out]" java="#doOnCancel(org.apache.pekko.japi.function.Procedure2)" }

## Description

Run the given function when the downstream cancels.

## Reactive Streams semantics

@@@div { .callout }

**emits** when upstream emits an element

**backpressures** when downstream backpressures

**completes** when upstream completes

**cancels** when downstream cancels

@@@
