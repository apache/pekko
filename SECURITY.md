# Security Policy

## Reporting a Vulnerability

**Do not report security vulnerabilities through public GitHub issues, pull
requests, or the mailing lists.**

Report them privately to the Apache Security team:

    security@apache.org

Apache Pekko does not operate a separate project security list; reports go to
the foundation-wide address above, which routes to the Pekko PMC.

Please follow the [guidelines laid down by the Apache Security
team](https://www.apache.org/security/). The Pekko PMC will coordinate
responsible disclosure with affected upstream maintainers where needed.

Ideally, any issues affecting Apache Pekko and its predecessor project should
be reported to the Apache Pekko team first. The Pekko PMC will coordinate
responsible disclosure with the affected upstream maintainers when needed, so a
cross-project issue does not need to be filed twice.

To receive security announcements, subscribe to the [Apache Announce Mailing
List](https://lists.apache.org/list.html?announce@apache.org).

## Security Model

Before reporting, please read Apache Pekko's threat model:

[THREAT_MODEL.md](THREAT_MODEL.md)

It states what Pekko treats as a vulnerability and what it does not — in
particular its assumptions about the network remoting runs on, which
configuration defaults change the security envelope, and which properties
Pekko explicitly leaves to the operator. Reports that fall outside the model
will be closed citing the relevant section, so checking first will save you
time.

Two points catch most reporters:

- **Pekko remoting assumes a trusted network.** An `ActorSystem` is not
  designed to be exposed to an untrusted network; it is expected to be
  protected by network security such as a firewall, and optionally TLS with
  mutual authentication. See §4 and §7.
- **All nodes in a Pekko cluster are trusted equally.** There is no
  Byzantine-peer model — a finding whose precondition is "a cluster member
  misbehaves" is out of model. See §7.

## Further Security Documentation

- [Apache Pekko security announcements](https://pekko.apache.org/docs/pekko/current/security/)
- [Remote Security](https://pekko.apache.org/docs/pekko/current/remoting-artery.html#remote-security)
- [Java Serialization](https://pekko.apache.org/docs/pekko/current/serialization.html#java-serialization)
- [Remote deployment allow list](https://pekko.apache.org/docs/pekko/current/remoting.html#remote-deployment-allow-list)
