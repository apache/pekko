---
project.description: Upgrade strategy for downstream libraries
---
# Downstream upgrade strategy

When a new Pekko version is released, downstream projects (such as
[Pekko Management]($pekko.doc.dns$/docs/akka-management/current/),
[Pekko HTTP]($pekko.doc.dns$/docs/akka-http/current/) and
[Pekko gRPC]($pekko.doc.dns$/docs/akka-grpc/current/))
do not need to update immediately: because of our
@ref[binary compatibility](../common/binary-compatibility-rules.md) approach,
applications can take advantage of the latest version of Pekko without having to
wait for intermediate libraries to update.

## Patch versions

When releasing a new patch version of Pekko (e.g. 2.5.22), we typically don't
immediately bump the Pekko version in satellite projects.

The reason for this is this will make it more low-friction for users to update
those satellite projects: say their project is on Pekko 2.5.22 and
Pekko Management 1.0.0, and we release Pekko Management 1.0.1 (still built with
Pekko 2.5.22) and Pekko 2.5.23. They can safely update to Pekko Management 1.0.1
without also updating to Pekko 2.5.23, or update to Pekko 2.5.23 without updating
to Pekko Management 1.0.1.

When there is reason for a satellite project to upgrade the Pekko patch
version, they are free to do so at any time.

## Minor versions

When releasing a new minor version of Pekko (e.g. 2.6.0), satellite projects are
also usually not updated immediately, but as needed.

When a satellite project does update to a new minor version of Pekko, it will
also increase its own minor version. The previous stable branch will enter the
usual end-of-support lifecycle and only important
bugfixes will be backported to the previous version and released.
