# Testing Resources

This folder contains a diverse set of keys, certificates and keystores generated using scripts inspired by 
those from https://github.com/playframework/play-samples/tree/2.8.x/play-scala-tls-example/scripts. 

The resources in this folder are build with a few restrictions. See `TlsResourcesSpec.scala` for some 
tests asserting the certificates and keys in this folder fulfill the required restrictions.  

`exampleca-bundle.crt` and `exampleca-rotated-bundle.crt` cover the case of a CA file bundling more
than one certificate. The first bundles two unrelated CAs (`exampleca.crt` and
`pem/selfsigned-certificate.pem`); the second bundles `exampleca.crt` and `exampleca-rotated.crt`,
which share a subject but hold different keys, as a CA rotation that keeps the distinguished name
would leave them. Both are built by `gen-ca-bundles.sh`.
