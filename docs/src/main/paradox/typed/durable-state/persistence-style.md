# Style Guide 

@@@ div { .group-scala }
## Command handlers in the state

We can take the previous bank account example one step further by handling the commands within the state as well.

Scala
:  @@snip [AccountExampleWithCommandHandlersInDurableState.scala](/cluster-sharding-typed/src/test/scala/docs/org/apache/pekko/cluster/sharding/typed/AccountExampleWithCommandHandlersInDurableState.scala) { #account-entity }

Take note of how the command handler is delegating to `applyCommand` in the `Account` (state), which is implemented
in the concrete `EmptyAccount`, `OpenedAccount`, and `ClosedAccount`.

@@@

## Optional initial state

Sometimes, it's not desirable to use a separate state class for the empty initial state, but rather act as if 
there is no state yet.
@java[You can use `null` as the `emptyState`, but be aware of that the `state` parameter
will be `null` until the first non-null state has been persisted 
It's possible to use `Optional` instead of `null`, but that requires extra boilerplate
to unwrap the `Optional` state parameter. Therefore use of `null` is simpler. The following example
illustrates using `null` as the `emptyState`.]
@scala[`Option[State]` can be used as the state type and `None` as the `emptyState`. Then pattern matching
is used in command handlers at the outer layer before delegating to the state or other methods.]

Scala
:  @@snip [AccountExampleWithOptionDurableState.scala](/cluster-sharding-typed/src/test/scala/docs/org/apache/pekko/cluster/sharding/typed/AccountExampleWithOptionDurableState.scala) { #account-entity }

Java
:  @@snip [AccountExampleWithNullDurableState.java](/cluster-sharding-typed/src/test/java/jdocs/org/apache/pekko/cluster/sharding/typed/AccountExampleWithNullDurableState.java) { #account-entity }
