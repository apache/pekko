/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.serialization

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.Address
import pekko.actor.Deploy
import pekko.actor.Props
import pekko.actor.SupervisorStrategy
import pekko.remote.DaemonMsgCreate
import pekko.remote.RemoteScope
import pekko.remote.serialization.DaemonMsgCreateSerializerAllowJavaSerializationSpec.ActorWithDummyParameter
import pekko.remote.serialization.DaemonMsgCreateSerializerAllowJavaSerializationSpec.MyActorWithParam
import pekko.routing.FromConfig
import pekko.routing.RoundRobinPool
import pekko.serialization.Serialization
import pekko.serialization.SerializationExtension
import pekko.testkit.PekkoSpec
import pekko.testkit.JavaSerializable
import pekko.util.unused

object DaemonMsgCreateSerializerAllowJavaSerializationSpec {

  trait EmptyActor extends Actor {
    def receive = Actor.emptyBehavior
  }
  class MyActor extends EmptyActor
  class MyActorWithParam(@unused ignore: String) extends EmptyActor
  class MyActorWithFunParam(@unused fun: Function1[Int, Int]) extends EmptyActor
  class ActorWithDummyParameter(@unused javaSerialized: DummyParameter, @unused protoSerialized: ActorRef)
      extends EmptyActor
}

case class DummyParameter(val inner: String) extends JavaSerializable

private[pekko] trait SerializationVerification { self: PekkoSpec =>

  def ser: Serialization

  def verifySerialization(msg: DaemonMsgCreate): Unit = {
    assertDaemonMsgCreate(
      msg,
      ser.deserialize(ser.serialize(msg).get, classOf[DaemonMsgCreate]).get.asInstanceOf[DaemonMsgCreate])
  }

  def assertDaemonMsgCreate(expected: DaemonMsgCreate, got: DaemonMsgCreate): Unit = {
    // can't compare props.creator when function
    got.props.clazz should ===(expected.props.clazz)
    got.props.args.length should ===(expected.props.args.length)
    got.props.args.zip(expected.props.args).foreach {
      case (g, e) =>
        if (e.isInstanceOf[Function0[_]]) ()
        else if (e.isInstanceOf[Function1[_, _]]) ()
        else g should ===(e)
    }
    got.props.deploy should ===(expected.props.deploy)
    got.deploy should ===(expected.deploy)
    got.path should ===(expected.path)
    got.supervisor should ===(expected.supervisor)
  }
}

class DaemonMsgCreateSerializerAllowJavaSerializationSpec
    extends PekkoSpec("""
  # test is verifying Java serialization  
  pekko.actor.allow-java-serialization = on
  pekko.actor.warn-about-java-serializer-usage = off
  """)
    with SerializationVerification {

  import DaemonMsgCreateSerializerAllowJavaSerializationSpec._
  val ser = SerializationExtension(system)
  val supervisor = system.actorOf(Props[MyActor](), "supervisor")

  "Serialization" must {

    "resolve DaemonMsgCreateSerializer" in {
      ser.serializerFor(classOf[DaemonMsgCreate]).getClass should ===(classOf[DaemonMsgCreateSerializer])
    }

    "serialize and de-serialize DaemonMsgCreate with function creator" in {
      verifySerialization {
        DaemonMsgCreate(props = Props(new MyActor), deploy = Deploy(), path = "foo", supervisor = supervisor)
      }
    }

    "serialize and de-serialize DaemonMsgCreate with FromClassCreator, with function parameters for Props" in {
      verifySerialization {
        DaemonMsgCreate(
          props = Props(classOf[MyActorWithFunParam], (i: Int) => i + 1),
          deploy = Deploy(),
          path = "foo",
          supervisor = supervisor)
      }
    }

    "serialize and de-serialize DaemonMsgCreate with Deploy and RouterConfig" in {
      verifySerialization {
        // Duration.Inf doesn't equal Duration.Inf, so we use another for test
        // we don't serialize the supervisor strategy, but always fallback to default
        val supervisorStrategy = SupervisorStrategy.defaultStrategy
        val deploy1 = Deploy(
          path = "path1",
          config = ConfigFactory.parseString("a=1"),
          routerConfig = RoundRobinPool(nrOfInstances = 5, supervisorStrategy = supervisorStrategy),
          scope = RemoteScope(Address("pekko", "Test", "host1", 1921)),
          dispatcher = "mydispatcher")
        val deploy2 = Deploy(
          path = "path2",
          config = ConfigFactory.parseString("a=2"),
          routerConfig = FromConfig,
          scope = RemoteScope(Address("pekko", "Test", "host2", 1922)),
          dispatcher = Deploy.NoDispatcherGiven)
        DaemonMsgCreate(
          props = Props[MyActor]().withDispatcher("my-disp").withDeploy(deploy1),
          deploy = deploy2,
          path = "foo",
          supervisor = supervisor)
      }
    }

  }
}

class DaemonMsgCreateSerializerNoJavaSerializationSpec extends PekkoSpec("""
   pekko.actor.allow-java-serialization=off
  """) with SerializationVerification {

  import DaemonMsgCreateSerializerAllowJavaSerializationSpec.MyActor

  val supervisor = system.actorOf(Props[MyActor](), "supervisor")
  val ser = SerializationExtension(system)

  "serialize and de-serialize DaemonMsgCreate with FromClassCreator" in {
    verifySerialization {
      DaemonMsgCreate(props = Props[MyActor](), deploy = Deploy(), path = "foo", supervisor = supervisor)
    }
  }

  "serialize and de-serialize DaemonMsgCreate with FromClassCreator, with null parameters for Props" in {
    verifySerialization {
      DaemonMsgCreate(
        props = Props(classOf[MyActorWithParam], null),
        deploy = Deploy(),
        path = "foo",
        supervisor = supervisor)
    }
  }

  "serialize and de-serialize DaemonMsgCreate with Deploy and RouterConfig" in {
    verifySerialization {
      val deploy1 = Deploy(
        path = "path1",
        config = ConfigFactory.parseString("a=1"),
        // a whole can of worms: routerConfig = RoundRobinPool(nrOfInstances = 5, supervisorStrategy = supervisorStrategy),
        scope = RemoteScope(Address("pekko", "Test", "host1", 1921)),
        dispatcher = "mydispatcher")
      val deploy2 = Deploy(
        path = "path2",
        config = ConfigFactory.parseString("a=2"),
        routerConfig = FromConfig,
        scope = RemoteScope(Address("pekko", "Test", "host2", 1922)),
        dispatcher = Deploy.NoDispatcherGiven)
      DaemonMsgCreate(
        props = Props[MyActor]().withDispatcher("my-disp").withDeploy(deploy1),
        deploy = deploy2,
        path = "foo",
        supervisor = supervisor)
    }
  }

  "allows for mixing serializers with and without manifests for props parameters" in {
    verifySerialization {
      DaemonMsgCreate(
        // parameters should trigger JavaSerializer for the first one and additional protobuf for the second (?)
        props = Props(classOf[ActorWithDummyParameter], new DummyParameter("dummy"), system.deadLetters),
        deploy = Deploy(),
        path = "foo",
        supervisor = supervisor)
    }
  }

}
