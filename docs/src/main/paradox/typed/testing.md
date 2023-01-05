# Testing

You are viewing the documentation for the new actor APIs, to view the Akka Classic documentation, see @ref:[Classic Testing](../testing.md).

## Module info

To use Actor TestKit add the module to your project:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group=org.apache.pekko
  artifact=pekko-actor-testkit-typed_$scala.binary.version$
  version=PekkoVersion
  scope=test
}

@@@div { .group-scala }

We recommend using Akka TestKit with ScalaTest:

@@dependency[sbt,Maven,Gradle] {
  group=org.scalatest
  artifact=scalatest_$scala.binary.version$
  version=$scalatest.version$
  scope=test
}

@@@

@@project-info{ projectId="actor-testkit-typed" }

## Introduction

Testing can either be done asynchronously using a real @apidoc[actor.typed.ActorSystem] or synchronously on the testing thread using the
@apidoc[typed.*.BehaviorTestKit].

For testing logic in a @apidoc[Behavior] in isolation synchronous testing is preferred, but the features that can be
tested are limited. For testing interactions between multiple actors a more realistic asynchronous test is preferred.

Those two testing approaches are described in:

@@toc { depth=2 }

@@@ index

* [Asynchronous testing](testing-async.md)
* [Synchronous behavior testing](testing-sync.md)

@@@

