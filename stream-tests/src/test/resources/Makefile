# Documents how truststore and keystore were created
# A similar Makefile is found in remote/src/test/resources/, however here the cert is created without specifying a Subject Alternate Name
all: truststore keystore

truststore: domain.crt
	keytool -importcert -file domain.crt -keystore truststore -deststorepass changeme

keystore: domain.crt domain.key
	openssl pkcs12 -export -inkey domain.key -passin pass:changeme -in domain.crt -out keystore -passout pass:changeme

domain.crt: domain.csr domain.key
	openssl x509 -req -in domain.csr -sha256 -out domain.crt -signkey domain.key

domain.csr:
	openssl req -new -newkey rsa:2048 -keyout domain.key -subj "/C=US/ST=Delaware/O=Apache/CN=pekko-remote" -out domain.csr -passout pass:changeme

.PHONY: clean
clean:
	rm domain.key domain.crt domain.csr keystore truststore 