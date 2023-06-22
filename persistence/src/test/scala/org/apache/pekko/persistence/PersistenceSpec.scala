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

package org.apache.pekko.persistence

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace

import com.typesafe.config.{ Config, ConfigFactory }
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.{ MatchResult, Matcher }

import org.apache.pekko
import pekko.actor.Props
import pekko.testkit.PekkoSpec

abstract class PersistenceSpec(config: Config)
    extends PekkoSpec(config)
    with BeforeAndAfterEach
    with Cleanup
    with PersistenceMatchers { this: PekkoSpec =>
  private var _name: String = _

  lazy val extension = Persistence(system)
  val counter = new AtomicInteger(0)

  /**
   * Unique name per test.
   */
  def name = _name

  /**
   * Prefix for generating a unique name per test.
   */
  def namePrefix: String = system.name

  /**
   * Creates a persistent actor with current name as constructor argument.
   */
  def namedPersistentActor[T <: NamedPersistentActor: ClassTag] =
    system.actorOf(Props(implicitly[ClassTag[T]].runtimeClass, name))

  /**
   * Creates a persistent actor with current name as constructor argument, plus a custom [[Config]]
   */
  def namedPersistentActorWithProvidedConfig[T <: NamedPersistentActor: ClassTag](providedConfig: Config) =
    system.actorOf(Props(implicitly[ClassTag[T]].runtimeClass, name, providedConfig))

  override protected def beforeEach(): Unit = {
    _name = s"$namePrefix-${counter.incrementAndGet()}"
  }
}

object PersistenceSpec {
  def config(plugin: String, test: String, serialization: String = "off", extraConfig: Option[String] = None) =
    extraConfig
      .map(ConfigFactory.parseString(_))
      .getOrElse(ConfigFactory.empty())
      .withFallback(ConfigFactory.parseString(s"""
      pekko.actor.serialize-creators = $serialization
      pekko.actor.serialize-messages = $serialization
      pekko.actor.no-serialization-verification-needed-class-prefix = []
      # test is using Java serialization and not priority to rewrite
      pekko.actor.allow-java-serialization = on
      pekko.actor.warn-about-java-serializer-usage = off
      pekko.persistence.publish-plugin-commands = on
      pekko.persistence.journal.plugin = "pekko.persistence.journal.$plugin"
      pekko.persistence.journal.leveldb.dir = "target/journal-$test"
      pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
      pekko.persistence.snapshot-store.local.dir = "target/snapshots-$test/"
      pekko.test.single-expect-default = 10s
    """))
}

trait Cleanup { this: PekkoSpec =>
  val storageLocations =
    List("pekko.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))

  override protected def atStartup(): Unit = {
    storageLocations.foreach(FileUtils.deleteDirectory)
  }

  override protected def afterTermination(): Unit = {
    storageLocations.foreach(FileUtils.deleteDirectory)
  }
}

abstract class NamedPersistentActor(name: String) extends PersistentActor {
  override def persistenceId: String = name
}

trait TurnOffRecoverOnStart { this: Eventsourced =>
  override def recovery = Recovery.none
}

class TestException(msg: String) extends Exception(msg) with NoStackTrace

case object GetState

/** Additional ScalaTest matchers useful in persistence tests */
trait PersistenceMatchers {

  /** Use this matcher to verify in-order execution of independent "streams" of events */
  final class IndependentlyOrdered(prefixes: immutable.Seq[String]) extends Matcher[immutable.Seq[Any]] {
    override def apply(_left: immutable.Seq[Any]) = {
      val left = _left.map(_.toString)
      val mapped = left.groupBy(l => prefixes.indexWhere(p => l.startsWith(p))) - -1 // ignore other messages
      val results =
        for {
          (pos, seq) <- mapped
          nrs = seq.map(_.replaceFirst(prefixes(pos), "").toInt)
          sortedNrs = nrs.sorted
          if nrs != sortedNrs
        } yield MatchResult(
          false,
          s"""Messages sequence with prefix ${prefixes(pos)} was not sorted! Was: $seq"""",
          s"""Messages sequence with prefix ${prefixes(pos)} was sorted! Was: $seq"""")

      if (results.forall(_.matches)) MatchResult(true, "", "")
      else results.find(r => !r.matches).get
    }
  }

  def beIndependentlyOrdered(prefixes: String*) = new IndependentlyOrdered(prefixes.toList)
}
