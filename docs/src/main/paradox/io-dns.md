# DNS Extension

@@@ warning

`async-dns` does not support:

* Local hosts file e.g. `/etc/hosts` on Unix systems
* The [nsswitch.conf](https://linux.die.net/man/5/nsswitch.conf) file (no plan to support)

Additionally, while search domains are supported through configuration, detection of the system configured
search domains is only supported on systems that provide this 
configuration through a `/etc/resolv.conf` file, i.e. it isn't supported on Windows or OSX, and none of the 
environment variables that are usually supported on most \*nix OSes are supported.

@@@

@@@ note

The `async-dns` API is marked as `ApiMayChange` as more information is expected to be added to the protocol.

@@@

@@@ warning

The ability to plugin in a custom DNS implementation is expected to be removed in future versions of Pekko.
Users should pick one of the built in extensions.

@@@

Pekko DNS is a pluggable way to interact with DNS. Implementations much implement `org.apache.pekko.io.DnsProvider` and provide a configuration
block that specifies the implementation via `provider-object`.

@@@ note { title="DNS via Pekko Discovery" }

@ref[Pekko Discovery](discovery/index.md) can be backed by the Pekko DNS implementation and provides a more general API for service lookups which is not limited to domain name lookup.

@@@

To select which `DnsProvider` to use set `pekko.io.dns.resolver` to the location of the configuration.

There are currently two implementations:

* `inet-address` - Based on the JDK's `InetAddress`. Using this will be subject to both the JVM's DNS cache and its built in one.
* `async-dns` - A native implemention of the DNS protocol that does not use any JDK classes or caches.

`inet-address` is the default implementation as it pre-dates `async-dns`, `async-dns` will likely become the default in the next major release.

DNS lookups can be done via the `DNS` extension:

Scala
:  @@snip [DnsCompileOnlyDocSpec.scala](/docs/src/test/scala/docs/actor/io/dns/DnsCompileOnlyDocSpec.scala) { #resolve }

Java
:  @@snip [DnsCompileOnlyDocTest.java](/docs/src/test/java/jdocs/actor/io/dns/DnsCompileOnlyDocTest.java) { #resolve }

Alternatively the `IO(Dns)` actor can be interacted with directly. `async-dns` protocol uses `DnsProtocol.Resolve` and `DnsProtocol.Resolved`. 

Async-DNS API:

Scala
:  @@snip [IODocSpec.scala](/docs/src/test/scala/docs/actor/io/dns/DnsCompileOnlyDocSpec.scala) { #actor-api-async }

Java
:  @@snip [DnsCompileOnlyDocTest.java](/docs/src/test/java/jdocs/actor/io/dns/DnsCompileOnlyDocTest.java) { #actor-api-async }

The Async DNS provider has the following advantages:

* No JVM DNS caching. It is expected that future versions will expose more caching related information.
* No blocking. `InetAddress` resolving is a blocking operation.
* Exposes `SRV`, `A` and `AAAA` records.


## SRV Records

To get DNS SRV records `pekko.io.dns.resolver` must be set to `async-dns` and `DnsProtocol.Resolve`'s requestType
must be set to `DnsProtocol.Srv` 

Scala
:  @@snip [IODocSpec.scala](/docs/src/test/scala/docs/actor/io/dns/DnsCompileOnlyDocSpec.scala) { #srv }

Java
:  @@snip [DnsCompileOnlyDocTest.java](/docs/src/test/java/jdocs/actor/io/dns/DnsCompileOnlyDocTest.java) { #srv }

The `DnsProtocol.Resolved` will contain `org.apache.pekko.io.dns.SRVRecord`s.






