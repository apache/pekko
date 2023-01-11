---
project.description: An intuitive and safe way to do asynchronous, non-blocking backpressured stream processing.
---
# Streams

## Module info

To use Pekko Streams, add the module to your project:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="pekko-stream_$scala.binary.version$"
  version=PekkoVersion
  group2="org.apache.pekko"
  artifact2="pekko-stream-testkit_$scala.binary.version$"
  version2=PekkoVersion
  scope2=test
}

@@project-info{ projectId="stream" }

@@toc { depth=2 }

@@@ index

* [stream-introduction](stream-introduction.md)
* [stream-quickstart](stream-quickstart.md)
* [../general/stream/stream-design](../general/stream/stream-design.md)
* [stream-flows-and-basics](stream-flows-and-basics.md)
* [stream-graphs](stream-graphs.md)
* [stream-composition](stream-composition.md)
* [stream-rate](stream-rate.md)
* [stream-context](stream-context.md)
* [stream-dynamic](stream-dynamic.md)
* [stream-customize](stream-customize.md)
* [futures-interop](futures-interop.md)
* [actor-interop](actor-interop.md)
* [reactive-streams-interop](reactive-streams-interop.md)
* [stream-error](stream-error.md)
* [stream-io](stream-io.md)
* [stream-refs](stream-refs.md)
* [stream-parallelism](stream-parallelism.md)
* [stream-testkit](stream-testkit.md)
* [stream-substream](stream-substream.md)
* [stream-cookbook](stream-cookbook.md)
* [../general/stream/stream-configuration](../general/stream/stream-configuration.md)
* [operators](operators/index.md)

@@@
