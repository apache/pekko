/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.serialization

import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.event.Logging
import pekko.remote.ContainerFormats
import pekko.serialization.DisabledJavaSerializer
import pekko.serialization.SerializationExtension

/**
 * INTERNAL API
 */
private[pekko] class ThrowableSupport(system: ExtendedActorSystem) {

  private lazy val serialization = SerializationExtension(system)
  private val payloadSupport = new WrappedPayloadSupport(system)
  private val log = Logging(system, classOf[ThrowableSupport])

  def serializeThrowable(t: Throwable): Array[Byte] = {
    toProtobufThrowable(t).build().toByteArray
  }

  def toProtobufThrowable(t: Throwable): ContainerFormats.Throwable.Builder = {
    val b = ContainerFormats.Throwable.newBuilder().setClassName(t.getClass.getName)
    if (t.getMessage ne null)
      b.setMessage(t.getMessage)
    if (t.getCause ne null)
      b.setCause(payloadSupport.payloadBuilder(t.getCause))
    val stackTrace = t.getStackTrace
    if (stackTrace ne null) {
      var i = 0
      while (i < stackTrace.length) {
        b.addStackTrace(stackTraceElementBuilder(stackTrace(i)))
        i += 1
      }
    }

    b
  }

  private def stackTraceElementBuilder(elem: StackTraceElement): ContainerFormats.StackTraceElement.Builder = {
    val builder = ContainerFormats.StackTraceElement
      .newBuilder()
      .setClassName(elem.getClassName)
      .setMethodName(elem.getMethodName)
      .setLineNumber(elem.getLineNumber)
    val fileName = elem.getFileName
    if (fileName ne null) builder.setFileName(fileName) else builder.setFileName("")
  }

  def deserializeThrowable(bytes: Array[Byte]): Throwable = {
    fromProtobufThrowable(ContainerFormats.Throwable.parseFrom(bytes))
  }

  def fromProtobufThrowable(protoT: ContainerFormats.Throwable): Throwable = {
    val className = protoT.getClassName
    try {
      // Important security note: before creating an instance of from the class name we
      // check that the class is a Throwable and that it has a configured serializer.
      val clazz = system.dynamicAccess.getClassFor[Throwable](className).get
      // this will throw NotSerializableException if no serializer configured, caught below
      val serializer = serialization.serializerFor(clazz)
      if (serializer.isInstanceOf[DisabledJavaSerializer]) {
        val notSerializableException =
          new ThrowableNotSerializableException(protoT.getMessage, "unknown")
        log.debug(
          "Couldn't deserialize [{}] because Java serialization is disabled. Fallback to " +
          "ThrowableNotSerializableException. {}",
          notSerializableException.originalClassName,
          notSerializableException.originalMessage)
        notSerializableException
      } else {

        (if (protoT.hasCause) {
           val cause = payloadSupport.deserializePayload(protoT.getCause).asInstanceOf[Throwable]
           system.dynamicAccess.createInstanceFor[Throwable](
             className,
             List(classOf[String] -> protoT.getMessage, classOf[Throwable] -> cause))
         } else {
           system.dynamicAccess.createInstanceFor[Throwable](clazz, List(classOf[String] -> protoT.getMessage))
         }) match {
          case Success(t) =>
            fillInStackTrace(protoT, t)
            t
          case Failure(e) =>
            new ThrowableNotSerializableException(protoT.getMessage, className, e)
        }
      }
    } catch {
      case NonFatal(e) =>
        new ThrowableNotSerializableException(protoT.getMessage, className, e)
    }
  }

  private def fillInStackTrace(protoT: ContainerFormats.Throwable, t: Throwable): Unit = {
    import pekko.util.ccompat.JavaConverters._
    val stackTrace =
      protoT.getStackTraceList.asScala.map { elem =>
        val fileName = elem.getFileName
        new StackTraceElement(
          elem.getClassName,
          elem.getMethodName,
          if (fileName.length > 0) fileName else null,
          elem.getLineNumber)
      }.toArray
    t.setStackTrace(stackTrace)
  }
}
