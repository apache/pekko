# Frequently Asked Questions

## Pekko Project

### Where does the name Pekko come from?

The former name of this project, Akka, is a goddess in the Sámi (the native Swedish population)
mythology. She is the goddess that stands for all the beauty and good in the world. Pekko builds on this
foundation and is the Finnish god of farming & protector of the crops.

## Resources with Explicit Lifecycle

Actors, ActorSystems, Materializers (for streams), all these types of objects bind
resources that must be released explicitly. The reason is that Actors are meant to have
a life of their own, existing independently of whether messages are currently en route
to them. Therefore you should always make sure that for every creation of such an object
you have a matching `stop`, `terminate`, or `shutdown` call implemented.

In particular you typically want to bind such values to immutable references, i.e.
`final ActorSystem system` in Java or `val system: ActorSystem` in Scala.

### JVM application or Scala REPL “hanging”

Due to an ActorSystem’s explicit lifecycle the JVM will not exit until it is stopped.
Therefore it is necessary to shutdown all ActorSystems within a running application or
Scala REPL session in order to allow these processes to terminate.

Shutting down an ActorSystem will properly terminate all Actors and Materializers
that were created within it.

## Actors

### Why OutOfMemoryError?

It can be many reasons for OutOfMemoryError. For example, in a pure push based system with
message consumers that are potentially slower than corresponding message producers you must
add some kind of message flow control. Otherwise messages will be queued in the consumers'
mailboxes and thereby filling up the heap memory.

## Cluster

### How reliable is the message delivery?

The general rule is **at-most-once delivery**, i.e. no guaranteed delivery.
Stronger reliability can be built on top, and Pekko provides tools to do so.

Read more in @ref:[Message Delivery Reliability](../general/message-delivery-reliability.md).

## Debugging

### How do I turn on debug logging?

To turn on debug logging in your actor system add the following to your configuration:

```
pekko.loglevel = DEBUG
```

Read more about it in the docs for @ref:[Logging](../typed/logging.md).