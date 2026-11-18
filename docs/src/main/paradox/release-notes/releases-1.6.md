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

# Release Notes (1.6.x)

Apache Pekko 1.6.x releases support Java 8 and above.

## 1.6.0

Pekko 1.6.0 has some bug fixes. See the [GitHub Milestone for 1.6.0](https://github.com/apache/pekko/milestone/29?closed=1) for a fuller list of changes.

### Bug Fixes

* IllegalStateException in ProducerController upon restart with an empty unconfirmed buffer (Durable Queue) ([#2760](https://github.com/apache/pekko/issues/2760))
* Remove implicit JUnit4 dependency from LogCapturingExtension ([PR2866](https://github.com/apache/pekko/pull/2866))
