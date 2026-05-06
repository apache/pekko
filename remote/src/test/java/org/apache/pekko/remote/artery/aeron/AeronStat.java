/*
 * Copyright 2014-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.remote.artery.aeron;

import static io.aeron.CncFileDescriptor.*;
import static io.aeron.driver.status.PerImageIndicator.PER_IMAGE_TYPE_ID;
import static io.aeron.driver.status.PublisherLimit.PUBLISHER_LIMIT_TYPE_ID;
import static io.aeron.driver.status.ReceiveChannelStatus.RECEIVE_CHANNEL_STATUS_TYPE_ID;
import static io.aeron.driver.status.ReceiverPos.RECEIVER_POS_TYPE_ID;
import static io.aeron.driver.status.SendChannelStatus.SEND_CHANNEL_STATUS_TYPE_ID;
import static io.aeron.driver.status.StreamCounter.*;
import static io.aeron.driver.status.SystemCounterDescriptor.SYSTEM_COUNTER_TYPE_ID;

import io.aeron.CncFileDescriptor;
import io.aeron.CommonContext;
import io.aeron.status.ChannelEndpointStatus;
import java.io.File;
import java.io.PrintStream;
import java.nio.MappedByteBuffer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.concurrent.status.CountersReader;

/**
 * Tool for printing out Aeron counters. A command-and-control (cnc) file is maintained by media
 * driver in shared memory. This application reads the cnc file and prints the counters. Layout of
 * the cnc file is described in {@link CncFileDescriptor}.
 *
 * <p>This tool accepts filters on the command line, e.g. for connections only see example below:
 * <code>
 *     java -cp aeron-samples/build/libs/samples.jar io.aeron.samples.AeronStat type=[1-4] identity=12345
 * </code>
 */
public class AeronStat {
  /**
   * Types of the counters.
   *
   * <ul>
   *   <li>0: System Counters
   *   <li>1 - 4: Stream Positions
   * </ul>
   */
  private static final String COUNTER_TYPE_ID = "type";

  /**
   * The identity of each counter that can either be the system counter id or registration id for
   * positions.
   */
  private static final String COUNTER_IDENTITY = "identity";

  /** Session id filter to be used for position counters. */
  private static final String COUNTER_SESSION_ID = "session";

  /** Stream id filter to be used for position counters. */
  private static final String COUNTER_STREAM_ID = "stream";

  /** Channel filter to be used for position counters. */
  private static final String COUNTER_CHANNEL = "channel";

  private static final int ONE_SECOND = 1_000;

  private final CountersReader counters;
  private final Pattern typeFilter;
  private final Pattern identityFilter;
  private final Pattern sessionFilter;
  private final Pattern streamFilter;
  private final Pattern channelFilter;

  public AeronStat(
      final CountersReader counters,
      final Pattern typeFilter,
      final Pattern identityFilter,
      final Pattern sessionFilter,
      final Pattern streamFilter,
      final Pattern channelFilter) {
    this.counters = counters;
    this.typeFilter = typeFilter;
    this.identityFilter = identityFilter;
    this.sessionFilter = sessionFilter;
    this.streamFilter = streamFilter;
    this.channelFilter = channelFilter;
  }

  public AeronStat(final CountersReader counters) {
    this.counters = counters;
    this.typeFilter = null;
    this.identityFilter = null;
    this.sessionFilter = null;
    this.streamFilter = null;
    this.channelFilter = null;
  }

  public static CountersReader mapCounters() {
    return mapCounters(CommonContext.newDefaultCncFile());
  }

  public static CountersReader mapCounters(final MappedByteBuffer cncByteBuffer) {
    final DirectBuffer cncMetaData = createMetaDataBuffer(cncByteBuffer);
    final int cncVersion = cncMetaData.getInt(cncVersionOffset(0));

    if (CncFileDescriptor.CNC_VERSION != cncVersion) {
      throw new IllegalStateException(
          "Aeron CnC version does not match: version=" + cncVersion + " required=" + CNC_VERSION);
    }

    return new CountersReader(
        createCountersMetaDataBuffer(cncByteBuffer, cncMetaData),
        createCountersValuesBuffer(cncByteBuffer, cncMetaData));
  }

  public static CountersReader mapCounters(final File cncFile) {
    System.out.println("Command `n Control file " + cncFile);

    final MappedByteBuffer cncByteBuffer = IoUtil.mapExistingFile(cncFile, "cnc");
    final DirectBuffer cncMetaData = createMetaDataBuffer(cncByteBuffer);
    final int cncVersion = cncMetaData.getInt(cncVersionOffset(0));

    if (CncFileDescriptor.CNC_VERSION != cncVersion) {
      throw new IllegalStateException(
          "Aeron CnC version does not match: version=" + cncVersion + " required=" + CNC_VERSION);
    }

    return new CountersReader(
        createCountersMetaDataBuffer(cncByteBuffer, cncMetaData),
        createCountersValuesBuffer(cncByteBuffer, cncMetaData));
  }

  public void print(final PrintStream out) {
    counters.forEach(
        (counterId, typeId, keyBuffer, label) -> {
          if (filter(typeId, keyBuffer)) {
            final long value = counters.getCounterValue(counterId);
            out.format("%3d: %,20d - %s%n", counterId, value, label);
          }
        });
  }

  private boolean filter(final int typeId, final DirectBuffer keyBuffer) {
    if (!match(typeFilter, () -> Integer.toString(typeId))) {
      return false;
    }

    if (SYSTEM_COUNTER_TYPE_ID == typeId
        && !match(identityFilter, () -> Integer.toString(keyBuffer.getInt(0)))) {
      return false;
    } else if ((typeId >= PUBLISHER_LIMIT_TYPE_ID && typeId <= RECEIVER_POS_TYPE_ID)
        || typeId == PER_IMAGE_TYPE_ID) {
      if (!match(identityFilter, () -> Long.toString(keyBuffer.getLong(REGISTRATION_ID_OFFSET)))
          || !match(sessionFilter, () -> Integer.toString(keyBuffer.getInt(SESSION_ID_OFFSET)))
          || !match(streamFilter, () -> Integer.toString(keyBuffer.getInt(STREAM_ID_OFFSET)))
          || !match(channelFilter, () -> keyBuffer.getStringUtf8(CHANNEL_OFFSET))) {
        return false;
      }
    } else if (typeId >= SEND_CHANNEL_STATUS_TYPE_ID && typeId <= RECEIVE_CHANNEL_STATUS_TYPE_ID) {
      if (!match(
          channelFilter, () -> keyBuffer.getStringUtf8(ChannelEndpointStatus.CHANNEL_OFFSET))) {
        return false;
      }
    }

    return true;
  }

  private static boolean match(final Pattern pattern, final Supplier<String> supplier) {
    return null == pattern || pattern.matcher(supplier.get()).find();
  }
}
