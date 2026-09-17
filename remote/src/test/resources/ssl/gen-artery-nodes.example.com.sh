#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# license agreements; and to You under the Apache License, version 2.0:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# This file is part of the Apache Pekko project, which was derived from Akka.

export PW=`cat password`

. gen-functions.sh


function createArteryNodeKeySet() {
  PREFIX=$1
  createExampleRSAKeySet $PREFIX "serverAuth,clientAuth" "DNS:$PREFIX.example.com,DNS:artery-node.example.com"
}

rm -rf artery-nodes

## Then create array few artery-nodes (in ./ssl/artery-nodes folder)
createArteryNodeKeySet "artery-node001"
createArteryNodeKeySet "artery-node002"
createArteryNodeKeySet "artery-node003"

mkdir -p artery-nodes
mv artery-node00* artery-nodes/.
