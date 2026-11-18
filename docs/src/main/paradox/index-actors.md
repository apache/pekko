<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Classic Actors

@@include[includes.md](includes.md) { #actor-api }

## Dependency

To use Classic Pekko Actors, you must add the following dependency in your project:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="pekko-actor_$scala.binary.version$"
  version=PekkoVersion
  group2="org.apache.pekko"
  artifact2="pekko-testkit_$scala.binary.version$"
  scope2=test
  version2=PekkoVersion
}

@@toc { depth=2 }

@@@ index

* [actors](actors.md)
* [supervision overview](supervision-classic.md)
* [fault-tolerance](fault-tolerance.md)
* [dispatchers](dispatchers.md)
* [mailboxes](mailboxes.md)
* [routing](routing.md)
* [fsm](fsm.md)
* [persistence](persistence.md)
* [persistence-fsm](persistence-fsm.md)
* [testing](testing.md)

@@@
