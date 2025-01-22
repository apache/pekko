# Classic Dispatchers

@@include[includes.md](includes.md) { #actor-api }
For the full documentation of this feature and for new projects see @ref:[Dispatchers](typed/dispatchers.md).

## Dependency

Dispatchers are part of core Pekko, which means that they are part of the pekko-actor dependency:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="pekko-actor_$scala.binary.version$"
  version=PekkoVersion
}

<a id="dispatcher-lookup"></a>
## Looking up a Dispatcher

Dispatchers implement the @scala[@scaladoc[ExecutionContext](scala.concurrent.ExecutionContext)]@java[@javadoc[Executor](java.util.concurrent.Executor)] interface and can thus be used to run @scala[@scaladoc[Future](scala.concurrent.Future)]@java[@javadoc[CompletableFuture](java.util.concurrent.CompletableFuture)] invocations etc.

Scala
:  @@snip [DispatcherDocSpec.scala](/docs/src/test/scala/docs/dispatcher/DispatcherDocSpec.scala) { #lookup }

Java
:  @@snip [DispatcherDocTest.java](/docs/src/test/java/jdocs/dispatcher/DispatcherDocTest.java) { #lookup }

## Setting the dispatcher for an Actor

So in case you want to give your @apidoc[actor.Actor] a different dispatcher than the default, you need to do two things, of which the first
is to configure the dispatcher:

<!--same config text for Scala & Java-->
@@snip [DispatcherDocSpec.scala](/docs/src/test/scala/docs/dispatcher/DispatcherDocSpec.scala) { #my-dispatcher-config }

@@@ note

Note that the `parallelism-max` does not set the upper bound on the total number of threads
allocated by the ForkJoinPool. It is a setting specifically talking about the number of *hot*
threads the pool keep running in order to reduce the latency of handling a new incoming task.
You can read more about parallelism in the JDK's [ForkJoinPool documentation](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html).

When Running on Java 9+, you can use `maximum-pool-size` to set the upper bound on the total number of threads allocated by the ForkJoinPool.

**Experimental**: When Running on Java 21+, you can use `virtualize=on` to enable the virtual threads feature.
When using virtual threads, all virtual threads will use the same `unparker`, so you may want to 
increase the number of `jdk.unparker.maxPoolSize`. 

#### Requirements

1. JDK 21+
2. add options to the JVM:
 - `--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED`
 - `--add-opens=java.base/java.lang=ALL-UNNAMED`
@@@

Another example that uses the "thread-pool-executor":

<!--same config text for Scala & Java-->
@@snip [DispatcherDocSpec.scala](/docs/src/test/scala/docs/dispatcher/DispatcherDocSpec.scala) { #fixed-pool-size-dispatcher-config }

@@@ note

The thread pool executor dispatcher is implemented using a @javadoc[java.util.concurrent.ThreadPoolExecutor](java.util.concurrent.ThreadPoolExecutor).
You can read more about it in the JDK's [ThreadPoolExecutor documentation](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html).

@@@

For more options, see @ref[Dispatchers](typed/dispatchers.md) and the `default-dispatcher` section of the @ref:[configuration](general/configuration.md).

Then you create the actor as usual and define the dispatcher in the deployment configuration.

Scala
:  @@snip [DispatcherDocSpec.scala](/docs/src/test/scala/docs/dispatcher/DispatcherDocSpec.scala) { #defining-dispatcher-in-config }

Java
:  @@snip [DispatcherDocTest.java](/docs/src/test/java/jdocs/dispatcher/DispatcherDocTest.java) { #defining-dispatcher-in-config }

<!--same config text for Scala & Java-->
@@snip [DispatcherDocSpec.scala](/docs/src/test/scala/docs/dispatcher/DispatcherDocSpec.scala) { #dispatcher-deployment-config } 

An alternative to the deployment configuration is to define the dispatcher in code.
If you define the `dispatcher` in the deployment configuration then this value will be used instead
of programmatically provided parameter.

Scala
:  @@snip [DispatcherDocSpec.scala](/docs/src/test/scala/docs/dispatcher/DispatcherDocSpec.scala) { #defining-dispatcher-in-code }

Java
:  @@snip [DispatcherDocTest.java](/docs/src/test/java/jdocs/dispatcher/DispatcherDocTest.java) { #defining-dispatcher-in-code }

@@@ note

The dispatcher you specify in @apidoc[withDispatcher](actor.Props) {scala="#withDispatcher(d:String):org.apache.pekko.actor.Props" java="#withDispatcher(java.lang.String)"} and the `dispatcher` property in the deployment
configuration is in fact a path into your configuration.
So in this example it's a top-level section, but you could for instance put it as a sub-section,
where you'd use periods to denote sub-sections, like this: `"foo.bar.my-dispatcher"`

@@@

