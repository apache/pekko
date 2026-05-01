# Release Notes (1.6.x)

Apache Pekko 1.6.x releases support Java 8 and above.

# 1.6.0

Pekko 1.6.0 has some bug fixes. See the [GitHub Milestone for 1.6.0](https://github.com/apache/pekko/milestone/29?closed=1) for a fuller list of changes.

### Bug Fix

* IllegalStateException in ProducerController upon restart with an empty unconfirmed buffer (Durable Queue) ([#2760](https://github.com/apache/pekko/issues/2760))
* Remove implicit JUnit4 dependency from LogCapturingExtension ([PR2866](https://github.com/apache/pekko/pull/2866))
