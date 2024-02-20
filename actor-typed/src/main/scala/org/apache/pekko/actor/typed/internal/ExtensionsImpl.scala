/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal

import java.util.concurrent.{ ConcurrentHashMap, CountDownLatch }

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

import org.apache.pekko
import pekko.actor.typed.{ ActorSystem, Extension, ExtensionId, Extensions }
import pekko.actor.typed.ExtensionSetup
import pekko.annotation.InternalApi
import pekko.util.ccompat.JavaConverters._

/**
 * INTERNAL API
 *
 * Actor system extensions registry
 */
@InternalApi
private[pekko] trait ExtensionsImpl extends Extensions { self: ActorSystem[?] with InternalRecipientRef[?] =>

  private val extensions = new ConcurrentHashMap[ExtensionId[?], AnyRef]

  /**
   * Hook for ActorSystem to load extensions on startup
   */
  def loadExtensions(): Unit = {

    /*
     * @param throwOnLoadFail Throw exception when an extension fails to load (needed for backwards compatibility)
     */
    def loadExtensionsFor(key: String, throwOnLoadFail: Boolean): Unit = {

      settings.config.getStringList(key).asScala.foreach { extensionIdFQCN =>
        // it is either a Scala object or it is a Java class with a static singleton accessor
        val idTry = dynamicAccess.getObjectFor[AnyRef](extensionIdFQCN).recoverWith {
          case _ => idFromJavaSingletonAccessor(extensionIdFQCN)
        }

        idTry match {
          case Success(id: ExtensionId[?]) => registerExtension(id)
          case Success(_) =>
            if (!throwOnLoadFail) log.error("[{}] is not an 'ExtensionId', skipping...", extensionIdFQCN)
            else throw new RuntimeException(s"[$extensionIdFQCN] is not an 'ExtensionId'")
          case Failure(problem) =>
            if (!throwOnLoadFail)
              log.error(s"While trying to load extension $extensionIdFQCN, skipping...", problem)
            else throw new RuntimeException(s"While trying to load extension [$extensionIdFQCN]", problem)
        }
      }
    }

    def idFromJavaSingletonAccessor(extensionIdFQCN: String): Try[ExtensionId[Extension]] =
      dynamicAccess.getClassFor[ExtensionId[Extension]](extensionIdFQCN).flatMap[ExtensionId[Extension]] {
        (clazz: Class[?]) =>
          Try {
            val singletonAccessor = clazz.getDeclaredMethod("getInstance")
            singletonAccessor.invoke(null).asInstanceOf[ExtensionId[Extension]]
          }
      }

    loadExtensionsFor("pekko.actor.typed.library-extensions", throwOnLoadFail = true)
    loadExtensionsFor("pekko.actor.typed.extensions", throwOnLoadFail = false)
  }

  final override def hasExtension(ext: ExtensionId[? <: Extension]): Boolean = findExtension(ext) != null

  final override def extension[T <: Extension](ext: ExtensionId[T]): T = findExtension(ext) match {
    case null => throw new IllegalArgumentException(s"Trying to get non-registered extension [$ext]")
    case some => some.asInstanceOf[T]
  }

  final override def registerExtension[T <: Extension](ext: ExtensionId[T]): T =
    findExtension(ext) match {
      case null     => createExtensionInstance(ext)
      case existing => existing.asInstanceOf[T]
    }

  private def createExtensionInstance[T <: Extension](ext: ExtensionId[T]): T = {
    val inProcessOfRegistration = new CountDownLatch(1)
    extensions.putIfAbsent(ext, inProcessOfRegistration) match { // Signal that registration is in process
      case null =>
        try { // Signal was successfully sent
          // Create and initialize the extension, first look for ExtensionSetup
          val instance = self.settings.setup.setups
            .collectFirst {
              case (_, extSetup: ExtensionSetup[?]) if extSetup.extId == ext => extSetup.createExtension(self)
            }
            .getOrElse(ext.createExtension(self))
          instance match {
            case null => throw new IllegalStateException(s"Extension instance created as 'null' for extension [$ext]")
            case nonNull =>
              val instance = nonNull.asInstanceOf[T]
              // Replace our in process signal with the initialized extension
              extensions.replace(ext, inProcessOfRegistration, instance)
              instance
          }
        } catch {
          case t: Throwable =>
            // In case shit hits the fan, remove the inProcess signal and escalate to caller
            extensions.replace(ext, inProcessOfRegistration, t)
            throw t
        } finally {
          // Always notify listeners of the inProcess signal
          inProcessOfRegistration.countDown()
        }
      case _ =>
        // Someone else is in process of registering an extension for this Extension, retry
        registerExtension(ext)
    }
  }

  /**
   * Returns any extension registered to the specified Extension or returns null if not registered
   */
  @tailrec
  private def findExtension[T <: Extension](ext: ExtensionId[T]): T = extensions.get(ext) match {
    case c: CountDownLatch =>
      // Registration in process, await completion and retry
      c.await()
      findExtension(ext)
    case t: Throwable => throw t // Initialization failed, throw same again
    case other        => other.asInstanceOf[T] // could be a T or null, in which case we return the null as T
  }
}
