# Security Announcements

@@toc { depth=2 }

@@@ index

* [dependency-check-report](dependency-check-report.md)

@@@

## Receiving Security Advisories

The best way to receive any and all security announcements is to subscribe to the [Apache Announce Mailing List](https://lists.apache.org/list.html?announce@apache.org).

This mailing list has a reasonable level of traffic, and receives notifications only after security reports have been managed by the core Apache teams and fixes are publicly available.

This mailing list also has announcements of releases for Apache projects.

## Reporting Vulnerabilities

We strongly encourage people to report such problems to our private security mailing list first, before disclosing them in a public forum.

Please follow the [guidelines](https://www.apache.org/security/) laid down by the Apache Security team.

Ideally, any issues affecting Apache Pekko and Akka should be reported to Apache team first. We will share the
report with the Lightbend Akka team.

## Dependency check scanner

This project uses [sbt-dependency-check](https://github.com/albuch/sbt-dependency-check) in order to scan the
projects dependencies against [OWASP](https://owasp.org/) to create a @ref:[dependency-check-report](dependency-check-report.md)
of any potential security issues.

If you want to suppress the checking of some dependencies then there is a [supression](github:dependency-check/suppression.xml)
file. The format of this file is documented [here](https://jeremylong.github.io/DependencyCheck/general/suppression.html).

## Security Related Documentation

 * [Akka security fixes]($pekko.doc.dns$/docs/pekko/current/security/index.html)
 * @ref:[Java Serialization](../serialization.md#java-serialization)
 * @ref:[Remote deployment allow list](../remoting.md#remote-deployment-allow-list)
 * @ref:[Remote Security](../remoting-artery.md#remote-security)
