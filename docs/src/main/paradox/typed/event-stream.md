# Event Stream

You are viewing the documentation for the new actor APIs, to view the Pekko Classic documentation, see @ref:[Classic Event Stream](../event-bus.md#event-stream).

## Dependency

To use Event Stream, you must have Pekko typed actors dependency in your project.

@@dependency[sbt,Maven,Gradle] {
bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
symbol1=PekkoVersion
value1="$pekko.version$"
group="org.apache.pekko"
artifact="pekko-actor-typed_$scala.binary.version$"
version=PekkoVersion
}

## How to use

The following example demonstrates how a subscription works. Given an actor:

Scala
: @@snip [EventStreamDocSpec.scala](/actor-typed-tests/src/test/scala/org/apache/pekko/actor/typed/eventstream/EventStreamDocSpec.scala) { #listen-to-dead-letters }

Java
: @@snip [EventStreamDocTest.java](/actor-typed-tests/src/test/java/org/apache/pekko/actor/typed/eventstream/EventStreamDocTest.java) { #dead-letter-imports }

@@@ div { .group-java }

The actor definition:

@@snip [EventStreamDocTest.java](/actor-typed-tests/src/test/java/org/apache/pekko/actor/typed/eventstream/EventStreamDocTest.java) { #listen-to-dead-letters }

It can be used as follows:

@@snip [EventStreamDocTest.java](/actor-typed-tests/src/test/java/org/apache/pekko/actor/typed/eventstream/EventStreamDocTest.java) { #subscribe-to-dead-letters }

@@@

It is possible to subscribe to common superclass:

Scala
: @@snip [EventStreamDocSpec.scala](/actor-typed-tests/src/test/scala/org/apache/pekko/actor/typed/eventstream/EventStreamDocSpec.scala) { #listen-to-super-class }

Java
: @@snip [EventStreamSuperClassDocTest.java](/actor-typed-tests/src/test/java/org/apache/pekko/actor/typed/eventstream/EventStreamSuperClassDocTest.java) { #listen-to-super-class-imports }

@@@ div { .group-java }

The actor definition:

@@snip [EventStreamSuperClassDocTest.java](/actor-typed-tests/src/test/java/org/apache/pekko/actor/typed/eventstream/EventStreamSuperClassDocTest.java) { #listen-to-super-class }

It can be used as follows:

@@snip [EventStreamSuperClassDocTest.java](/actor-typed-tests/src/test/java/org/apache/pekko/actor/typed/eventstream/EventStreamSuperClassDocTest.java) { #subscribe-to-super-class }

@@@
