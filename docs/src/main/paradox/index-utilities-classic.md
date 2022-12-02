# Classic Utilities

## Dependency

To use Utilities, you must add the following dependency in your project:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=com.typesafe.akka bomArtifact=akka-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="com.typesafe.akka"
  artifact="akka-actor_$scala.binary.version$"
  version=PekkoVersion
  group2="com.typesafe.akka"
  artifact2="akka-testkit_$scala.binary.version$"
  scope2=test
  version2=PekkoVersion
}

@@toc { depth=2 }

@@@ index

* [event-bus](event-bus.md)
* [logging](logging.md)
* [scheduler](scheduler.md)
* [extending-akka](extending-akka.md)

@@@
