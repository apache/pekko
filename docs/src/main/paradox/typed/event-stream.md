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

Each actor system has a local event stream for publishing system events and application events to subscribed actors.
Events are delivered to subscribers of their class or a superclass. They are not propagated to other actor systems.
Subscriptions are removed automatically when the subscriber terminates.

The same actor can subscribe to several event types. Sending `EventStream.Unsubscribe` for that actor removes all of its
subscriptions. These rules apply to both the Scala and Java APIs.

Classic logging events use the event stream. Typed actors log through SLF4J, as described in @ref:[Logging](logging.md).

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

A subscription to `DeadLetter` receives ordinary dead letters. Messages marked with
`DeadLetterSuppression` are published as `SuppressedDeadLetter` instead. Subscribe to their common superclass
`AllDeadLetters` to receive both, as well as `Dropped` and `UnhandledMessage`, which also extend it.

It is possible to subscribe to a common superclass:

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
