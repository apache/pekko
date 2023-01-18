# Scala 3 support

Pekko has experimental support for Scala 3.

## Using 2.13 artifacts in Scala 3

You can use [CrossVersion.for3Use2_13](https://scala-lang.org/blog/2021/04/08/scala-3-in-sbt.html#using-scala-213-libraries-in-scala-3)
to use the regular 2.13 Pekko artifacts in a Scala 3 project. This has been
shown to be successful for Streams, HTTP and gRPC-heavy applications.

## Scala 3 artifacts

We are publishing experimental Scala 3 artifacts that can be used 'directly' (without `CrossVersion`) with Scala 3.

We do not promise @ref:[binary compatibility](../common/binary-compatibility-rules.md) for these artifacts yet.
