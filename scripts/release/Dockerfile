FROM ubuntu:22.04

ENV RUN DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y curl gnupg2 openjdk-8-jdk-headless openjdk-11-jdk-headless sudo
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list &&\
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list &&\
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add

RUN apt-get update && apt-get install -y sbt git

RUN useradd -U -u 1000 user
RUN mkdir /home/user && chown user: /home/user
RUN mkdir /build && chown user: /build

USER user:user
WORKDIR /build

RUN mkdir -p /tmp/sbt-init/project && echo "sbt.version=1.8.2" > /tmp/sbt-init/project/build.properties && cd /tmp/sbt-init && sbt exit

ENV BUILD_COMMIT=origin/main

RUN git clone --depth 100 https://github.com/apache/incubator-pekko
WORKDIR /build/incubator-pekko
RUN git checkout $BUILD_COMMIT
RUN sbt +update # prime sbt
