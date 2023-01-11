# Classic Utilities

## Dependency

To use Utilities, you must add the following dependency in your project:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="akka-actor_$scala.binary.version$"
  version=PekkoVersion
  group2="org.apache.pekko"
  artifact2="akka-testkit_$scala.binary.version$"
  scope2=test
  version2=PekkoVersion
}

@@toc { depth=2 }

@@@ index

* [event-bus](event-bus.md)
* [logging](logging.md)
* [scheduler](scheduler.md)
* [extending-pekko](extending-pekko.md)

@@@
