---
project.description: Details about security aspects of the remoting module for Apache Pekko Cluster.
---
# Remote Security

An @apidoc[actor.ActorSystem] should not be exposed via @ref:[Pekko Cluster](typed/cluster.md) or
@ref:[Pekko Remote](remoting-artery.md) over plain Aeron/UDP or TCP to an untrusted network, such as the Internet.
It should be protected by network security, such as a firewall. If that is not considered enough protection,
@ref:[TLS with mutual authentication](#remote-tls) should be enabled.

Best practice is that Pekko remoting nodes should only be accessible from the adjacent network. Note that if TLS is
enabled with mutual authentication there is still a risk that an attacker can gain access to a valid certificate by
compromising any node with certificates issued by the same internal PKI tree.

By default, @ref:[Java serialization](serialization.md#java-serialization) is disabled in Pekko. That is also a security
best practice because of its multiple
[known attack surfaces](https://docs.oracle.com/en/java/javase/25/core/addressing-serialization-vulnerabilities.html).

<a id="remote-tls"></a>
## Configuring SSL/TLS for Pekko Remoting

In addition to what is described here, see also @ref:[mTLS with rotated certificates in Kubernetes](#mtls-with-rotated-certificates-in-kubernetes).

SSL can be used as the remote transport by using the `tls-tcp` transport:

```
pekko.remote.artery {
  transport = tls-tcp
}
```

Next the actual SSL/TLS parameters have to be configured:

```
pekko.remote.artery {
  transport = tls-tcp

  ssl.config-ssl-engine {
    key-store = "/example/path/to/mykeystore.jks"
    trust-store = "/example/path/to/mytruststore.jks"

    key-store-password = ${SSL_KEY_STORE_PASSWORD}
    key-password = ${SSL_KEY_PASSWORD}
    trust-store-password = ${SSL_TRUST_STORE_PASSWORD}

    protocol = "TLSv1.3"

    enabled-algorithms = [TLS_AES_256_GCM_SHA384]
  }
}
```

Always use [substitution from environment variables](https://github.com/lightbend/config#optional-system-or-env-variable-overrides)
for passwords. Don't define real passwords in config files.

According to [RFC 7525](https://www.rfc-editor.org/rfc/rfc7525.html), the recommended algorithms to use with TLS 1.2
are:

 * TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
 * TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
 * TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
 * TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384

For TLS 1.3, these are good options:

 * TLS_AES_128_GCM_SHA256
 * TLS_AES_256_GCM_SHA384
 * TLS_CHACHA20_POLY1305_SHA256 (may not be supported on Java 8 runtimes)

You should always check the latest information about security and algorithm recommendations before configuring your
system.

Since Pekko remoting is inherently @ref:[peer-to-peer](general/remoting.md#symmetric-communication), both the key-store
and trust-store need to be configured on each remoting node participating in the cluster.

The official [Java Secure Socket Extension documentation](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html)
as well as the [Oracle documentation on creating KeyStore and TrustStores](https://docs.oracle.com/cd/E19509-01/820-3503/6nf1il6er/index.html)
are both great resources to research when setting up security on the JVM. Please consult those resources when
troubleshooting and configuring SSL.

Mutual authentication between TLS peers is enabled by default. Mutual authentication means that the passive side
(the TLS server side) of a connection will also request and verify a certificate from the connecting peer.
Without this mode only the client side is requesting and verifying certificates. While Pekko is a peer-to-peer
technology, each connection between nodes starts out from one side (the "client") towards the other (the "server").

Note that if TLS is enabled with mutual authentication there is still a risk that an attacker can gain access to a
valid certificate by compromising any node with certificates issued by the same internal PKI tree.

It's recommended that you enable hostname verification with
`pekko.remote.artery.ssl.config-ssl-engine.hostname-verification=on`.
When enabled it will verify that the destination hostname matches the hostname in the peer's certificate.

In deployments where hostnames are dynamic and not known up front it can make sense to leave the hostname verification
off.

You have a few choices how to set up certificates and hostname verification:

* Have a single set of keys and a single certificate for all nodes and *disable* hostname checking
    * The single set of keys and the single certificate is distributed to all nodes. The certificate can
      be self-signed as it is distributed both as a certificate for authentication but also as the trusted certificate.
    * If the keys/certificate are lost, someone else can connect to your cluster.
    * Adding nodes to the cluster is simple as the key material can be deployed / distributed to the new node.
* Have a single set of keys and a single certificate for all nodes that contains all of the host names and *enable*
  hostname checking.
    * This means that only the hosts mentioned in the certificate can connect to the cluster.
    * It cannot be checked, though, if the node you talk to is actually the node it is supposed to be (or if it is one
      of the other nodes). This seems like a minor restriction as you'll have to trust all cluster nodes the same in a
      Pekko cluster anyway.
    * The certificate can be self-signed in which case the same single certificate is distributed and trusted on all
      nodes (but see the next bullet).
    * Adding a new node means that its host name needs to conform to the trusted host names in the certificate.
      That either means to foresee new hosts, use a wildcard certificate, or use a full CA in the first place,
      so you can later issue more certificates if more nodes are to be added.
    * If a certificate is stolen, it can only be used to connect to the cluster from a node reachable via a hostname
      that is trusted in the certificate. It would require tampering with DNS to allow other nodes to get access to
      the cluster.
* Have a CA and then keys/certificates, one for each node, and *enable* host name checking.
    * Basically like internet HTTPS but that you only trust the internal CA and then issue certificates for each new node.
    * Needs a PKI, the CA certificate is trusted on all nodes, the individual certificates are used for authentication.
    * Only the CA certificate and the key/certificate for a node is distributed.
    * If keys/certificates are stolen, only the same node can access the cluster (unless DNS is tampered with as well).
      You can revoke single certificates.

See also a description of the settings in the @ref:[Remote Configuration](remoting-artery.md#remote-configuration-artery)
section.

@@@ note

When using SHA1PRNG on Linux it's recommended to specify `-Djava.security.egd=file:/dev/urandom` as argument
to the JVM to prevent blocking. It is NOT as secure because it reuses the seed.

@@@

## mTLS with rotated certificates in Kubernetes

Pekko remoting has support for using mTLS certificates that are frequently rotated. This support is designed
to work with cert-manager and other Kubernetes-based secret providers with a minimum of configuration.
This feature is important for secure Pekko deployments because it helps prevent malicious hosts from joining the
Pekko Cluster or eavesdropping on cluster communication.

@@@ note { title="Service mesh" }

Encryption and authentication via a service mesh is not a replacement for Pekko Cluster remoting security. Pekko
Cluster communication has peer-to-peer addressing requirements as described in
@ref:[Service mesh](general/remoting.md#service-mesh).

@@@

The examples below use [cert-manager](https://cert-manager.io/) to provision the certificates needed by Pekko. The
Kubernetes cluster should have a standard [cert-manager installation](https://cert-manager.io/docs/installation/).

### Understanding the certificates

First, a few concepts: cert-manager has a concept of `Certificates` and `Issuers`. A Certificate is a Custom Resource
Definition (CRD) that cert-manager reconciles into a Kubernetes `Secret` containing a TLS certificate. The `Certificate`
references an `Issuer`, and the `Issuer` describes how Certificates that reference it should be issued.

In order to support frequently rotated certificates, Pekko can't just use a self-signed certificate. Self-signed
certificates need to be the same at both ends to authenticate each other properly, and during the time when the
certificate is being rotated, two different Pekko nodes may have different certificates. Instead, Pekko needs
certificates issued by a certificate authority (CA). The CA verifies whether a certificate should be trusted, so during
rotation both the old and the new certificate can work together because both are signed by the same CA.

The CA Issuer itself needs a certificate to do its signing, and this certificate can also be provisioned by
cert-manager. That certificate is not rotated in this example. Its private key never gets shared with anything outside
of cert-manager, and so rotating it is not as necessary. Because of this, it will use a self-signed certificate, and
provisioning that certificate can be done by using a cert-manager self-signed `Issuer` type.

In total, we'll have two issuers: a self-signed issuer that issues certificates for the CA issuer, and then that CA
issuer will issue frequently rotated certificates for the Pekko service to use. The self-signed issuer, certificate,
and CA issuer can be reused across different Pekko deployments.

### Kubernetes resources

First we deploy the self-signed issuer:

```
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: self-signed-issuer
spec:
  selfSigned: {}
```

We're creating this for the whole cluster. Self-signed issuers don't have any state or configuration, so there is no
reason to have more than one for your entire cluster.

Next we create a self-signed certificate for our CA issuer to use that references this issuer:

```
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: pekko-tls-ca-certificate
  namespace: default
spec:
  issuerRef:
    name: self-signed-issuer
    kind: ClusterIssuer
  secretName: pekko-tls-ca-certificate
  commonName: default.pekko.cluster.local
  # 100 years
  duration: 876000h
  # 99 years
  renewBefore: 867240h
  isCA: true
  privateKey:
    rotationPolicy: Always
```

We've created this in the default namespace, which will be the same namespace that our Pekko service is deployed to.
If you're using a different namespace, you'll need to update accordingly.

The `commonName` isn't very important; it's not actually used anywhere, though may be useful for debugging why a
particular certificate isn't trusted by a service. We use a naming convention for common names and DNS names that
follows the pattern `<service-name>.<namespace>.pekko.cluster.local`. The CA uses the same convention without the
service name. This convention doesn't need to be followed, but it makes it easy to reason about the purpose of any
given certificate.

Now we create the CA issuer:

```
apiVersion: cert-manager.io/v1
kind: Issuer
metadata:
  name: pekko-tls-ca-issuer
  namespace: default
spec:
  ca:
    secretName: pekko-tls-ca-certificate
```

This uses the secret that we configured to be provisioned in the certificate above. Finally, we provision the
certificate that our Pekko service is going to use. We're assuming that the name of the service in this case is
`my-service`:

```
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: my-service-pekko-tls-certificate
  namespace: default
spec:
  issuerRef:
    name: pekko-tls-ca-issuer
  secretName: my-service-pekko-tls-certificate
  dnsNames:
  - my-service.default.pekko.cluster.local
  duration: 24h
  renewBefore: 16h
  privateKey:
    rotationPolicy: Always
```

The actual `dnsName` configured isn't important for Kubernetes service discovery, since Pekko Cluster does not use
these names for looking up the service, as long as it's unique to the service within the issuer.
`RotatingKeysSSLEngineProvider` applies an authorization check after the TLS handshake and verifies that the peer
certificate shares at least one common subject name (CN or SAN) with its own certificate. Again, we're using the
naming convention for the `dnsName` mentioned above.

This certificate is configured to last for 24 hours, and rotate every 16 hours.

If you have more Pekko services that you wish to deploy in the same namespace, you can reuse the same CA Issuer. You
only need to deploy an additional Certificate for each service.

### Enable TLS with RotatingKeysSSLEngineProvider

Add the following Pekko configuration:

```
pekko.remote.artery {
  transport = tls-tcp
  ssl.ssl-engine-provider = "org.apache.pekko.remote.artery.tcp.ssl.RotatingKeysSSLEngineProvider"
}
```

This instructs Pekko to use TLS with the `RotatingKeysSSLEngineProvider`, an SSL engine provider that is designed to
pick up Kubernetes TLS secrets and poll the file system for when they get rotated. It also applies authorization by
matching subject names from the incoming certificate with the subject names of its own certificate.

`RotatingKeysSSLEngineProvider` does not use the `ssl.config-ssl-engine.hostname-verification` setting. It scopes peers
with the post-handshake subject-name authorization described above, so all nodes that should join the same cluster must
have at least one common subject name (CN or SAN), and unrelated services should use different subject identities or
issuers.

### Configuring the Kubernetes deployment

In the Kubernetes `Deployment` for the Pekko application you need to mount the certificate at the path
`/var/run/secrets/pekko-tls/rotating-keys-engine`. This is the default path that the `RotatingKeysSSLEngineProvider`
uses to pick up its certificates. Add the following volume to your pod:

```
      volumes:
      - name: pekko-tls
        secret:
          secretName: my-service-pekko-tls-certificate
```

And then you can mount that in your container:

```
        volumeMounts:
        - name: pekko-tls
          mountPath: /var/run/secrets/pekko-tls/rotating-keys-engine
```

The complete deployment YAML when adding those to the Deployment Spec from
@extref:[Deploying a Pekko Cluster to Kubernetes](pekko-management:kubernetes-deployment/preparing-for-production.html#deployment-spec):

```
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: my-service
  name: my-service
  namespace: default
spec:
  replicas: 2
  selector:
    matchLabels:
      app: my-service
  template:
    metadata:
      labels:
        app: my-service
    spec:
      containers:
      - name: my-service
        image: my-service:latest
        readinessProbe:
          httpGet:
            path: /ready
            port: management
        livenessProbe:
          httpGet:
            path: /alive
            port: management
        ports:
        - name: management
          containerPort: 8558
          protocol: TCP
        - name: http
          containerPort: 8080
          protocol: TCP
        resources:
          limits:
            memory: 1024Mi
          requests:
            cpu: 2
            memory: 1024Mi
        volumeMounts:
        - name: pekko-tls
          mountPath: /var/run/secrets/pekko-tls/rotating-keys-engine
      volumes:
      - name: pekko-tls
        secret:
          secretName: my-service-pekko-tls-certificate
```

## Untrusted Mode

As soon as an actor system can connect to another remotely, it may in principle
send any possible message to any actor contained within that remote system. One
example may be sending a @apidoc[actor.PoisonPill] to the system guardian, shutting
that system down. This is not always desired, and it can be disabled with the
following setting:

```
pekko.remote.artery.untrusted-mode = on
```

This disallows sending of system messages (actor life-cycle commands,
DeathWatch, etc.) and any message extending @apidoc[actor.PossiblyHarmful] to the
system on which this flag is set. Should a client send them nonetheless they
are dropped and logged (at DEBUG level in order to reduce the possibilities for
a denial of service attack). `PossiblyHarmful` covers the predefined
messages like @apidoc[actor.PoisonPill] and @apidoc[actor.Kill], but it can also be added
as a marker trait to user-defined messages.

@@@ warning

Untrusted mode does not give full protection against attacks by itself.
It makes it slightly harder to perform malicious or unintended actions but
it should be noted that @ref:[Java serialization](serialization.md#java-serialization)
should still not be enabled.
Additional protection can be achieved when running in an untrusted network by
network security (e.g. firewalls) and/or enabling @ref:[TLS with mutual authentication](#remote-tls).

@@@

Messages sent with actor selection are by default discarded in untrusted mode, but
permission to receive actor selection messages can be granted to specific actors
defined in configuration:

```
pekko.remote.artery.trusted-selection-paths = ["/user/receptionist", "/user/namingService"]
```

The actual message must still not be of type `PossiblyHarmful`.

In summary, the following operations are ignored by a system configured in
untrusted mode when incoming via the remoting layer:

 * remote deployment (which also means no remote supervision)
 * remote DeathWatch
 * @apidoc[system.stop()](actor.ActorRefFactory) {scala="#stop(actor:org.apache.pekko.actor.ActorRef):Unit" java="#stop(org.apache.pekko.actor.ActorRef)"}, @apidoc[actor.PoisonPill], @apidoc[actor.Kill]
 * sending any message which extends from the @apidoc[actor.PossiblyHarmful] marker
   interface, which includes @apidoc[actor.Terminated]
 * messages sent with actor selection, unless destination defined in `trusted-selection-paths`.

@@@ note

Enabling the untrusted mode does not remove the capability of the client to
freely choose the target of its message sends, which means that messages not
prohibited by the above rules can be sent to any actor in the remote system.
It is good practice for a client-facing system to only contain a well-defined
set of entry point actors, which then forward requests (possibly after
performing validation) to another actor system containing the actual worker
actors. If messaging between these two server-side systems is done using
local @apidoc[actor.ActorRef] (they can be exchanged safely between actor systems
within the same JVM), you can restrict the messages on this interface by
marking them @apidoc[actor.PossiblyHarmful] so that a client cannot forge them.

@@@
