# Release Notes (1.4.x)

Apache Pekko 1.4.x releases support Java 8 and above.

# 1.4.0

Pekko 1.4.0 has a dependency change and some bug fixes. See the [GitHub Milestone for 1.3.1](https://github.com/apache/pekko/milestone/24?closed=1) and the [GitHub Milestone for 1.4.0](https://github.com/apache/pekko/milestone/25?closed=1) for a fuller list of changes.

### Dependency Changes

* Switch to at.yawk.lz4:lz4-java. The org.lz4:lz4-java jar is unmaintained. The forked jar is a drop in replacement but with important bug fixes [#2536](https://github.com/apache/pekko/issues/2536)
* Scala 2.13.18

### Bug Fixes

* Handle build issue in OSGi build code ([PR2513](https://github.com/apache/pekko/pull/2513))
