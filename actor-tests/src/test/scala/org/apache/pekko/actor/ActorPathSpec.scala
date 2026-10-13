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
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

class ActorPathSpec extends AnyWordSpec with Matchers with TableDrivenPropertyChecks {

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

    "accept valid path elements" in {
      val valid = Table(
        "name",
        "a",
        "actor-1",
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
        "-_.*$+:@&=,!~';",
        "a$" + "b",
        "%20",
        "%ff%FF%0a",
        "a%20b",
        "x" * 10000)
      forAll(valid) { name =>
        ActorPath.isValidPathElement(name) should ===(true)
        ActorPath.validatePathElement(name)
      }
    }

    "reject invalid path elements and report the position" in {
      def positionOf(name: String): Int =
        intercept[InvalidActorNameException](ActorPath.validatePathElement(name)).getMessage match {
          case msg if msg.contains("at position: ") =>
            msg.split("at position: ")(1).takeWhile(_.isDigit).toInt
          case msg => fail(s"unexpected message [$msg]")
        }

      val invalid = Table(
        ("name", "position"),
        ("$" + "a", 0), // `$` is reserved for system names at the start
        ("a b", 1),
        ("a/b", 1),
        ("a#b", 1),
        ("a?b", 1),
        ("a%", 1), // `%` is only valid as `%XX`
        ("a%2", 1),
        ("a%2g", 1),
        ("a%g2", 1),
        ("caf\u00e9", 3), // Latin-1 but not ASCII
        ("na\u00efve-actor", 2),
        ("\u4e2d\u6587", 0), // outside Latin-1
        ("actor-\u4e2d", 6),
        ("actor-\ud83d\ude00", 6), // surrogate pair
        ("a\u0000b", 1),
        ("a\u007fb", 1),
        ("x" * 100 + "\u00e9", 100))
      forAll(invalid) { (name, position) =>
        ActorPath.isValidPathElement(name) should ===(false)
        positionOf(name) should ===(position)
      }
    }

    "classify every char like the reference predicate" in {
      // Independent, exhaustive definition of the accepted alphabet, so the table-driven validator
      // is locked to it for every one of the 65,536 chars (including 0x80-0xFF and surrogates).
      def isValidChar(c: Char): Boolean =
        (c >= 'a' && c <= 'z') ||
        (c >= 'A' && c <= 'Z') ||
        (c >= '0' && c <= '9') ||
        "-_.*$+:@&=,!~';".contains(c)
      def isHexChar(c: Char): Boolean =
        (c >= 'a' && c <= 'f') ||
        (c >= 'A' && c <= 'F') ||
        (c >= '0' && c <= '9')

      var c = 0
      while (c <= Char.MaxValue) {
        val ch = c.toChar
        withClue(f"char U+$c%04X: ") {
          ActorPath.isValidPathElement(ch.toString) should ===(isValidChar(ch) && ch != '$')
          ActorPath.isValidPathElement("a" + ch) should ===(isValidChar(ch))
          ActorPath.isValidPathElement("%" + ch + "0") should ===(isHexChar(ch))
          ActorPath.isValidPathElement("%0" + ch) should ===(isHexChar(ch))
        }
        c += 1
      }
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
