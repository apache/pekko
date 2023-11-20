# Scala 3 support

Pekko has support for Scala 3. Now that the Scala 3.3 LTS release is out, we can be pretty confident that it is unlikely that we will need to make binary incompatible changes due to Scala 3 changes. See the @ref:[binary compatibility documentation](../common/binary-compatibility-rules.md).

## Using 2.13 artifacts in Scala 3

This should not be necessary but you can use [CrossVersion.for3Use2_13](https://scala-lang.org/blog/2021/04/08/scala-3-in-sbt.html#using-scala-213-libraries-in-scala-3)
to use the regular 2.13 Pekko artifacts in a Scala 3 project. This has been
shown to be successful for Streams, HTTP and gRPC-heavy applications.
