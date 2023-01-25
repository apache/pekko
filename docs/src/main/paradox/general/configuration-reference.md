# Default configuration

Each Pekko module has a `reference.conf` file with the default values.

Make your edits/overrides in your `application.conf`. Don't override default values if
you are not sure of the implications.

The purpose of `reference.conf` files is for libraries, like Pekko, to define default values that are used if
an application doesn't define a more specific value. It's also a good place to document the existence and
meaning of the configuration properties. One library must not try to override properties in its own `reference.conf`
for properties originally defined by another library's `reference.conf`, because the effective value would be
nondeterministic when loading the configuration.`

<a id="config-pekko-actor"></a>
### pekko-actor

@@snip [reference.conf](/actor/src/main/resources/reference.conf)

<a id="config-pekko-actor-typed"></a>
### pekko-actor-typed

@@snip [reference.conf](/actor-typed/src/main/resources/reference.conf)

<a id="config-pekko-cluster-typed"></a>
### pekko-cluster-typed

@@snip [reference.conf](/cluster-typed/src/main/resources/reference.conf)

<a id="config-pekko-cluster"></a>
### pekko-cluster

@@snip [reference.conf](/cluster/src/main/resources/reference.conf)

<a id="config-pekko-discovery"></a>
### pekko-discovery

@@snip [reference.conf](/discovery/src/main/resources/reference.conf)

<a id="config-pekko-coordination"></a>
### pekko-coordination

@@snip [reference.conf](/coordination/src/main/resources/reference.conf)

<a id="config-pekko-multi-node-testkit"></a>
### pekko-multi-node-testkit

@@snip [reference.conf](/multi-node-testkit/src/main/resources/reference.conf)

<a id="config-pekko-persistence-typed"></a>
### pekko-persistence-typed

@@snip [reference.conf](/persistence-typed/src/main/resources/reference.conf)

<a id="config-pekko-persistence"></a>
### pekko-persistence

@@snip [reference.conf](/persistence/src/main/resources/reference.conf)

<a id="config-pekko-persistence-query"></a>
### pekko-persistence-query

@@snip [reference.conf](/persistence-query/src/main/resources/reference.conf)

<a id="config-pekko-persistence-testkit"></a>
### pekko-persistence-testkit

@@snip [reference.conf](/persistence-testkit/src/main/resources/reference.conf)

<a id="config-pekko-remote-artery"></a>
### pekko-remote artery

@@snip [reference.conf](/remote/src/main/resources/reference.conf) { #shared #artery type=none }

<a id="config-pekko-remote"></a>
### pekko-remote classic (deprecated)

@@snip [reference.conf](/remote/src/main/resources/reference.conf) { #shared #classic type=none }

<a id="config-pekko-testkit"></a>
### pekko-testkit

@@snip [reference.conf](/testkit/src/main/resources/reference.conf)

<a id="config-cluster-metrics"></a>
### pekko-cluster-metrics

@@snip [reference.conf](/cluster-metrics/src/main/resources/reference.conf)

<a id="config-cluster-tools"></a>
### pekko-cluster-tools

@@snip [reference.conf](/cluster-tools/src/main/resources/reference.conf)

<a id="config-cluster-sharding-typed"></a>
### pekko-cluster-sharding-typed

@@snip [reference.conf](/cluster-sharding-typed/src/main/resources/reference.conf)

<a id="config-cluster-sharding"></a>
### pekko-cluster-sharding

@@snip [reference.conf](/cluster-sharding/src/main/resources/reference.conf)

<a id="config-distributed-data"></a>
### pekko-distributed-data

@@snip [reference.conf](/distributed-data/src/main/resources/reference.conf)

<a id="config-pekko-stream"></a>
### pekko-stream

@@snip [reference.conf](/stream/src/main/resources/reference.conf)

<a id="config-pekko-stream-testkit"></a>
### pekko-stream-testkit

@@snip [reference.conf](/stream-testkit/src/main/resources/reference.conf)

