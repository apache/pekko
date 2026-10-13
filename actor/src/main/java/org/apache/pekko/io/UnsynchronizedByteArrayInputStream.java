/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.io;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import org.apache.pekko.annotation.InternalApi;

/**
 * Internal API: An unsynchronized byte array input stream. This class does not copy the provided
 * byte array, and it is not thread safe.
 *
 * <p>All bulk operations ({@link #readAllBytes()}, {@link #readNBytes(int)}, {@link
 * #transferTo(OutputStream)}, ...) are overridden so that they operate directly on the backing
 * array instead of going through the chunked, allocation-heavy defaults in {@link InputStream}.
 *
 * @see ByteArrayInputStream
 * @since 2.0.0
 */
// @NotThreadSafe
// originally copied from
// https://github.com/apache/commons-io/blob/26e5aa9661a72bfd9697fb384ca72f58e5d672e9/src/main/java/org/apache/commons/io/input/UnsynchronizedByteArrayInputStream.java
@InternalApi
public class UnsynchronizedByteArrayInputStream extends InputStream {

  /** The end of stream marker. */
  private static final int END_OF_STREAM = -1;

  private static int requireNonNegative(final int value, final String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " cannot be negative");
    }
    return value;
  }

  /** The underlying data buffer. */
  private final byte[] data;

  /**
   * End Of Data.
   *
   * <p>Similar to data.length, which is the last readable offset + 1. Invariant: {@code offset <=
   * eod <= data.length}.
   */
  private final int eod;

  /** Current offset in the data buffer. */
  private int offset;

  /** The current mark (if any). */
  private int markedOffset;

  /**
   * Constructs a new byte array input stream.
   *
   * @param data the buffer
   */
  public UnsynchronizedByteArrayInputStream(final byte[] data) {
    this.data = Objects.requireNonNull(data, "data");
    this.offset = 0;
    this.markedOffset = 0;
    this.eod = data.length;
  }

  /**
   * Constructs a new byte array input stream.
   *
   * @param data the buffer
   * @param offset the offset into the buffer
   * @param length the length of the buffer
   * @throws java.lang.IllegalArgumentException if the offset or length less than zero
   */
  public UnsynchronizedByteArrayInputStream(final byte[] data, final int offset, final int length) {
    requireNonNegative(offset, "offset");
    requireNonNegative(length, "length");
    this.data = Objects.requireNonNull(data, "data");
    final int start = Math.min(offset, data.length);
    this.offset = start;
    this.markedOffset = start;
    // long arithmetic avoids int overflow for large offset + length
    this.eod = (int) Math.min((long) start + length, data.length);
  }

  @Override
  public int available() {
    return eod - offset;
  }

  @SuppressWarnings("sync-override")
  @Override
  public void mark(final int readLimit) {
    this.markedOffset = this.offset;
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  @Override
  public int read() {
    return offset < eod ? data[offset++] & 0xff : END_OF_STREAM;
  }

  @Override
  public int read(final byte[] dest) {
    Objects.requireNonNull(dest, "dest");
    return readLocal(dest, 0, dest.length);
  }

  @Override
  public int read(final byte[] dest, final int off, final int len) {
    Objects.checkFromIndexSize(off, len, Objects.requireNonNull(dest, "dest").length);
    return readLocal(dest, off, len);
  }

  private int readLocal(final byte[] dest, final int off, final int len) {
    if (len == 0) {
      return 0;
    }
    final int actualLen = Math.min(len, eod - offset);
    if (actualLen <= 0) {
      return END_OF_STREAM;
    }
    System.arraycopy(data, offset, dest, off, actualLen);
    offset += actualLen;
    return actualLen;
  }

  @Override
  public byte[] readAllBytes() {
    final byte[] result = Arrays.copyOfRange(data, offset, eod);
    offset = eod;
    return result;
  }

  @Override
  public byte[] readNBytes(final int len) {
    if (len < 0) {
      // same exception type/message as InputStream.readNBytes(int)
      throw new IllegalArgumentException("len < 0");
    }
    final int actualLen = Math.min(len, eod - offset);
    final byte[] result = Arrays.copyOfRange(data, offset, offset + actualLen);
    offset += actualLen;
    return result;
  }

  @Override
  public int readNBytes(final byte[] dest, final int off, final int len) {
    final int n = read(dest, off, len);
    return n == END_OF_STREAM ? 0 : n;
  }

  @SuppressWarnings("sync-override")
  @Override
  public void reset() {
    this.offset = this.markedOffset;
  }

  @Override
  public long skip(final long n) {
    if (n < 0) {
      throw new IllegalArgumentException("Skipping backward is not supported");
    }
    final int actualSkip = (int) Math.min(n, eod - offset);
    offset += actualSkip;
    return actualSkip;
  }

  @Override
  public void skipNBytes(final long n) throws IOException {
    if (n > 0) {
      if (n > eod - offset) {
        offset = eod;
        throw new EOFException();
      }
      offset += (int) n;
    }
  }

  @Override
  public long transferTo(final OutputStream out) throws IOException {
    Objects.requireNonNull(out, "out");
    final int len = eod - offset;
    if (len > 0) {
      out.write(data, offset, len);
      offset = eod;
    }
    return len;
  }
}
