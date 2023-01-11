# Stash

You are viewing the documentation for the new actor APIs, to view the Pekko Classic documentation, see @ref:[Classic Actors](../actors.md#stash).

## Dependency

To use Pekko Actor Typed, you must add the following dependency in your project:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=akka-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group=org.apache.pekko
  artifact=pekko-actor-typed_$scala.binary.version$
  version=PekkoVersion
}

## Introduction

Stashing enables an actor to temporarily buffer all or some messages that cannot or should not
be handled using the actor's current behavior.

A typical example when this is useful is if the actor has to load some initial state or initialize
some resources before it can accept the first real message. Another example is when the actor
is waiting for something to complete before processing the next message.

Let's illustrate these two with an example. The `DataAccess` actor below is used like a single access point
to a value stored in a database. When it's started it loads current state from the database, and
while waiting for that initial value all incoming messages are stashed.

When a new state is saved in the database it also stashes incoming messages to make the
processing sequential, one after the other without multiple pending writes.

Scala
:  @@snip [StashDocSpec.scala](/actor-typed-tests/src/test/scala/docs/org/apache/pekko/typed/StashDocSpec.scala) { #stashing }

Java
:  @@snip [StashDocTest.java](/actor-typed-tests/src/test/java/jdocs/org/apache/pekko/typed/StashDocSample.java) {
  #import
  #db
  #stashing
}

One important thing to be aware of is that the @apidoc[StashBuffer] is a buffer and stashed messages will be
kept in memory until they are unstashed (or the actor is stopped and garbage collected). It's recommended
to avoid stashing too many messages to avoid too much memory usage and even risking @javadoc[OutOfMemoryError](java.lang.OutOfMemoryError)
if many actors are stashing many messages. Therefore the @apidoc[StashBuffer] is bounded and the `capacity`
of how many messages it can hold must be specified when it's created.

If you try to stash more messages than the `capacity` a @apidoc[StashOverflowException](typed.*.StashOverflowException) will be thrown.
You can use @apidoc[StashBuffer.isFull](StashBuffer) {scala="#isFull:Boolean" java="#isFull()"} before stashing a message to avoid that and take other actions, such as
dropping the message.

When unstashing the buffered messages by calling @apidoc[unstashAll](StashBuffer) {scala="#unstashAll(behavior:org.apache.pekko.actor.typed.Behavior[T]):org.apache.pekko.actor.typed.Behavior[T]" java="#unstashAll(org.apache.pekko.actor.typed.Behavior)"} the messages will be processed sequentially
in the order they were added and all are processed unless an exception is thrown. The actor is unresponsive
to other new messages until @apidoc[unstashAll](StashBuffer) {scala="#unstashAll(behavior:org.apache.pekko.actor.typed.Behavior[T]):org.apache.pekko.actor.typed.Behavior[T]" java="#unstashAll(org.apache.pekko.actor.typed.Behavior)"} is completed. That is another reason for keeping the number of
stashed messages low. Actors that hog the message processing thread for too long can result in starvation
of other actors.

That can be mitigated by using the @apidoc[StashBuffer.unstash](StashBuffer) {scala="#unstash(behavior:org.apache.pekko.actor.typed.Behavior[T],numberOfMessages:Int,wrap:T=%3ET):org.apache.pekko.actor.typed.Behavior[T]" java="#unstash(org.apache.pekko.actor.typed.Behavior,int,java.util.function.Function)"} with `numberOfMessages` parameter and then send a
message to @scala[`context.self`]@java[`context.getSelf`] before continuing unstashing more. That means that other
new messages may arrive in-between and those must be stashed to keep the original order of messages. It
becomes more complicated, so better keep the number of stashed messages low.
