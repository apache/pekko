# Scala 3 support

Apache Pekko has experimental support for Scala 3.

## Using 2.13 artifacts in Scala 3

You can use [CrossVersion.for3Use2_13](https://scala-lang.org/blog/2021/04/08/scala-3-in-sbt.html#using-scala-213-libraries-in-scala-3)
to use the regular 2.13 Apache Pekko artifacts in a Scala 3 project. This has been
shown to be successful for Streams, HTTP and gRPC-heavy applications.

## Scala 3 artifacts

Experimental Scala 3 artifacts are published.

[Development snapshots](https://nightlies.apache.org/pekko/snapshots/org/apache/pekko/pekko-actor_3/) can be found in the snapshots repository.

We encourage you to try out these artifacts and [report any findings](https://github.com/apache/incubator-pekko/issues?q=is%3Aopen+is%3Aissue+label%3At%3Ascala-3).

We do not promise @ref:[binary compatibility](../common/binary-compatibility-rules.md) for these artifacts yet.
