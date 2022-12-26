# Futures patterns

## Dependency

Akka offers tiny helpers for use with @scala[@scaladoc[Future](scala.concurrent.Future)s]@java[@javadoc[CompletionStage](java.util.concurrent.CompletionStage)]. These are part of Akka's core module:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=akka-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="akka-actor_$scala.binary.version$"
  version=PekkoVersion
}

## After

@scala[`org.apache.pekko.pattern.after`]@java[@javadoc[org.apache.pekko.pattern.Patterns.after](pekko.pattern.Patterns#after)] makes it easy to complete a @scala[@scaladoc[Future](pekko.concurrent.Future)]@java[@javadoc[CompletionStage](java.util.concurrent.CompletionStage)] with a value or exception after a timeout.

Scala
:  @@snip [FutureDocSpec.scala](/docs/src/test/scala/docs/future/FutureDocSpec.scala) { #after }

Java
:   @@snip [FutureDocTest.java](/docs/src/test/java/jdocs/future/FutureDocTest.java) { #imports #after }

## Retry

@scala[`org.apache.pekko.pattern.retry`]@java[@javadoc[org.apache.pekko.pattern.Patterns.retry](pekko.pattern.Patterns#retry)] will retry a @scala[@scaladoc[Future](scala.concurrent.Future)]@java[@javadoc[CompletionStage](java.util.concurrent.CompletionStage)] some number of times with a delay between each attempt.

Scala
:   @@snip [FutureDocSpec.scala](/docs/src/test/scala/docs/future/FutureDocSpec.scala) { #retry }

Java
:   @@snip [FutureDocTest.java](/docs/src/test/java/jdocs/future/FutureDocTest.java) { #imports #retry }
