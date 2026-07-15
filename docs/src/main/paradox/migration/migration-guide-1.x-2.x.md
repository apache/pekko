---
project.description: Migrating to Apache Pekko 2.0.
---
# Migration from Apache Pekko 1.x to 2.x

Apache Pekko 2.x is not @ref:[Binary Compatible](../common/binary-compatibility-rules.md) with Apache 1.x.

The major differences is that some deprecated code has been removed but there are also a small number of
breaking changes where deprecation was not feasible.

It is possible that some simple code compiled with Pekko 1.x libs will work with Pekko 2.x but you should not
rely on this.

Pekko 1.x is still maintained and major bugs will be fixed in it.

## Start by upgrading to latest Pekko 1.x releases

Some of the changes in Pekko 2.x have been backported although these changes are normally only done when they fix
important bugs.

Some additional code has been deprecated in most recent 1.x releases and the compile warnings will help you with
moving onto better supported APIs.

## Change any code that relies on deprecated APIs in Pekko 1.x

Not every deprecated API has been removed in Pekko 2.x but many have been.

Java API users may find that they have more deprecations to deal with because the Scala API is more stable and there
were a few mistakes in the Java API where Scala classes leaked into some of the Java API methods.

## Additional Breaking Changes in Pekko 2.x

An example is in the Scala DSL for Flow and Source, the `watchTermination` function call no longer needs an empty param
list before a second param list. Instead of `watchTermination(){ ... }`, you now must use `watchTermination{ ... }`.
