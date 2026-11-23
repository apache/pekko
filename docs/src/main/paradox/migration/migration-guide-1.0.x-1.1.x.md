---
project.description: Migrating to Apache Pekko 1.1.
---
<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Migration from Apache Pekko 1.0.x to 1.1.x

Apache Pekko 1.1.x is binary backwards compatible with 1.0.x with the ordinary exceptions listed in the
@ref:[Binary Compatibility Rules](../common/binary-compatibility-rules.md).

This means that updating an application from Apache Pekko 1.0.x to 1.1.x should be a smooth process, and
that libraries built for Apache Pekko 1.0.x can also be used with Apache Pekko 1.1.x. For example
Apache Pekko HTTP 1.0.0 and Apache Pekko Management 1.0.3 can be used with Apache Pekko 1.1.x dependencies.
You may have to add explicit dependencies to the new Apache Pekko version in your build.

That said, there are some changes to configuration and behavior that should be considered, so
reading this migration guide and testing your application thoroughly is recommended.

## splitWhen/splitAfter behavior change

In Apache Pekko 1.0.x, the `splitWhen`/`splitAfter` stream operators on `Source`/`Flow` had an optional
`SubstreamCancelStrategy` parameter which defaulted to `SubstreamCancelStrategy.drain`. In Apache Pekko
1.1.x, the usage of `SubstreamCancelStrategy` has been deprecated and instead `splitWhen`/`splitAfter`
inherits the already existing `Supervision` strategy `Attribute` to achieve the same effect with the
following translation

* `Supervision.resume` behaves the same way as `SubstreamCancelStrategy.drain`
* `Supervision.stop` behaves the same way as `SubstreamCancelStrategy.propagate`
* `Supervision.restart` doesn't have an equivalent `SubstreamCancelStrategy`. Since it doesn't make semantic
sense for `SubFlow`s it behaves the same way as `Supervision.stop`.

The potential behavior change results from the fact that in Apache Pekko Streams the default `Supervision`
strategy is `Supervision.stop` whereas the default `SubstreamCancelStrategy` for Apache Pekko 1.0.x is
`SubstreamCancelStrategy.drain` (which translates to `Supervision.resume` in Apache Pekko 1.1.x). This means
that when you upgrade from Apache Pekko 1.0.x to 1.1.x its possible that previously if `SubFlow`s
resulted in errors the parent stream would continue to operate whereas in Apache Pekko 1.1.x the stream would cancel.

If you would like to keep the previous Apache Pekko 1.0.x behavior you can just specify the
`Supervision.resumingDecider` attribute on your stream in the standard way. That is, you would change

@@@div { .group-java }
```java
source.splitAfter(somePredicate);
```
@@@

@@@div { .group-scala }
```scala
source.splitAfter(somePredicate)
```
@@@

to

@@@div { .group-java }
```java
source
    .splitAfter(somePredicate)
    .concatSubstreams()
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider));
```
@@@

@@@div { .group-scala }
```scala
source
  .splitAfter(_ == somePredicate)
  .concatSubstreams
  .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
```
@@@

Note that the `.withAttributes` needs to be placed after the substreams are combined with
`.concatSubstreams` method, this is due to how attribute propagation currently behaves
when dealing with `SubFlow`'s.

If you already happen to have already explicitly defined a `SubstreamCancelStrategy` in the
`splitWhen`/`splitAfter` operators then there won't be any behavior change albeit you
will get deprecation warnings on compilation so it's recommended to migrate your code to use
the equivalent `SupervisionStrategy` as described earlier.
