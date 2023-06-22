/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote

import org.apache.pekko
import pekko.actor.Address
import pekko.annotation.ApiMayChange
import pekko.event.LogMarker

/**
 * This is public with the purpose to document the used markers and properties of log events.
 * No guarantee that it will remain binary compatible, but the marker names and properties
 * are considered public API and will not be changed without notice.
 */
@ApiMayChange
object RemoteLogMarker {

  /**
   * Marker "pekkoFailureDetectorGrowing" of log event when failure detector heartbeat interval
   * is growing too large.
   *
   * @param remoteAddress The address of the node that the failure detector is monitoring. Included as property "pekkoRemoteAddress".
   */
  def failureDetectorGrowing(remoteAddress: String): LogMarker =
    LogMarker("pekkoFailureDetectorGrowing", Map(LogMarker.Properties.RemoteAddress -> remoteAddress))

  /**
   * Marker "pekkoQuarantine" of log event when a node is quarantined.
   *
   * @param remoteAddress The address of the node that is quarantined. Included as property "pekkoRemoteAddress".
   * @param remoteAddressUid The address of the node that is quarantined. Included as property "pekkoRemoteAddressUid".
   */
  def quarantine(remoteAddress: Address, remoteAddressUid: Option[Long]): LogMarker =
    LogMarker(
      "pekkoQuarantine",
      Map(
        LogMarker.Properties.RemoteAddress -> remoteAddress,
        LogMarker.Properties.RemoteAddressUid -> remoteAddressUid.getOrElse("")))

  /**
   * Marker "pekkoConnect" of log event when outbound connection is attempted.
   *
   * @param remoteAddress The address of the connected node. Included as property "pekkoRemoteAddress".
   * @param remoteAddressUid The address of the connected node. Included as property "pekkoRemoteAddressUid".
   */
  def connect(remoteAddress: Address, remoteAddressUid: Option[Long]): LogMarker =
    LogMarker(
      "pekkoConnect",
      Map(
        LogMarker.Properties.RemoteAddress -> remoteAddress,
        LogMarker.Properties.RemoteAddressUid -> remoteAddressUid.getOrElse("")))

  /**
   * Marker "pekkoDisconnected" of log event when outbound connection is closed.
   *
   * @param remoteAddress The address of the disconnected node. Included as property "pekkoRemoteAddress".
   * @param remoteAddressUid The address of the disconnected node. Included as property "pekkoRemoteAddressUid".
   */
  def disconnected(remoteAddress: Address, remoteAddressUid: Option[Long]): LogMarker =
    LogMarker(
      "pekkoDisconnected",
      Map(
        LogMarker.Properties.RemoteAddress -> remoteAddress,
        LogMarker.Properties.RemoteAddressUid -> remoteAddressUid.getOrElse("")))
}
