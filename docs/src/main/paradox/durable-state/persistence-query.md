---
project.description: Query side to Apache Pekko Persistence allowing for building CQRS applications using durable state.
---
# Persistence Query

## Dependency

To use Apache Pekko Persistence Query, you must add the following dependency in your project:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group=org.apache.pekko
  artifact=pekko-persistence-query_$scala.binary.version$
  version=PekkoVersion
}

This will also add dependency on the @ref[Pekko Persistence](../persistence.md) module.

## Introduction

Pekko persistence query provides a query interface to @ref:[Durable State Behaviors](../typed/durable-state/persistence.md).
These queries are based on asynchronous streams. These streams are similar to the ones offered in the @ref:[Event Sourcing](../persistence-query.md)
based implementation. Various state store plugins can implement these interfaces to expose their query capabilities.

One of the rationales behind having a separate query module for Pekko Persistence is for implementing the so-called 
query side or read side in the popular CQRS architecture pattern - in which the writing side of the 
application implemented using Pekko persistence, is completely separated from the query side.

## Using query with Pekko Projections

Pekko Persistence and Pekko Projections together can be used to develop a CQRS application. In the application the 
durable state is stored in a database and fetched as an asynchronous stream to the user. Currently queries on 
durable state, provided by the `DurableStateStoreQuery` interface, are used to implement tag based searches in 
Pekko Projections. 

Changes to durable state can be queried in two ways, depending on which queries the state store plugin implements:

* by _tag_, with `changes` and `currentChanges` of @apidoc[pekko.persistence.query.*.DurableStateStoreQuery].
  If you have not tagged your objects, this query cannot be used.
* by _slices_, with `changesBySlices` and `currentChangesBySlices` of @apidoc[pekko.persistence.query.typed.*.DurableStateStoreBySliceQuery]
  for a given entity type and slice range. A slice is deterministically defined based on the persistence id, and the
  purpose is to evenly distribute all persistence ids over the slices. `sliceForPersistenceId` returns the slice of a
  persistence id and `sliceRanges` splits all slices into a given number of ranges, for example to run one query per range.
  This is similar to @ref:[EventsBySlice](../persistence-query.md#eventsbyslice-and-currenteventsbyslice) for event
  sourced entities. This API may change.

Refer to the documentation of the state store plugin you are using for which of these queries it supports.

The example below shows how to get the  `DurableStateStoreQuery` from the `DurableStateStoreRegistry` extension.

Scala
:  @@snip [DurableStateStoreQueryUsageCompileOnlySpec.scala](/cluster-sharding-typed/src/test/scala/docs/org/apache/pekko/cluster/sharding/typed/DurableStateStoreQueryUsageCompileOnlySpec.scala) { #get-durable-state-store-query-example }

Java
:  @@snip [DurableStateStoreQueryUsageCompileOnlyTest.java](/cluster-sharding-typed/src/test/java/jdocs/org/apache/pekko/cluster/sharding/typed/DurableStateStoreQueryUsageCompileOnlyTest.java) { #get-durable-state-store-query-example } 

The @apidoc[DurableStateChange] elements can be `UpdatedDurableState` or `DeletedDurableState`.
