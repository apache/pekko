/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import java.net.MalformedURLException

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ActorPathSpec extends AnyWordSpec with Matchers {

  "An ActorPath" must {

    "support parsing its String rep" in {
      val path = RootActorPath(Address("pekko", "mysys")) / "user"
      ActorPath.fromString(path.toString) should ===(path)
    }

    "support parsing remote paths" in {
      val remote = "pekko://my_sys@host:1234/some/ref"
      ActorPath.fromString(remote).toString should ===(remote)
    }

    "support parsing remote akka paths" in {
      val remote = "akka://my_sys@host:1234/some/ref"
      ActorPath.fromString(remote).toString should ===(remote)
    }

    "support parsing AddressFromURIString" in {
      val remote = "pekko://my_sys@host:1234"
      AddressFromURIString(remote) should ===(Address("pekko", "my_sys", Some("host"), Some(1234)))
    }

    "support parsing akka AddressFromURIString" in {
      val remote = "akka://my_sys@host:1234"
      AddressFromURIString(remote) should ===(Address("akka", "my_sys", Some("host"), Some(1234)))
    }

    "throw exception upon malformed paths" in {
      intercept[MalformedURLException] { ActorPath.fromString("") }
      intercept[MalformedURLException] { ActorPath.fromString("://hallo") }
      intercept[MalformedURLException] { ActorPath.fromString("s://dd@:12") }
      intercept[MalformedURLException] { ActorPath.fromString("s://dd@h:hd") }
      intercept[MalformedURLException] { ActorPath.fromString("a://l:1/b") }
    }

    "create correct toString" in {
      val a = Address("pekko", "mysys")
      RootActorPath(a).toString should ===("pekko://mysys/")
      (RootActorPath(a) / "user").toString should ===("pekko://mysys/user")
      (RootActorPath(a) / "user" / "foo").toString should ===("pekko://mysys/user/foo")
      (RootActorPath(a) / "user" / "foo" / "bar").toString should ===("pekko://mysys/user/foo/bar")
    }

    "have correct path elements" in {
      (RootActorPath(Address("pekko", "mysys")) / "user" / "foo" / "bar").elements.toSeq should ===(
        Seq("user", "foo", "bar"))
    }

    "create correct toStringWithoutAddress" in {
      val a = Address("pekko", "mysys")
      RootActorPath(a).toStringWithoutAddress should ===("/")
      (RootActorPath(a) / "user").toStringWithoutAddress should ===("/user")
      (RootActorPath(a) / "user" / "foo").toStringWithoutAddress should ===("/user/foo")
      (RootActorPath(a) / "user" / "foo" / "bar").toStringWithoutAddress should ===("/user/foo/bar")
    }

    "validate path elements" in {
      intercept[InvalidActorNameException](ActorPath.validatePathElement("")).getMessage should include(
        "must not be empty")
    }

    "create correct toStringWithAddress" in {
      val local = Address("pekko", "mysys")
      val a = local.copy(host = Some("aaa"), port = Some(7355))
      val b = a.copy(host = Some("bb"))
      val c = a.copy(host = Some("cccc"))
      val root = RootActorPath(local)
      root.toStringWithAddress(a) should ===("pekko://mysys@aaa:7355/")
      (root / "user").toStringWithAddress(a) should ===("pekko://mysys@aaa:7355/user")
      (root / "user" / "foo").toStringWithAddress(a) should ===("pekko://mysys@aaa:7355/user/foo")

      //      root.toStringWithAddress(b) should ===("pekko://mysys@bb:7355/")
      (root / "user").toStringWithAddress(b) should ===("pekko://mysys@bb:7355/user")
      (root / "user" / "foo").toStringWithAddress(b) should ===("pekko://mysys@bb:7355/user/foo")

      root.toStringWithAddress(c) should ===("pekko://mysys@cccc:7355/")
      (root / "user").toStringWithAddress(c) should ===("pekko://mysys@cccc:7355/user")
      (root / "user" / "foo").toStringWithAddress(c) should ===("pekko://mysys@cccc:7355/user/foo")

      val rootA = RootActorPath(a)
      rootA.toStringWithAddress(b) should ===("pekko://mysys@aaa:7355/")
      (rootA / "user").toStringWithAddress(b) should ===("pekko://mysys@aaa:7355/user")
      (rootA / "user" / "foo").toStringWithAddress(b) should ===("pekko://mysys@aaa:7355/user/foo")
    }

    "not allow path separators in RootActorPath's name" in {
      intercept[IllegalArgumentException] {
        RootActorPath(Address("pekko", "mysys"), "/user/boom/*") // illegally pass in a path where name is expected
      }.getMessage should include("is a path separator")

      // check that creating such path still works
      ActorPath.fromString("pekko://mysys/user/boom/*")
    }

    "detect valid and invalid chars in host names when not using AddressFromURIString, e.g. docker host given name" in {
      Seq(
        Address("pekko", "sys", "valid", 0),
        Address("pekko", "sys", "is_valid.org", 0),
        Address("pekko", "sys", "fu.is_valid.org", 0)).forall(_.hasInvalidHostCharacters) shouldBe false

      Seq(Address("pekko", "sys", "in_valid", 0), Address("pekko", "sys", "invalid._org", 0))
        .forall(_.hasInvalidHostCharacters) shouldBe true

      intercept[MalformedURLException](AddressFromURIString("pekko://sys@in_valid:5001"))
    }

    "not fail fast if the check is called on valid chars in host names" in {
      Seq(
        Address("pekko", "sys", "localhost", 0),
        Address("pekko", "sys", "is_valid.org", 0),
        Address("pekko", "sys", "fu.is_valid.org", 0)).foreach(_.checkHostCharacters())
    }

    "fail fast if the check is called when invalid chars are in host names" in {
      Seq(
        Address("pekko", "sys", "localhost", 0),
        Address("pekko", "sys", "is_valid.org", 0),
        Address("pekko", "sys", "fu.is_valid.org", 0)).foreach(_.checkHostCharacters())

      intercept[IllegalArgumentException](Address("pekko", "sys", "in_valid", 0).checkHostCharacters())
      intercept[IllegalArgumentException](Address("pekko", "sys", "invalid._org", 0).checkHostCharacters())
    }
  }
}
