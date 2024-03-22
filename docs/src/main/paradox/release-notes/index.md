# Release Notes

## 1.0.3-M1
This is milestone release and is aimed at testing some new support for users who want to add Pekko nodes to an existing Akka cluster. This support is experimental. This release should not be used in production.

### Bug Fixes

* Fix StackOverflowError in ByteStringBuilder.addAll ([PR903](https://github.com/apache/pekko/pull/903))
* Fix issue with possible int overflow in snapshot interval calculation ([PR1088](https://github.com/apache/pekko/pull/1088))

### Other Changes

* Change the manager name to use `pekko` in the name ([PR587](https://github.com/apache/pekko/pull/587))
* Support interacting with Akka Remote and Cluster nodes ([PR765](https://github.com/apache/pekko/pull/765), [PR1112](https://github.com/apache/pekko/pull/1112))
    * See the [wiki notes](https://cwiki.apache.org/confluence/display/PEKKO/Pekko+Akka+Compatibility) about uptaking this 
* Change noisy logging in DNS handler ([PR835](https://github.com/apache/pekko/pull/835))
* Support reading akka-persistence snapshots ([PR837](https://github.com/apache/pekko/pull/837), [PR841](https://github.com/apache/pekko/pull/841))
* Fix deprecation version on GraphApply ([PR877](https://github.com/apache/pekko/pull/877))
* Reject zero and negative periodic tasks schedule ([PR887](https://github.com/apache/pekko/pull/887))

## 1.0.2
A minor bug fix release.

### Bug Fixes

* Do not render env variables when logging configs. This relates to the optional config `pekko.log-config-on-start`. We do not recommend logging configs in production environments. ([PR771](https://github.com/apache/pekko/pull/771))
* Allow `pekko-actor-testkit-typed` to work with slf4j-api v2.0.x. ([PR784](https://github.com/apache/pekko/pull/784))

### Additional Changes

* Deprecate statefulMapConcat ([#601](https://github.com/apache/pekko/issues/601))
* Add section on using Scala 3 Union types to eliminate msg adapters ([PR741](https://github.com/apache/pekko/pull/741))

## 1.0.1
A minor bug fix release. The class renaming described below (`#491`) is not expected to affect anyone
upgrading from version 1.0.0 but it is strongly recommended that Apache Pekko users switch to the 1.0.1
release when it becomes available.

### Bug Fixes

* Issue with class name of package private object `PekkoPduProtobufCodec$` ([#491](https://github.com/apache/pekko/issues/491))

## 1.0.0
Apache Pekko 1.0.0 is based on Akka 2.6.20. Pekko came about as a result of Lightbend's decision to make future
Akka releases under a [Business Software License](https://www.lightbend.com/blog/why-we-are-changing-the-license-for-akka),
a license that is not compatible with Open Source usage.

Apache Pekko has changed the package names, among other changes. Config names have changed to use `pekko` instead
of `akka` in their names. The default ports for pekko-remote have changed to avoid clashing with the akka-remote
defaults. Users switching from Akka to Pekko should read our @ref:[Migration Guide](../project/migration-guides.md).

Generally, we have tried to make it as easy as possible to switch existing Akka 2.6 based projects over to using
Pekko 1.0.

We have gone through the code base and have tried to properly acknowledge all third party source code in the
Apache Pekko code base. If anyone believes that there are any instances of third party source code that is not
properly acknowledged, please get in touch.

### Bug Fixes
We haven't had to fix many bugs that were in Akka 2.6.20.

* Optimized JsonFraming breaks existing functionality in v2.6.20 ([PR44](https://github.com/apache/pekko/pull/44))
* Use random IDs in Async DNS Resolver. This change was made due to [CVE-2023-31442](https://akka.io/security/akka-async-dns-2023-31442.html) in Akka. ([#384](https://github.com/apache/pekko/issues/384))
* Include critical TLS fix from Akka 2.6.21 ([#442](https://github.com/apache/pekko/issues/442))

### Dependency Upgrades
We have tried to limit the changes to third party dependencies that are used in Pekko 1.0.0. These are some exceptions:

* Scala 3.3.0 is the minimum Scala 3 version supported. Scala 2.12 and 2.13 are still supported.
* Jackson 2.14.3 ([#7](https://github.com/apache/pekko/issues/7))
* protobuf-java 3.16.3 ([PR390](https://github.com/apache/pekko/pull/390))
* reactive-streams 1.0.4
* scala-java8-compat 1.0.2 - it is now only a dependency if you are using Scala 2.12. It is no longer used by Pekko when Scala 2.13 or 3 are used.
* ssl-config 0.6.1 ([PR394](https://github.com/apache/pekko/pull/394))
* scalatest 3.2.14. Pekko users who have existing tests based on Akka Testkit may need to migrate their tests due to the scalatest upgrade. The [scalatest 3.2 release notes](https://www.scalatest.org/release_notes/3.2.0) have a detailed description of the changes needed.

### Known Issues
* The Pekko tests run well in our GitHub Actions continuous integration setup but can be hard to get running locally. We are adding improvements and they can be tracked among our GitHub issues using the [make-tests-easier-to-run](https://github.com/apache/pekko/issues?q=label%3Amake-tests-easier-to-run+) label).
