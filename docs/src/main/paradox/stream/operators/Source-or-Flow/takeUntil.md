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

# takeUntil

Pass elements downstream until the predicate function returns true. The first element for which the predicate returns true is also emitted before the stream completes.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.takeUntil](Source) { scala="#takeUntil(p:Out=&gt;Boolean):FlowOps.this.Repr[Out]" java="#takeUntil(org.apache.pekko.japi.function.Predicate)" }
@apidoc[Flow.takeUntil](Flow) { scala="#takeUntil(p:Out=&gt;Boolean):FlowOps.this.Repr[Out]" java="#takeUntil(org.apache.pekko.japi.function.Predicate)" }

## Description

Pass elements downstream until the predicate function returns true. The first element for which the predicate returns true is also emitted before the stream completes.

## Reactive Streams semantics

@@@div { .callout }

**emits** the predicate is false or the first time the predicate is true

**backpressures** when downstream backpressures

**completes** after predicate returned true or upstream completes

@@@
