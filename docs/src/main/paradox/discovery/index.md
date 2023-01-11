---
project.description: Service discovery with Pekko using DNS, Kubernetes, AWS, Consul or Marathon.
---
# Discovery

The Pekko Discovery API enables **service discovery** to be provided by different technologies. 
It allows to delegate endpoint lookup so that services can be configured depending on the environment by other means than configuration files. 

Implementations provided by the Pekko Discovery module are 

* @ref:[Configuration](#discovery-method-configuration) (HOCON)
* @ref:[DNS](#discovery-method-dns) (SRV records)
* @ref:[Aggregate](#discovery-method-aggregate-multiple-discovery-methods) multiple discovery methods

In addition, the @extref:[Pekko Management](pekko-management:) toolbox contains Pekko Discovery implementations for

* @extref:[Kubernetes API](pekko-management:discovery/kubernetes.html)
* @extref:[AWS API: EC2 Tag-Based Discovery](pekko-management:discovery/aws.html#discovery-method-aws-api-ec2-tag-based-discovery)
* @extref:[AWS API: ECS Discovery](pekko-management:discovery/aws.html#discovery-method-aws-api-ecs-discovery)
* @extref:[Consul](pekko-management:discovery/consul.html)
* @extref:[Marathon API](pekko-management:discovery/marathon.html)

## Module info

@@dependency[sbt,Gradle,Maven] {
  bomGroup=org.apache.pekko bomArtifact=pekko-bom_$scala.binary.version$ bomVersionSymbols=PekkoVersion
  symbol1=PekkoVersion
  value1="$pekko.version$"
  group="org.apache.pekko"
  artifact="pekko-discovery_$scala.binary.version$"
  version=PekkoVersion
}

@@project-info{ projectId="discovery" }

## How it works

Loading the extension:

Scala
:  @@snip [CompileOnlySpec.scala](/discovery/src/test/scala/doc/org/apache/pekko/discovery/CompileOnlySpec.scala) { #loading }

Java
:  @@snip [CompileOnlyTest.java](/discovery/src/test/java/jdoc/org/apache/pekko/discovery/CompileOnlyTest.java) { #loading }

A `Lookup` contains a mandatory `serviceName` and an optional `portName` and `protocol`. How these are interpreted is discovery 
method dependent e.g.DNS does an A/AAAA record query if any of the fields are missing and an SRV query for a full look up:

Scala
:  @@snip [CompileOnlySpec.scala](/discovery/src/test/scala/doc/org/apache/pekko/discovery/CompileOnlySpec.scala) { #basic }

Java
:  @@snip [CompileOnlyTest.java](/discovery/src/test/java/jdoc/org/apache/pekko/discovery/CompileOnlyTest.java) { #basic }


`portName` and `protocol` are optional and their meaning is interpreted by the method.

Scala
:  @@snip [CompileOnlySpec.scala](/discovery/src/test/scala/doc/org/apache/pekko/discovery/CompileOnlySpec.scala) { #full }

Java
:  @@snip [CompileOnlyTest.java](/discovery/src/test/java/jdoc/org/apache/pekko/discovery/CompileOnlyTest.java) { #full }

Port can be used when a service opens multiple ports e.g. a HTTP port and an Pekko remoting port.

## Discovery Method: DNS

@@@ note { title="Async DNS" }

Pekko Discovery with DNS does always use the @ref[Pekko-native "async-dns" implementation](../io-dns.md) (it is independent of the `pekko.io.dns.resolver` setting).

@@@

DNS discovery maps `Lookup` queries as follows:

* `serviceName`, `portName` and `protocol` set: SRV query in the form: `_port._protocol.name` Where the `_`s are added.
* Any query  missing any of the fields is mapped to a A/AAAA query for the `serviceName`

The mapping between Pekko service discovery terminology and SRV terminology:

* SRV service = port
* SRV name = serviceName
* SRV protocol = protocol

Configure `pekko-dns` to be used as the discovery implementation in your `application.conf`:

@@snip[application.conf](/docs/src/test/scala/docs/discovery/DnsDiscoveryDocSpec.scala){ #configure-dns }

From there on, you can use the generic API that hides the fact which discovery method is being used by calling:

Scala
:   @@snip[snip](/docs/src/test/scala/docs/discovery/DnsDiscoveryDocSpec.scala){ #lookup-dns }

Java
:   @@snip[snip](/docs/src/test/java/jdocs/discovery/DnsDiscoveryDocTest.java){ #lookup-dns }

### DNS records used

DNS discovery will use either A/AAAA records or SRV records depending on whether a `Simple` or `Full` lookup is issued.
The advantage of SRV records is that they can include a port.

#### SRV records

Lookups with all the fields set become SRV queries. For example:

```
dig srv _service._tcp.pekko.test

; <<>> DiG 9.11.3-RedHat-9.11.3-6.fc28 <<>> srv service.tcp.pekko.test
;; global options: +cmd
;; Got answer:
;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 60023
;; flags: qr aa rd ra; QUERY: 1, ANSWER: 2, AUTHORITY: 1, ADDITIONAL: 5

;; OPT PSEUDOSECTION:
; EDNS: version: 0, flags:; udp: 4096
; COOKIE: 5ab8dd4622e632f6190f54de5b28bb8fb1b930a5333c3862 (good)
;; QUESTION SECTION:
;service.tcp.pekko.test.         IN      SRV

;; ANSWER SECTION:
_service._tcp.pekko.test.  86400   IN      SRV     10 60 5060 a-single.pekko.test.
_service._tcp.pekko.test.  86400   IN      SRV     10 40 5070 a-double.pekko.test.

```

In this case `service.tcp.pekko.test` resolves to `a-single.pekko.test` on port `5060`
and `a-double.pekko.test` on port `5070`. Currently discovery does not support the weightings.

#### A/AAAA records

Lookups with any fields missing become A/AAAA record queries. For example:

```
dig a-double.pekko.test

; <<>> DiG 9.11.3-RedHat-9.11.3-6.fc28 <<>> a-double.pekko.test
;; global options: +cmd
;; Got answer:
;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 11983
;; flags: qr aa rd ra; QUERY: 1, ANSWER: 2, AUTHORITY: 1, ADDITIONAL: 2

;; OPT PSEUDOSECTION:
; EDNS: version: 0, flags:; udp: 4096
; COOKIE: 16e9815d9ca2514d2f3879265b28bad05ff7b4a82721edd0 (good)
;; QUESTION SECTION:
;a-double.pekko.test.            IN      A

;; ANSWER SECTION:
a-double.pekko.test.     86400   IN      A       192.168.1.21
a-double.pekko.test.     86400   IN      A       192.168.1.22

```

In this case `a-double.pekko.test` would resolve to `192.168.1.21` and `192.168.1.22`.

## Discovery Method: Configuration

Configuration currently ignores all fields apart from service name.

For simple use cases configuration can be used for service discovery. The advantage of using Pekko Discovery with
configuration rather than your own configuration values is that applications can be migrated to a more
sophisticated discovery method without any code changes.


Configure it to be used as discovery method in your `application.conf`

```
pekko {
  discovery.method = config
}
```

By default the services discoverable are defined in `pekko.discovery.config.services` and have the following format:

```
pekko.discovery.config.services = {
  service1 = {
    endpoints = [
      {
        host = "cat"
        port = 1233
      },
      {
        host = "dog"
        port = 1234
      }
    ]
  },
  service2 = {
    endpoints = []
  }
}
```

Where the above block defines two services, `service1` and `service2`.
Each service can have multiple endpoints.

## Discovery Method: Aggregate multiple discovery methods

Aggregate discovery allows multiple discovery methods to be aggregated e.g. try and resolve
via DNS and fall back to configuration.

To use aggregate discovery add its dependency as well as all of the discovery that you
want to aggregate.

Configure `aggregate` as `pekko.discovery.method` and which discovery methods are tried and in which order.

```
pekko {
  discovery {
    method = aggregate
    aggregate {
      discovery-methods = ["pekko-dns", "config"]
    }
    config {
      services {
        service1 {
          endpoints = [
            {
              host = "host1"
              port = 1233
            },
            {
              host = "host2"
              port = 1234
            }
          ]
        }
      }
    }
  }
}

```

The above configuration will result in `pekko-dns` first being checked and if it fails or returns no
targets for the given service name then `config` is queried which i configured with one service called
`service1` which two hosts `host1` and `host2`.

## Migrating from Pekko Management Discovery (before 1.0.0)

Pekko Discovery started out as a submodule of Pekko Management, before 1.0.0 of Pekko Management. Pekko Discovery is not compatible with those versions of Pekko Management Discovery.

At least version `1.0.0` of any Pekko Management module should be used if also using Pekko Discovery.

Migration steps:

* Any custom discovery method should now implement `org.apache.pekko.discovery.ServiceDiscovery`
* `discovery-method` now has to be a configuration location under `pekko.discovery` with at minimum a property `class` specifying the fully qualified name of the implementation of `org.apache.pekko.discovery.ServiceDiscovery`.
  Previous versions allowed this to be a class name or a fully qualified config location e.g. `pekko.discovery.kubernetes-api` rather than just `kubernetes-api`



