/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.persistence.typed.auction;

/** Auction status. */
public enum AuctionStatus {
  /** The auction hasn't started yet (or doesn't exist). */
  NOT_STARTED,
  /** The item is under auction. */
  UNDER_AUCTION,
  /** The auction is complete. */
  COMPLETE,
  /** The auction is cancelled. */
  CANCELLED
}
