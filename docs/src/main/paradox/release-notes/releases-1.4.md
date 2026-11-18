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

# Release Notes (1.4.x)

Apache Pekko 1.4.x releases support Java 8 and above.

## 1.4.0

Pekko 1.4.0 has a dependency change and some bug fixes. See the [GitHub Milestone for 1.3.1](https://github.com/apache/pekko/milestone/24?closed=1) and the [GitHub Milestone for 1.4.0](https://github.com/apache/pekko/milestone/25?closed=1) for a fuller list of changes.

### Dependency Changes

* pekko-serialization-jackson: switch to at.yawk.lz4:lz4-java. The org.lz4:lz4-java jar is unmaintained. The forked jar is a drop in replacement but with important bug fixes. Lz4 is only used if you override the default configs for pekko-serialization-jackson. [#2536](https://github.com/apache/pekko/issues/2536)
* Scala 2.13.18

### Bug Fixes

* Handle build issue in OSGi build code ([PR2513](https://github.com/apache/pekko/pull/2513))
