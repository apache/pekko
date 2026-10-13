#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# license agreements; and to You under the Apache License, version 2.0:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# This file is part of the Apache Pekko project, which was derived from Akka.

export PW=`cat password`

. gen-functions.sh

rm *.crt
rm *.p12
rm *.pem

./genca.sh

## some server certificates
createExampleECKeySet "one" "serverAuth" "DNS:one.example.com,DNS:example.com"
createExampleECKeySet "two" "serverAuth" "DNS:two.example.com,DNS:example.com"
createExampleECKeySet "island" "serverAuth" "DNS:island.example.com"

## a client certificate
createExampleECKeySet "client" "clientAuth" "DNS:client.example.com,DNS:example.com"

## node.example.com is part of the example.com dataset (in ./ssl/ folder) but not the artery-nodes
createExampleRSAKeySet "node" "serverAuth,clientAuth" "DNS:node.example.com,DNS:example.com"
createExampleRSAKeySet "rsa-client" "clientAuth" "DNS:rsa-client.example.com,DNS:example.com"

## a certificate valid for both server and client (peer-to-peer)
## with RSA keys
./gen-artery-nodes.example.com.sh

## the multi-certificate `ca-cert-file` samples, built on top of the certificates above
./gen-ca-bundles.sh
