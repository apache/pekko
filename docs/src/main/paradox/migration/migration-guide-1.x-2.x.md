---
project.description: Migrating to Apache Pekko 2.0.
---
# Migration from Apache Pekko 1.x to 2.x

Apache Pekko 2.x is not @ref:[Binary Compatible](../common/binary-compatibility-rules.md) with Apache Pekko 1.x.

The major difference is that some deprecated code has been removed but there are also a small number of
breaking changes where deprecation was not feasible.

It is possible that some simple code compiled with Pekko 1.x libs will work with Pekko 2.x but you should not
rely on this.

Pekko 1.x is still maintained and major bugs will be fixed in it.

## Start by upgrading to latest Pekko 1.x releases

Some of the changes in Pekko 2.x have been backported although these changes are normally only done when they fix
important bugs.

Some additional code has been deprecated in the most recent 1.x releases and the compile warnings will help you with
moving onto better supported APIs.

## Change any code that relies on deprecated APIs in Pekko 1.x

Not every deprecated API has been removed in Pekko 2.x but many have been.

Java API users may find that they have more deprecations to deal with because the Scala API is more stable and there
were a few mistakes in the Java API where Scala classes leaked into some of the Java API methods.

## Additional Breaking Changes in Pekko 2.x
* In the Scala DSL for Flow and Source, the `watchTermination` function call no longer needs an empty param
list before a second param list. Instead of `watchTermination(){ ... }`, you now must use `watchTermination{ ... }`.
([PR2378](https://github.com/apache/pekko/pull/2378))
* In the Java API, `FSMTransitionHandlerBuilder.build` and `FSMTransitionHandlerBuilder.state` now use
`pekko.japi.Pair` instead of `scala.Tuple2`. Java users need to update their code to use `Pair` instead of `Tuple2`.
([PR3378](https://github.com/apache/pekko/pull/3378))
* The Java DSL `SourceWithContext` graph shape now uses `pekko.japi.Pair` instead of `scala.Tuple2`.
Java code that passes a `SourceWithContext` directly to `GraphDSL` must use `Pair`-typed stages.
Code that converts it with `asSource()` already uses `Pair` and requires no changes.
([PR3388](https://github.com/apache/pekko/pull/3388))
* `ReceiveTimeout` changed from a `case object` singleton to a `final case class` that carries the configured
timeout duration. Scala pattern matches must use a type pattern (`case timeout: ReceiveTimeout =>`) instead of a
stable identifier pattern, and the `ReceiveTimeout.getInstance()` method has been removed from the Java API;
Java users should match on `ReceiveTimeout.class` instead.
([PR3399](https://github.com/apache/pekko/pull/3399))
* In pekko-remote Artery comms, PEKK is now the default magic. ([PR3425](https://github.com/apache/pekko/pull/3425))
