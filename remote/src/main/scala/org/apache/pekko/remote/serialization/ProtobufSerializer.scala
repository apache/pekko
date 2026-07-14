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

package org.apache.pekko.remote.serialization

import java.lang.invoke.{ MethodHandle, MethodHandles, MethodType }
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap

import scala.util.control.NonFatal

import org.apache.pekko

import pekko.actor.{ ActorRef, ExtendedActorSystem }
import pekko.event.LogMarker
import pekko.event.Logging
import pekko.remote.WireFormats.ActorRefData
import pekko.serialization.{ BaseSerializer, Serialization }
import pekko.serialization.SerializationExtension

object ProtobufSerializer {
  private val lookup = MethodHandles.lookup()
  private val publicLookup = MethodHandles.publicLookup()
  private val toByteArrayMethodType = MethodType.methodType(classOf[Array[Byte]])
  private val parsingInvokerType = MethodType.methodType(classOf[AnyRef], classOf[Array[Byte]])
  private val toByteArrayInvokerType = MethodType.methodType(classOf[AnyRef], classOf[AnyRef])
  private val invocationTargetExceptionConstructor =
    lookup.findConstructor(
      classOf[InvocationTargetException],
      MethodType.methodType(Void.TYPE, classOf[Throwable]))

  private def parsingHandle(clazz: Class[?]): MethodHandle = {
    val methodType = MethodType.methodType(clazz, classOf[Array[Byte]])
    val handle =
      try lookup.findStatic(clazz, "parseFrom", methodType)
      catch {
        case _: IllegalAccessException => publicLookup.findStatic(clazz, "parseFrom", methodType)
      }
    val invoker = wrapTargetException(handle).asType(parsingInvokerType)
    ensureInitialized(clazz)
    invoker
  }

  private def toByteArrayHandle(clazz: Class[?]): MethodHandle = {
    val handle =
      try lookup.findVirtual(clazz, "toByteArray", toByteArrayMethodType)
      catch {
        case _: IllegalAccessException => publicLookup.findVirtual(clazz, "toByteArray", toByteArrayMethodType)
      }
    val invoker = wrapTargetException(handle).asType(toByteArrayInvokerType)
    ensureInitialized(clazz)
    invoker
  }

  private def wrapTargetException(target: MethodHandle): MethodHandle = {
    val exceptionThrower =
      MethodHandles.throwException(target.`type`().returnType(), classOf[InvocationTargetException])
    val handler = MethodHandles.filterArguments(exceptionThrower, 0, invocationTargetExceptionConstructor)
    val handlerWithArguments = MethodHandles.dropArguments(handler, 1, target.`type`().parameterList())
    MethodHandles.catchException(target, classOf[Throwable], handlerWithArguments)
  }

  private def ensureInitialized(clazz: Class[?]): Unit = {
    try lookup.ensureInitialized(clazz)
    catch {
      case _: IllegalAccessException =>
        publicLookup.ensureInitialized(clazz)
    }
  }

  /**
   * Helper to serialize a [[pekko.actor.ActorRef]] to Pekko's
   * protobuf representation.
   */
  def serializeActorRef(ref: ActorRef): ActorRefData = {
    ActorRefData.newBuilder.setPath(Serialization.serializedActorPath(ref)).build
  }

  /**
   * Helper to materialize (lookup) a [[pekko.actor.ActorRef]]
   * from Pekko's protobuf representation in the supplied
   * [[pekko.actor.ActorSystem]].
   */
  def deserializeActorRef(system: ExtendedActorSystem, refProtocol: ActorRefData): ActorRef =
    system.provider.resolveActorRef(refProtocol.getPath)
}

/**
 * This Serializer serializes `org.apache.pekko.protobufv3.internal.Message`
 * It is using reflection to find the `parseFrom` and `toByteArray` methods to avoid
 * dependency to `com.google.protobuf`.
 *
 * This is related to the config property `pekko.serialization.protobuf.allowed-classes`.
 */
class ProtobufSerializer(val system: ExtendedActorSystem) extends BaseSerializer {

  private val parsingMethodBindings = new ConcurrentHashMap[Class[?], MethodHandle]
  private val toByteArrayMethodBindings = new ConcurrentHashMap[Class[?], MethodHandle]

  private val allowedClassNames: Set[String] = {
    import scala.jdk.CollectionConverters._
    system.settings.config.getStringList("pekko.serialization.protobuf.allowed-classes").asScala.toSet
  }

  // This must lazy otherwise it will deadlock the ActorSystem creation
  private lazy val serialization = SerializationExtension(system)

  private val log = Logging.withMarker(system, classOf[ProtobufSerializer])

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[?]]): AnyRef = {
    manifest match {
      case Some(clazz) =>
        def parsingMethod(): MethodHandle = {
          val cached = parsingMethodBindings.get(clazz)
          if (cached ne null) cached
          else {
            checkAllowedClass(clazz)
            val handle = ProtobufSerializer.parsingHandle(clazz)
            val existing = parsingMethodBindings.putIfAbsent(clazz, handle)
            if (existing eq null) handle else existing
          }
        }
        val result: AnyRef = parsingMethod().invokeExact(bytes)
        result

      case None =>
        throw new IllegalArgumentException("Need a protobuf message class to be able to serialize bytes using protobuf")
    }
  }

  override def toBinary(obj: AnyRef): Array[Byte] = {
    val clazz = obj.getClass
    def toByteArrayMethod(): MethodHandle = {
      val cached = toByteArrayMethodBindings.get(clazz)
      if (cached ne null) cached
      else {
        val handle = ProtobufSerializer.toByteArrayHandle(clazz)
        val existing = toByteArrayMethodBindings.putIfAbsent(clazz, handle)
        if (existing eq null) handle else existing
      }
    }
    val result: AnyRef = toByteArrayMethod().invokeExact(obj)
    result.asInstanceOf[Array[Byte]]
  }

  private def checkAllowedClass(clazz: Class[?]): Unit = {
    if (!isInAllowList(clazz)) {
      val warnMsg = s"Can't deserialize object of type [${clazz.getName}] in [${getClass.getName}]. " +
        "Only classes that are on the allow list are allowed for security reasons. " +
        "Configure allowed classes with pekko.actor.serialization-bindings or " +
        "pekko.serialization.protobuf.allowed-classes"
      log.warning(LogMarker.Security, warnMsg)
      throw new IllegalArgumentException(warnMsg)
    }
  }

  /**
   * Using the `serialization-bindings` as source for the allowed classes.
   * Note that the intended usage of serialization-bindings is for lookup of
   * serializer when serializing (`toBinary`). For deserialization (`fromBinary`) the serializer-id is
   * used for selecting serializer.
   * Here we use `serialization-bindings` also when deserializing (fromBinary)
   * to check that the manifest class is of a known (registered) type.
   *
   * If an old class is removed from `serialization-bindings` when it's not used for serialization
   * but still used for deserialization (e.g. rolling update with serialization changes) it can
   * be allowed by specifying in `pekko.serialization.protobuf.allowed-classes`.
   *
   * That is also possible when changing a binding from a ProtobufSerializer to another serializer (e.g. Jackson)
   * and still bind with the same class (interface).
   */
  private def isInAllowList(clazz: Class[?]): Boolean = {
    isBoundToProtobufSerializer(clazz) || isInAllowListClassName(clazz)
  }

  private def isBoundToProtobufSerializer(clazz: Class[?]): Boolean = {
    try {
      val boundSerializer = serialization.serializerFor(clazz)
      boundSerializer.isInstanceOf[ProtobufSerializer]
    } catch {
      case NonFatal(_) => false // not bound
    }
  }

  private def isInAllowListClassName(clazz: Class[?]): Boolean = {
    allowedClassNames(clazz.getName) ||
    allowedClassNames(clazz.getSuperclass.getName) ||
    clazz.getInterfaces.exists(c => allowedClassNames(c.getName))
  }
}
