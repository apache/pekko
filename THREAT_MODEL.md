# Apache Pekko — Threat Model

**Status:** DRAFT — awaiting Pekko PMC review. Not yet maintainer-ratified.

| | |
| --- | --- |
| **Project** | Apache Pekko (core toolkit) |
| **Written against** | commit `90b02d60`, `main` |
| **Date** | 2026-08-27 |
| **Authors** | ASF Security team, at the request of the Pekko PMC |
| **Version binding** | This model is versioned alongside the project. A report against Pekko version *N* is triaged against the model as it stood at *N*, not at `main`. |
| **Reporting** | Findings that violate a §8 property should be reported per [`SECURITY.md`](SECURITY.md). Findings that fall under §3 or §9 will be closed citing this document. |

**Provenance legend.** Every non-trivial claim is tagged:
*(documented)* — stated in Pekko's own docs, cited.
*(maintainer)* — stated by a Pekko maintainer in review of this document.
*(inferred)* — reasoned from code or config defaults, **not yet confirmed**; each has a matching question in §14.

**Draft confidence:** 41 documented / 43 maintainer / 4 inferred. The documented share is unusually high because Pekko's own remoting and serialization docs already state much of the trust model explicitly. The §5a default rulings — previously the largest inferred block — are now answered by the §5b posture statement and §14 Q1 to Q5. What remains inferred is the residual module in/out split (§14 Q6) and the clock assumption (§14 Q12).

## §1 Overview

Apache Pekko is a Scala/Java toolkit for building concurrent, distributed and resilient message-driven applications. Its unit of computation is the actor: an object with private state that communicates only by asynchronous message passing. Pekko extends that model across machines — an `ActorSystem` can address actors on remote nodes as if they were local, and a cluster of such systems provides membership, sharding, singletons and replicated data. That transparency is the source of most of this document: **the network is not incidental to Pekko, it is part of the programming model**, and the security consequences follow from where the toolkit assumes that network sits.

---

## §2 Scope and intended use

Pekko is an **in-process library** that the application embeds and configures. It is not a server, not a daemon, and ships no runnable artifact that an operator deploys on its own. There is consequently no "Pekko instance" to secure independently of the application hosting it.

Three caller roles matter, and they are not equally trusted:

- **The embedding application** — fully trusted. It constructs the `ActorSystem`, supplies configuration, and already shares the JVM with Pekko.
- **The operator/deployer** — trusted for the instance. Chooses transport, TLS material, and the network the node sits on. Most of §10 lands here.
- **The remote peer** — another `ActorSystem` that has associated over remoting. Its trust level is the central question of this model, and §7 answers it.

### Component families

| Family | Modules | Entry point | Leaves the process? | In model |
| --- | --- | --- | --- | --- |
| Actor core | `actor`, `actor-typed`, `slf4j`, `coordination` | `ActorSystem`, `ActorRef` | no | **yes** |
| Classic IO | `actor` (`org.apache.pekko.io`) | `IO(Tcp)`, `IO(Udp)`, `IO(Dns)` | **network — if the application binds** | **yes** |
| Remoting | `remote` | Artery transport (`tcp` / `tls-tcp` / `aeron-udp`) | **network** | **yes — primary surface** |
| Cluster | `cluster`, `cluster-typed`, `cluster-tools`, `cluster-sharding*`, `cluster-metrics` | gossip, membership, sharding | **network** (via remoting) | **yes** |
| Replicated data | `distributed-data` | CRDT replication | **network** (via remoting) | **yes** |
| Serialization | `serialization-jackson`, `serialization-jackson3` | `Serializer` SPI | deserializes network bytes | **yes — critical** |
| Wire encoding | `protobuf-v3` | shaded protobuf runtime | parses remoting and cluster wire bytes | **yes** |
| Streams | `stream`, `stream-typed` | `Source`/`Flow`/`Sink`; `Tcp`, `TLS`, `FileIO`, `Framing` | **network / filesystem — if the application uses those connectors** | **yes** |
| Persistence | `persistence`, `persistence-typed`, `persistence-query`, `persistence-shared` | journal / snapshot plugin SPI | **storage backend** | **yes**, boundary at the SPI |
| PKI | `pki` | PEM/keystore parsing | **reads files** | **yes** |
| Discovery | `discovery` | service-discovery SPI | **network / DNS** | **yes** |
| OSGi | `osgi` | bundle activator | classloading | **yes** *(maintainer — §14 Q6)* |
| Test kits | `*-testkit`, `multi-node-testkit`, `*-tests`, `persistence-tck`, `stream-tests-tck` | — | — | **no** — §3 |
| Benchmarks | `bench-jmh` | — | — | **no** — §3 |
| Build / docs | `docs`, `project`, `scripts`, `legal`, `kubernetes` (test-cluster provisioning scripts), `plugins`, `bill-of-materials`, `scala-nightly` | — | — | **no** — §3 |

*(inferred — the in/out split is the ASF Security team's proposal, except `osgi`, which is confirmed in model; see §14 Q6)*

---

## §3 Out of scope (explicit non-goals)

- **Test kits, benchmarks, build tooling and documentation sources.** These ship in the repository but are not part of the security-supported surface. A finding in `bench-jmh` or any `*-tests` module is `OUT-OF-MODEL: unsupported-component`. *(inferred — §14 Q6)*
- **Pekko is not a sandbox.** Actors are not an isolation boundary. Any code running in the JVM can reach any actor's state by ordinary means; the actor model is a concurrency discipline, not a security control. *(maintainer — §14 Q4)*
- **Pekko is not an authorization framework.** It carries no notion of a principal, role, or permission on a message. Application-level authorization is the embedding application's job. *(maintainer — §14 Q11)*
- **A Pekko cluster is not a multi-tenancy boundary.** The documentation is explicit that *"you'll have to trust all cluster nodes the same in a Pekko cluster anyway"* *(documented — `remote-security.md`)*. Separating mutually-distrusting tenants across nodes of one cluster is not a supported deployment.
- **Attackers who already control the embedding process** are out of scope — they have already won. *(maintainer — §14 Q11)*
- **This document describes the `apache/pekko` core toolkit.** Its technical content — the §2 component families, the §5a defaults, the §8 properties — is about that codebase. Other Apache Pekko deliverables (Pekko Management, Pekko HTTP, Pekko gRPC, Pekko Connectors, Pekko Projection, the persistence plugins) ship from their own repositories, and this model does not enumerate their surfaces.

    That is a limit on **coverage, not on standing**. Reporting and triage are identical across all Pekko repositories: the same address (see [`SECURITY.md`](SECURITY.md)), the same PMC, and the same posture — an implementation defect is Pekko's to fix, while a request to change a default is a change request under §5b, not a vulnerability. A finding in another Pekko repository is **not** `OUT-OF-MODEL: unsupported-component`; that disposition is for the §3 components above, not for code that simply lives elsewhere. *(maintainer — §14 Q6)*

---

## §4 Trust boundaries and data flow

**The primary trust boundary is the remoting network boundary**, and Pekko's documentation places it unusually explicitly:

> "An `ActorSystem` should not be exposed via Pekko Cluster or Pekko Remote over plain Aeron/UDP or TCP to an untrusted network, such as the Internet. It should be protected by network security, such as a firewall. If that is not considered enough protection, TLS with mutual authentication should be enabled."
> — *(documented — `remote-security.md`)*

and states the consequence of crossing it:

> "As soon as an actor system can connect to another remotely, it may in principle send any possible message to any actor contained within that remote system."
> — *(documented — `remote-security.md`, Untrusted Mode)*

Read together, these define the model: **remoting assumes it runs on a network the operator has already restricted.** Association is the security decision; once a peer is associated, it is inside. Pekko offers two mechanisms to tighten that (TLS mutual authentication, untrusted mode) but neither converts remoting into a boundary that is safe to face the open internet *(documented — see §9)*.

The application's own boundaries — the HTTP endpoint, the message broker, the database — sit **outside** Pekko and are the embedding application's responsibility.

### Reachability preconditions per family

A finding must meet its family's precondition to be in-model:

- **Remoting** — reachable from bytes arriving on the Artery transport, *and* the report must state whether it assumes the network-isolation assumption above is intact. A finding that requires an attacker already on the cluster network is judged under §7, not automatically valid.
- **Serialization** — reachable from a message payload deserialized by a **configured, enabled** serializer. Findings reachable only when `allow-java-serialization = on` are judged under §5a.
- **Cluster / distributed-data** — reachable from gossip or replication traffic originating at an **associated peer**. Per §7 such a peer is trusted, so these are typically out of model unless the finding shows a pre-association reach.
- **Actor core / streams** — reachable from data the embedding application passes in. Trusted by default; a finding must show the data crosses an application boundary that Pekko itself defines.
- **Persistence** — reachable from journal or snapshot contents. The store is trusted *(maintainer — §14 Q5)*, so a finding requiring write access to it is out of model; a defect in Pekko's replay handling is not.
- **PKI** — reachable from PEM/keystore material. Operator-supplied and trusted per §6.

---

## §5 Assumptions about the environment

- **Runtime.** A conformant JVM. Pekko does not defend against a hostile JVM, a hostile classpath, or an attacker with local code execution in the same process. *(maintainer — §14 Q11)*
- **Network adjacency.** *"Best practice is that Pekko remoting nodes should only be accessible from the adjacent network."* *(documented — `remote-security.md`)*
- **PKI scope.** Where TLS is used, every certificate issued by the same internal PKI tree is equivalent: *"there is still a risk that an attacker can gain access to a valid certificate by compromising any node with certificates issued by the same internal PKI tree."* *(documented — `remote-security.md`)*
- **Clock.** The failure detector and gossip convergence depend on reasonably-behaved local clocks. Pekko does not defend against adversarial clock manipulation on a cluster node. *(inferred — §14 Q12)*
- **Entropy.** On Linux with SHA1PRNG, the docs recommend `-Djava.security.egd=file:/dev/urandom` to avoid blocking, noting it *"is NOT as secure because it reuses the seed."* *(documented — `remote-security.md`)*

### What Pekko does not do to its host

These are negative claims, rarely written down anywhere. Each is confirmed and carries the citation, or the exception, behind it *(maintainer — §14 Q7)*:

- Installs no signal handlers and spawns no child processes — no `sun.misc.Signal`/`SignalHandler`, `Runtime.exec` or `ProcessBuilder` in the main sources. It does register **one JVM shutdown hook**, via `CoordinatedShutdown` (`actor/.../actor/CoordinatedShutdown.scala:381`).
- Opens no listening socket of its own accord. Remoting binds when configured; `org.apache.pekko.io.Tcp`/`Udp` (in `pekko-actor`) and `stream.scaladsl.Tcp` (in `pekko-stream`) bind only on an explicit application call. Pekko never binds a port the application did not ask for.
- **Reads environment variables during configuration startup, but never modifies them** *(maintainer — §14 Q7)*. Pekko itself calls neither `System.getenv` nor `sys.env` anywhere in the main sources; environment values reach it only through HOCON `${?VAR}` substitution when `ConfigFactory.load` resolves the configuration (`actor/.../actor/ActorSystem.scala:281`). That path is deliberate and documented — it is how the docs tell operators to supply passwords (§10.8).
- Writes to logging via the configured logger. One exception: `StandardOutLogger` prints to stdout with `println` (`actor/.../event/Logging.scala:1024` onward). It carries the very early startup log, before the configured loggers are running, and is bounded by `pekko.stdout-loglevel`, which defaults to `WARNING`.
- Does not mutate process-global state at initialization — no `System.setProperty`, `Locale.setDefault` or `TimeZone.setDefault` in the main sources.

---

## §5a Configuration variants that change the security envelope

Pekko's security posture is set almost entirely by configuration. **Every row below whose default is the weaker value needs a maintainer ruling** — see §14.

| Setting | Default | Effect | Maintainer stance |
| --- | --- | --- | --- |
| `pekko.actor.allow-java-serialization` | `off` | On, exposes the JVM deserialization attack surface to any message payload. Docs: *"highly discouraged to enable in production"* *(documented — `serialization.md`)* | Secure default. Enabling it transfers gadget-chain defence to the operator — §5b, §14 Q2 *(maintainer)* |
| `pekko.remote.artery.transport` | `tcp` | **Plaintext.** No peer authentication and no confidentiality on the wire. `tls-tcp` opts into TLS | Compatibility default — §5b, §14 Q1 *(maintainer)* |
| `pekko.remote.artery.ssl.config-ssl-engine.require-mutual-authentication` | `on` | Both ends present certificates *(documented)* | Secure default |
| `…ssl.config-ssl-engine.hostname-verification` | `off` | Off, a valid cert from the trusted PKI authenticates regardless of which host presents it. Docs *recommend* `on` but ship `off` *(documented)* | Compatibility default, warned at runtime — §5b, §14 Q3 *(maintainer)* |
| `pekko.remote.artery.untrusted-mode` | `off` | On, blocks inbound system messages, `PossiblyHarmful` messages, remote deployment, remote DeathWatch, and actor selections outside `trusted-selection-paths` *(documented)* | Hardening; adoption is the operator's call — §5b, §14 Q4 *(maintainer)* |
| `pekko.remote.artery.trusted-selection-paths` | `[]` | Allow-list of actor paths that may receive selections under untrusted mode *(documented)* | Follows Q4 |
| `pekko.remote.deployment.enable-allow-list` | `off` | On, restricts which actor classes a peer may remote-deploy *(documented — `remoting.md`)* | Hardening; adoption is the operator's call — §5b, §14 Q4 *(maintainer)* |
| `pekko.remote.classic.untrusted-mode` | `off` | Classic-remoting equivalent of the artery flag *(reference.conf:381)* | Follows Q4 |
| `pekko.remote.classic.trusted-selection-paths` | `[]` | As above *(reference.conf:387)* | Follows Q4 |
| `pekko.remote.classic.netty.tcp.enable-ssl` | `false` | Classic's default transport is plaintext netty TCP *(reference.conf:582)* | Follows Q1 |
| `pekko.actor.serialize-messages` / `serialize-creators` | `off` | Testing aids that force serialization round-trips. Not security controls. Docs: *"this is only intended for testing"* *(documented — `actor/src/main/resources/reference.conf`)* | Not a security knob |

---

## §5b Security posture: hardening, not secure-by-default

Pekko is a long-lived toolkit whose deployment base is inherited from Akka. Its configuration defaults are chosen for compatibility with those deployments, in which operators have already been tasked with securing the network Pekko runs on and controlling who may reach and message a deployed system. Changing a default to a more restrictive value breaks those deployments on upgrade, sometimes without a clear signal as to why.

Pekko therefore takes the following position *(maintainer)*:

1. **Defaults are compatibility choices, not security claims.** §5a lists every setting whose default affects the security envelope; §10 lists what the operator must do as a result. Read together they are the contract: Pekko states what it does not provide, and states what it expects of the operator instead.
2. **A report that a default should be more restrictive is not a vulnerability report.** It is a change request, and is closed as `BY-DESIGN: default-configuration` (§13).
3. **Proposals to change a default are welcome, and belong on the development list.** The PMC will weigh them in good faith on their merits — the compatibility cost, whether a migration path exists, and whether a major version is in flight. Defaults can and do change; they change through project discussion, not as the remediation of a security report.
4. **If an implementation is wrong, Pekko fixes it.** Where a control does not do what it is documented to do once enabled, that is a defect, in scope, at the severity §8 assigns. This posture governs which value ships as the default — never whether the mechanism works.

---

## §6 Assumptions about inputs

For a toolkit whose surface is a wire protocol, the useful table is keyed by **message class**, not by function.

| Source | Input | Attacker-controllable? | Who must enforce what |
| --- | --- | --- | --- |
| Artery transport | Inbound frame headers / framing | **Yes if the network-isolation assumption fails** | Operator: network isolation (§10), or TLS |
| Artery transport | Serialized user-message payload | **Yes**, from an associated peer | Pekko: only enabled serializers run. App: validate semantic content |
| Artery transport | System messages (`Create`, `Terminate`, `Watch`, `Supervise`) | **Yes**, from an associated peer unless untrusted mode is on | Operator: `untrusted-mode` if peers are less than fully trusted |
| Artery transport | `PossiblyHarmful` messages (`PoisonPill`, `Kill`) | **Yes**, same condition | Same |
| Artery transport | Remote-deployment `Props` | **Yes**, same condition | Operator: `enable-allow-list` |
| Cluster | Gossip / membership state | From an **associated** peer — trusted per §7 | — |
| `distributed-data` | Replicated CRDT deltas | From an associated peer — trusted per §7 | — |
| Persistence | Journal / snapshot contents on replay | **No** — the store is trusted *(maintainer — §14 Q5)* | Operator/DBA: secure the store |
| `pki` | PEM / keystore files | **No** — operator-supplied, trusted | Operator: protect key material |
| `discovery` | Service-discovery responses (DNS, K8s API) | **Potentially** — depends on the resolver | Operator: trust the discovery mechanism |
| `io.Tcp` / `io.Udp` | Bytes on an application-bound socket | **Potentially** — depends where the application exposes it | App: it chose to bind, defines the protocol, and owns what it exposes |
| `stream.Tcp` / `stream.TLS` | Bytes on an application-bound stream server | **Potentially** — as above | App: as above. TLS itself is delegated to the JDK's JSSE |
| `Framing` / `JsonFraming` | Delimited or length-prefixed frames | **Potentially** where fed from a network source | App: supplies `maximumFrameLength`, which bounds frame size |
| `stream.FileIO` | File contents at an application-supplied path | Depends on the path | App |
| Config | `application.conf`, system properties | **No** — trusted, part of the deployment | Operator |

**Size and rate.** Artery imposes frame-size limits and the failure detector bounds how long an unresponsive peer is tolerated. Whether these are *security* controls or tuning parameters is §14 Q8.

---

## §7 Adversary model

**In scope:**

- **An unassociated network attacker** who can reach the remoting port — in a deployment where the operator's isolation assumption has held, this attacker should not exist; where it does, the relevant question is whether they can achieve anything **before** association completes. Pre-association reachability is the sharpest in-model attack surface. *(maintainer — §14 Q1)*
- **An attacker supplying message content** to an otherwise legitimate peer — e.g. data that originates at the application's own untrusted edge and is forwarded into an actor message. *(maintainer — §14 Q11)*

**Explicitly out of scope:**

- **An associated peer behaving arbitrarily.** There is no Byzantine-peer model. The documentation is direct: *"you'll have to trust all cluster nodes the same in a Pekko cluster anyway"* *(documented)*, and *"as soon as an actor system can connect to another remotely, it may in principle send any possible message to any actor contained within that remote system"* *(documented)*. **A finding whose precondition is "a cluster member misbehaves" is out of model** — there is no honest-majority threshold to state, because the model has no notion of a dishonest member. This generalises to every cluster protocol — cluster membership, sharding, singleton and `distributed-data` alike: **Pekko makes no guarantee of being able to recognise a compromised node.** Failure detection is heartbeat-based (`remote/.../PhiAccrualFailureDetector.scala`), so it identifies members that stop responding, not members that respond dishonestly; a compromised node that keeps heartbeating is indistinguishable from a healthy one. *(maintainer — §14 Q9)*
- **An attacker holding any certificate from the cluster's PKI tree.** Documented as equivalent to cluster access *(documented)*.
- **An attacker with code execution in the embedding JVM.** Already inside the trust boundary.
- **Side-channel observers.** Pekko makes no timing or memory-access guarantees. *(maintainer — §14 Q11)*

---

## §8 Security properties Pekko provides

| # | Property & conditions | Violation symptom | Severity | Provenance |
| --- | --- | --- | --- | --- |
| P1 | **Java serialization is disabled by default**; Pekko uses it for none of its own internal messages | A payload deserialized via Java serialization under default config | **Critical** — RCE class | *(documented — `serialization.md`)* |
| P2 | The disabled Java serializer **logs rejected attempts** under the `SECURITY` marker, and those *"SHOULD be treated as potential attacks which the serializer prevented"* | Silent acceptance where rejection + log is expected | High | *(documented — `serialization.md`)* |
| P3 | **Remote deployment is not remote code loading.** The actor class must already be present on the target system | A peer causing a class absent from the target's classpath to execute | **Critical** | *(documented — `remoting.md`)* |
| P4 | With `enable-allow-list = on`, only listed actor classes may be remote-deployed onto this node | An unlisted class deployed | High | *(documented — `remoting.md`)* |
| P5 | With `untrusted-mode = on`, inbound system messages, `PossiblyHarmful` messages, remote deployment, remote DeathWatch and non-allow-listed actor selections are **dropped and logged** | Any of these taking effect despite the flag | High | *(documented — `remote-security.md`)* |
| P6 | With `transport = tls-tcp`, TLS is applied and **mutual authentication is on by default** — the server side also requests and verifies the client's certificate | Association completing without peer certificate verification | **Critical** | *(documented — `remote-security.md`)* |
| P7 | Certificate rotation is supported for mTLS in Kubernetes without cluster restart | Rotation causing association failure or silent downgrade | Medium | *(documented — `remote-security.md`; **2.0+ only**, absent from the released 1.x docs — [snapshot](https://pekko.apache.org/docs/pekko/snapshot/remote-security.html#mtls-with-rotated-certificates-in-kubernetes))* |

**Note the shape of this list:** P4, P5 and P6 are all *conditional on a non-default setting*. Under stock configuration, the properties Pekko actively provides at the network boundary are P1, P2 and P3 — the rest of the posture is delegated to the operator via §10. This is a deliberate design, but it is the single most important thing for a triager to understand.

---

## §9 Security properties Pekko does **not** provide

- **No peer authentication by default.** With the default `transport = tcp` there is no shared secret, no certificate, and no handshake credential. Any host that can reach the port and speak Artery can attempt association. *(maintainer — §14 Q1; §5b)*
- **No confidentiality or integrity on the wire by default.** Same cause. *(maintainer — §14 Q1; §5b)*
- **No intra-cluster authorization.** Once associated, a peer may address any actor in the system. There is no per-actor, per-message, or per-peer permission model. *(documented)*
- **No Byzantine fault tolerance, and no compromised-node detection.** See §7. Cluster protocols assume members are honest; there is no threshold below which arbitrary member behaviour is tolerated *(documented — `remote-security.md`)*, and Pekko offers no mechanism that would identify a member as compromised in the first place *(maintainer — §14 Q9)*.
- **No bound on blast radius from one compromised node.** Documented explicitly for the PKI case *(documented)*.
- **No protection once Java serialization is enabled.** Turning it on re-exposes the full JVM deserialization surface; the docs place this squarely on the operator. *(documented)*

### False friends

These are the assumptions integrators most often bring with them, and each is wrong:

- **Untrusted mode is not a security boundary.** *"Untrusted mode does not give full protection against attacks by itself. It makes it slightly harder to perform malicious or unintended actions"* *(documented — `remote-security.md`)*. It is hardening. Treating it as a substitute for network isolation is a §11 misuse.
- **`PossiblyHarmful` is a marker, not an authorization mechanism.** It is a compile-time trait that untrusted mode consults. It confers no protection when untrusted mode is off.
- **A service mesh is not a substitute for remoting security.** *"Encryption and authentication via a service mesh is not a replacement for Pekko Cluster remoting security"* — Pekko's peer-to-peer addressing has requirements a mesh does not satisfy *(documented — `remote-security.md`; **2.0+ only**, absent from the released 1.x docs — [snapshot](https://pekko.apache.org/docs/pekko/snapshot/general/remoting.html#service-mesh))*.
- **TLS mutual authentication does not give per-node identity guarantees by default**, because `hostname-verification` ships `off` — any cert from the trusted PKI authenticates as any node. *(documented + config default)*
- **The actor boundary is not a security boundary.** Message-passing isolation is a concurrency property, not a confidentiality one.

### Well-known attack classes left to the caller

- **JVM deserialization gadget chains** — mitigated by P1 only so long as Java serialization stays off, and only for payloads Pekko itself deserializes; application-level serializers are the application's problem. Pekko integrates **no** serialization filter: `JavaSerializer.fromBinary` performs an unfiltered `ObjectInputStream.readObject` (`actor/.../serialization/Serializer.scala`). An operator who enables Java serialization must supply the allow list themselves through the JVM — `-Djdk.serialFilter` or a process-wide `ObjectInputFilter` — and owns that entirely *(maintainer — §14 Q2)*.
- **Resource-exhaustion via message volume or size** — see §14 Q8.
- **DNS / service-discovery spoofing** — `discovery` trusts the resolver it is configured with.
- **Storage-layer tampering** on persistence journals and snapshot stores — the store is trusted, and securing it belongs to whoever administers it *(maintainer — §14 Q5)*.

---

## §10 Downstream responsibilities

The operator or embedding application must:

1. **Keep remoting off untrusted networks.** Firewall the remoting port to the adjacent network. This is the assumption the whole model rests on *(documented)*.
2. **Enable `tls-tcp` if the network is not sufficiently trusted**, and set `hostname-verification = on` unless hostnames are genuinely dynamic *(documented)*.
3. **Leave `allow-java-serialization = off`.** If it must be enabled for legacy compatibility, treat the deployment as having no deserialization protection *(documented)*, and **maintain your own gadget-chain allow list** via `-Djdk.serialFilter` or a process-wide `ObjectInputFilter`. Pekko supplies no filter of its own, and findings that require the flag to be on are out of model *(maintainer — §14 Q2)*.
4. **Treat `SECURITY`-marked log entries from the Java serializer as attack indicators**, not noise *(documented)*.
5. **Scope the PKI tree to the cluster.** Any certificate it issues is cluster access *(documented)*.
6. **Consider `untrusted-mode = on` and `enable-allow-list = on`** where peers are less than fully trusted — understanding both are hardening, not boundaries.
7. **Never place mutually-distrusting tenants in one cluster** *(documented)*.
8. **Supply passwords by environment substitution, not literals in config files** *(documented)*.
9. **Secure the persistence store.** Pekko trusts journal and snapshot contents on replay and does not validate them as potentially hostile, so access control and integrity for the database or store are the administrator's responsibility *(maintainer — §14 Q5)*.

---

## §11 Known misuse patterns

- **Exposing remoting or cluster ports to the internet**, on the theory that TLS or untrusted mode makes it safe. Both are explicitly documented as insufficient alone.
- **Enabling `allow-java-serialization` for prototyping convenience and shipping it.** The docs anticipate exactly this path.
- **Relying on a service mesh** for cluster authentication *(documented as insufficient)*.
- **Using cluster membership as a tenancy or privilege boundary.**
- **Enabling TLS but leaving `hostname-verification = off`** and assuming per-node identity.
- **Treating actor encapsulation as a confidentiality guarantee** within a single JVM.

---

## §11a Known non-findings (recurring false positives)

Feed this section to scanners and AI triage as a suppression list.

- **"Remote deployment permits arbitrary class instantiation."** Remote deployment is *not* remote code loading — the class must already be on the target's classpath *(P3, documented)*. Absent a way to introduce a class, this is not RCE.
- **"Remoting transport defaults to plaintext."** Correct, and by design under the §4 network-isolation assumption. In-model only if the finding shows harm reachable **pre-association**; a request to change the default is `BY-DESIGN: default-configuration` per §5b.
- **"`PoisonPill` can be sent remotely to shut down a system."** Documented behaviour, gated by `untrusted-mode` *(documented — `remote-security.md`)*.
- **"`hostname-verification` is disabled by default."** Known and documented, with the trade-off spelled out for dynamic-hostname deployments, and warned at runtime under `LogMarker.Security`. A request to change the default is `BY-DESIGN: default-configuration` per §5b.
- **Findings in `*-tests`, `*-testkit`, `bench-jmh`, `docs`** — `OUT-OF-MODEL: unsupported-component` per §3.
- **"SHA1PRNG with `/dev/urandom` reuses the seed."** Documented trade-off, deliberately recommended to avoid blocking *(documented)*.

---

## §12 Conditions that would change this model

- A change to any §5a **default**, particularly `transport`, `untrusted-mode`, or `allow-java-serialization`.
- A new transport, or a new wire protocol at the remoting layer.
- Any per-peer or per-actor authorization mechanism — that would create an intra-cluster trust boundary this model says does not exist.
- Promotion of a §3 module into the supported surface, or **removal of a module from it** — `osgi` is a candidate for removal (§14 Q6), and dropping it would move findings there to `OUT-OF-MODEL: unsupported-component`.
- **A report that cannot be routed to exactly one §13 disposition.** That is evidence of a model gap; the correct response is to revise this document, not to make an ad-hoc call.

---

## §13 Triage dispositions

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Violates a §8 property via an in-scope adversary and input | §6, §7, §8 |
| `VALID-HARDENING` | No §8 property violated, but the API makes a §11 misuse easy enough to warrant hardening. No CVE by default | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires control of an input §6 marks trusted (config, PEM material, journal and snapshot contents, application-supplied data) | §6 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires an associated peer to misbehave, a PKI-tree certificate, or in-JVM code execution | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in a §3 module | §3 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under a non-default §5a setting — most often `allow-java-serialization = on` | §5a |
| `BY-DESIGN: property-disclaimed` | Concerns a §9 property Pekko explicitly does not provide | §9 |
| `BY-DESIGN: default-configuration` | Asks that a §5a default be changed to a more restrictive value. Not a vulnerability; §5b.3 invites the proposal on the development list | §5b |
| `KNOWN-NON-FINDING` | Matches a §11a pattern | §11a |
| `MODEL-GAP` | Routable to none of the above — triggers §12 | §12 |

---

## §14 Open questions for the maintainers

Each states a **proposed answer**. Confirming or correcting is enough; no need to write prose.

Q1 to Q11 are **answered**, retained in place so that cross-references elsewhere in this document continue to resolve. Q12 and Q13 remain open.

**Q1 — The plaintext transport default. ANSWERED *(maintainer)*.**
`transport` ships `tcp`, so a stock cluster has no peer authentication. **Answer:** the default is a compatibility choice under §5b, not a security claim. Reports route as follows:
- "the default should be `tls-tcp`" → `BY-DESIGN: default-configuration`; §5b.3 invites the proposal on the development list.
- "an unauthenticated peer can associate", assuming internet exposure → `BY-DESIGN: property-disclaimed`; §9 disclaims peer authentication by default. (The draft proposed `OUT-OF-MODEL: adversary-not-in-scope`, which does not fit: §13 defines that disposition as requiring an associated peer, a PKI-tree certificate, or in-JVM execution, and §7 lists the unassociated network attacker as **in scope**.)
- harm reachable **pre-association** from an adjacent-network host → `VALID`.

**Q2 — `allow-java-serialization`. ANSWERED *(maintainer)*.** Enabling it is not recommended. **Answer:** any finding that requires `allow-java-serialization = on` to manifest is `OUT-OF-MODEL: non-default-build`, including gadget-chain deserialization. An operator who enables it takes on gadget-chain defence **entirely**: Pekko integrates no serialization filter, so the only lever is the JVM's own — `-Djdk.serialFilter`, or an `ObjectInputFilter` installed process-wide. Maintaining that allow list is the operator's responsibility, not Pekko's.

Two things this does **not** dispose of, per §5b.4:
- A serious defect in Pekko's own serialization implementation is in scope and may warrant a CVE, whatever the flag is set to.
- Java deserialization occurring **despite** `allow-java-serialization = off` violates §8 P1 and is `VALID`, Critical.

**Q3 — `hostname-verification = off`. ANSWERED *(maintainer)*.** **Answer:** a compatibility default under §5b, deliberate rather than legacy, supporting deployments where hostnames are dynamic and not known up front. Pekko additionally warns at runtime under `LogMarker.Security` whenever TLS is enabled and verification is off, on both transports (`artery/tcp/ConfigSSLEngineProvider.scala`, `transport/netty/SSLEngineProvider.scala`), so the operator is told at startup. A report that "any PKI cert authenticates as any node" is `BY-DESIGN: property-disclaimed` per §9; a report that the default should be `on` is `BY-DESIGN: default-configuration`.

**Q4 — Are `untrusted-mode` and `enable-allow-list` security boundaries or hardening? ANSWERED *(maintainer)*.** **Answer:** both are **hardening** features, per the documented *"does not give full protection"*, and shipping them `off` is a §5b compatibility choice — adoption is the operator's decision. A request that either default to `on` is `BY-DESIGN: default-configuration`.

A **bypass of either once enabled** is a separate matter and is **not** covered by that. It violates §8 P5 or P4 respectively, so it is `VALID` at the severity §8 assigns, per §5b.4. The draft proposed `VALID-HARDENING` for this case, which §13 defines as *"No §8 property violated"* — that cannot apply to a bypass of a control §8 credits.

**Q5 — Persistence backend trust. ANSWERED *(maintainer)*.** **Answer:** journal and snapshot stores are **trusted**. Securing the database or persistence store is the responsibility of whoever administers it, and Pekko does not treat persisted values as potentially compromised on replay. A finding whose precondition is an attacker who can write to the journal or snapshot store is `OUT-OF-MODEL: trusted-input`.

This is a trust statement about the **store**, not a licence for the plugin SPI: a defect in Pekko's own replay handling is in scope per §5b.4.

**Q6 — Module in/out split (§2 table). ANSWERED *(maintainer)*, except the residual split noted below.**

- **`osgi` — answered *(maintainer)*.** It stays **in model**: security reports against it are accepted. It is a barely used feature, and the project may in future remove it rather than carry the maintenance overhead — but while it ships it is supported, and a finding in it is not `OUT-OF-MODEL: unsupported-component`. If it is removed, §12 applies.
- **`kubernetes/` — answered *(maintainer)*.** It is test-cluster provisioning tooling — four files (`setup.sh`, `create-cluster-gke.sh`, `test-node-base.yaml`, `.gitignore`), not a build module. It is correctly out of scope with the build and docs sources. Kubernetes *functionality* — discovery, bootstrap, lease — is not in this repository at all: it lives in **Apache Pekko Management**. That is a different repository, not a different security process: reports are made and triaged exactly as they are for core, per §3.
- The rest of the in/out split shown in §2 remains the ASF Security team's proposal *(inferred)*.

**Q7 — The negative claims in §5. ANSWERED.**

- **Environment variables — *(maintainer)*.** Read during configuration startup, never modified. §5 states the mechanism.
- **Sockets — answered.** The original claim was wrong and is corrected in §2, §5 and §6: `pekko-actor` ships `org.apache.pekko.io.Tcp`/`Udp` and `pekko-stream` ships `Tcp`, `TLS` and `FileIO`, all of which bind or open only on an explicit application call.
- **Signal handlers and child processes — *(maintainer)*.** None: no `sun.misc.Signal`, `SignalHandler`, `Runtime.exec` or `ProcessBuilder` in the main sources. One JVM shutdown hook is registered by `CoordinatedShutdown`, disclosed in §5; a shutdown hook is not a signal handler.
- **stdout/stderr — *(maintainer)*.** Logging goes to the configured logger. `StandardOutLogger` prints to stdout during early startup, bounded by `pekko.stdout-loglevel` (default `WARNING`), disclosed in §5.
- **Process-global state — *(maintainer)*.** Not mutated at initialization: no `System.setProperty`, `Locale.setDefault` or `TimeZone.setDefault`.

**Q8 — Resource guarantees. ANSWERED *(maintainer)*.** **Answer:** **super-linear in message size is a bug; constant-factor is not.** Memory or CPU that grows super-linearly in the size of an inbound message is a defect and is `VALID`; a constant-factor overhead proportional to the message is expected and is not.

Two notes on applying this line:
- Artery already bounds message size by configuration — `maximum-frame-size` defaults to 256 KiB and `maximum-large-frame-size` to 2 MiB (`remote/src/main/resources/reference.conf`) — so the input to the rule is bounded on the remoting path.
- The rule is stated in terms of **size**. Exhaustion driven by message **volume** from an associated peer is not covered by it and remains subject to §7, under which such a peer is trusted.

**Q9 — Byzantine generalisation. ANSWERED *(maintainer)*.** **Answer:** it holds for all of them. No subsystem — cluster membership, sharding, singleton or `distributed-data` — claims resilience against a misbehaving member, and Pekko has no guarantee of being able to recognise a compromised node at all. Failure detection is heartbeat-based and answers "is this member responding?", not "is this member honest".

Consequently a finding whose precondition is "a cluster member misbehaves" is `OUT-OF-MODEL: adversary-not-in-scope`, and so is one that assumes Pekko should have detected the compromise.

**Q10 — Coexistence. ANSWERED *(maintainer)*.** Three documents carry security information, each canonical for one thing:

| Document | Canonical for | Reached by |
| --- | --- | --- |
| `docs/src/main/paradox/security/index.md` | Security announcements; the reporting process as published | Readers of the documentation site |
| [`SECURITY.md`](SECURITY.md) | The reporting policy as GitHub presents it | Anyone arriving via the repository |
| `THREAT_MODEL.md` (this document) | **Scope** — what is and is not a vulnerability, and how a report is triaged | Reporters, triagers, scanning tools |

The other two link to this document rather than restating it, so a change in scope is made in one place. Neither attempts to state scope itself.

**Maintenance note.** The reporting wording is currently duplicated between `SECURITY.md` and `security/index.md`, and the two have drifted apart once already. Until one is reduced to a pointer to the other, a change to either must be made to both.

**Q11 — The §3/§7 boundary non-goals. ANSWERED *(maintainer)*.** Confirmed:

- Pekko is **not a sandbox** and **not an authorization framework** (§3).
- Out of the adversary model: an attacker with code execution in the embedding JVM, a hostile classpath, and side-channel observers — Pekko makes no timing or memory-access guarantees of its own. TLS is delegated to the JDK's JSSE, whose own guarantees are unaffected by this disclaimer.
- **In** the adversary model: attacker-influenced *message content* arriving by the ordinary path, since that is how application data travels. This covers defects in **Pekko's own handling** of that content — a serializer, codec or framing defect reachable from a well-formed message is `VALID` per §5b.4. It does not extend to the application's interpretation of the content, which is the application's responsibility per §6.

**Q12 — Clock assumptions.** The failure detector and gossip convergence depend on
local clocks. *Proposed:* Pekko makes no claim against adversarial clock manipulation on
a cluster member — consistent with §7, since such a member is trusted anyway. Confirm?

**Q13 — Classic remoting's place in the supported surface.** Classic remoting is
deprecated but still shipped and still CI-gated (the "Pekko Classic Remoting Tests" job).
It carries its own `untrusted-mode`, `trusted-selection-paths` and netty SSL settings,
now listed in §5a. *Proposed:* deprecation is not desupport, so classic stays **in model**
and its knobs follow the same Q1/Q4 rulings as their artery equivalents. Confirm — or is
classic remoting `OUT-OF-MODEL: unsupported-component` per §3?

---

## §15 Appendix — back-map from existing docs

Proof that nothing the project already asserts has been dropped or weakened.

| Existing statement | Source | Lands in |
| --- | --- | --- |
| Do not expose an `ActorSystem` to an untrusted network; firewall it | `remote-security.md` | §4, §10.1 |
| TLS with mutual authentication if network protection is insufficient | `remote-security.md` | §5a, §8 P6, §10.2 |
| Nodes should only be reachable from the adjacent network | `remote-security.md` | §5 |
| A compromised node's PKI-tree certificate grants cluster access | `remote-security.md` | §5, §7, §9 |
| Mutual authentication on by default | `remote-security.md` | §8 P6 |
| Hostname verification recommended on | `remote-security.md` | §5a, §9, §11 |
| All cluster nodes must be trusted equally | `remote-security.md` | §3, §7, §9 |
| A connected system may send any message to any actor | `remote-security.md` | §4, §7, §9 |
| Untrusted mode does not give full protection | `remote-security.md` | §9, §14 Q4 |
| Untrusted mode blocks system messages, `PossiblyHarmful`, selections | `remote-security.md` | §8 P5 |
| Service mesh is not a replacement | `remote-security.md` | §9 |
| SHA1PRNG / `urandom` trade-off | `remote-security.md` | §5, §11a |
| Java serialization disabled by default; discouraged in production | `serialization.md` | §5a, §8 P1, §10.3 |
| `SECURITY`-marked log entries indicate prevented attacks | `serialization.md` | §8 P2, §10.4 |
| Remote deployment is not remote code loading | `remoting.md` | §8 P3, §11a |
| Remote deployment allow list restricts deployable classes | `remoting.md` | §5a, §8 P4 |
| `serialize-messages` / `serialize-creators` are "only intended for testing" | `actor` `reference.conf` | §5a |
| Report vulnerabilities privately per ASF guidelines; coordinate disclosure with upstream maintainers | `security/index.md` | `SECURITY.md`, §1 |
