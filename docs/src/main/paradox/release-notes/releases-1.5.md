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

# Release Notes (1.5.x)

Apache Pekko 1.5.x releases support Java 8 and above.

## 1.5.0

Pekko 1.5.0 has some bug fixes and small enhancements. See the [GitHub Milestone for 1.5.0](https://github.com/apache/pekko/milestone/26?closed=1) for a fuller list of changes.

### Bug Fixes

* RecoverWith operator break of semantics in 1.2.0 release ([#2620](https://github.com/apache/pekko/issues/2620))
* Release lease on shard stop ([PR2740](https://github.com/apache/pekko/pull/2740))
* Source.combine single source with type-transforming fan-in strategies ([PR2746](https://github.com/apache/pekko/pull/2746))

### Additions

* Add Sink.eagerFutureSink to avoid NeverMaterializedException on empty streams ([PR2722](https://github.com/apache/pekko/pull/2722))

### Changes

* Deprecate Source#future in javadsl ([PR2555](https://github.com/apache/pekko/pull/2555))
* Optimize Source#future and Source#futureSource in scaladsl ([PR2583](https://github.com/apache/pekko/pull/2583))
* Use array list for better performance in BroadcastHub ([PR2596](https://github.com/apache/pekko/pull/2596))

### Dependency Changes

* netty 4.2.10.Final
* jackson 2.21.2
* protobuf-java 4.33.5
* config 1.4.6
* lz4-java 1.10.4
