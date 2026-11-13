#!/bin/bash

set -ex
set -o pipefail

# check config
# BUILD_COMMIT
# NEXUS_USER
# NEXUS_PW
# GPG_SIGNING_KEY_ID

# checkout
git checkout $BUILD_COMMIT

# generate source artifacts
sbt sourceDistGenerate

# sign source artifacts
find target/dist -regex '.*\(tgz\|zip\)' | xargs -I{} sh -c "sha512sum {} > {}.sha512"
find target/dist -regex '.*\(tgz\|zip\)' | xargs -n1 gpg --sign --armor --default-key $GPG_SIGNING_KEY_ID --detach-sig

# upload source artifacts
# TODO

# publish to Apache Nexus Staging
echo "pgpSigningKey := Some(\"$GPG_SIGNING_KEY_ID\")" > pgp-signing-key.sbt
sbt +publishSigned

# generate and upload docs
# TODO