/*
 * Copyright 2014 - 2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.remote.artery.aeron;

import io.aeron.CncFileDescriptor;
import java.io.File;
import java.nio.MappedByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.errors.ErrorLogReader;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.event.NoLogging;
import org.apache.pekko.util.Helpers;

/**
 * INTERNAL API
 *
 * <p>Application to print out errors recorded in the command-and-control (cnc) file is maintained
 * by media driver in shared memory. This application reads the cnc file and prints the distinct
 * errors. Layout of the cnc file is described in {@link io.aeron.CncFileDescriptor}.
 */
public class AeronErrorLog {
  final MappedByteBuffer cncByteBuffer;
  final AtomicBuffer buffer;

  @Deprecated
  public AeronErrorLog(File cncFile) {
    this(cncFile, NoLogging.getInstance());
  }

  public AeronErrorLog(File cncFile, LoggingAdapter log) {
    cncByteBuffer = IoUtil.mapExistingFile(cncFile, "cnc");
    final DirectBuffer cncMetaDataBuffer = CncFileDescriptor.createMetaDataBuffer(cncByteBuffer);
    final int cncVersion = cncMetaDataBuffer.getInt(CncFileDescriptor.cncVersionOffset(0));
    buffer = CncFileDescriptor.createErrorLogBuffer(cncByteBuffer, cncMetaDataBuffer);

    if (CncFileDescriptor.CNC_VERSION != cncVersion) {
      log.warning(
          "Aeron CnC version mismatch: compiled version = {}, file version = {}",
          CncFileDescriptor.CNC_VERSION,
          cncVersion);
    }
  }

  public long logErrors(LoggingAdapter log, long sinceTimestamp) {
    // using AtomicLong because access from lambda, not because of concurrency
    final AtomicLong lastTimestamp = new AtomicLong(sinceTimestamp);

    ErrorLogReader.read(
        buffer,
        (observationCount,
            firstObservationTimestamp,
            lastObservationTimestamp,
            encodedException) -> {
          log.error(
              String.format(
                  "Aeron error: %d observations from %s to %s for:%n %s",
                  observationCount,
                  Helpers.timestamp(firstObservationTimestamp),
                  Helpers.timestamp(lastObservationTimestamp),
                  encodedException));
          lastTimestamp.set(Math.max(lastTimestamp.get(), lastObservationTimestamp));
        },
        sinceTimestamp);
    return lastTimestamp.get();
  }

  public void close() {
    IoUtil.unmap(cncByteBuffer);
  }
}
