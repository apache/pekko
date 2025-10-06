/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pekko.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.pekko.annotation.InternalApi;

/**
 * Internal API: An unsynchronized byte array input stream. This class does not copy
 * the provided byte array, and it is not thread safe.
 *
 * @see ByteArrayInputStream
 * @since 2.0.0
 */
//@NotThreadSafe
// copied from https://github.com/apache/commons-io/blob/26e5aa9661a72bfd9697fb384ca72f58e5d672e9/src/main/java/org/apache/commons/io/input/UnsynchronizedByteArrayInputStream.java
@InternalApi
public class UnsynchronizedByteArrayInputStream extends InputStream {

    /**
     * The end of stream marker.
     */
    private static final int END_OF_STREAM = -1;

    private static int minPosLen(final byte[] data, final int defaultValue) {
        requireNonNegative(defaultValue, "defaultValue");
        return Math.min(defaultValue, data.length > 0 ? data.length : defaultValue);
    }

    private static int requireNonNegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }

    private static void checkFromIndexSize(final byte[] array, final int off, final int len) {
        final int arrayLength = Objects.requireNonNull(array, "byte array").length;
        if ((off | len | arrayLength) < 0 || arrayLength - len < off) {
            throw new IndexOutOfBoundsException(String.format("Range [%s, %<s + %s) out of bounds for length %s", off, len, arrayLength));
        }
    }

    /**
     * The underlying data buffer.
     */
    private final byte[] data;

    /**
     * End Of Data.
     *
     * Similar to data.length, which is the last readable offset + 1.
     */
    private final int eod;

    /**
     * Current offset in the data buffer.
     */
    private int offset;

    /**
     * The current mark (if any).
     */
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
     * @param data   the buffer
     * @param offset the offset into the buffer
     * @param length the length of the buffer
     * @throws IllegalArgumentException if the offset or length less than zero
     */
    public UnsynchronizedByteArrayInputStream(final byte[] data, final int offset, final int length) {
        requireNonNegative(offset, "offset");
        requireNonNegative(length, "length");
        this.data = Objects.requireNonNull(data, "data");
        this.eod = Math.min(minPosLen(data, offset) + length, data.length);
        this.offset = minPosLen(data, offset);
        this.markedOffset = minPosLen(data, offset);
    }

    @Override
    public int available() {
        return offset < eod ? eod - offset : 0;
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
        return read(dest, 0, dest.length);
    }

    @Override
    public int read(final byte[] dest, final int off, final int len) {
        checkFromIndexSize(dest, off, len);
        if (len == 0) {
            return 0;
        }

        if (offset >= eod) {
            return END_OF_STREAM;
        }

        int actualLen = eod - offset;
        if (len < actualLen) {
            actualLen = len;
        }
        if (actualLen <= 0) {
            return 0;
        }
        System.arraycopy(data, offset, dest, off, actualLen);
        offset += actualLen;
        return actualLen;
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

        long actualSkip = eod - offset;
        if (n < actualSkip) {
            actualSkip = n;
        }

        offset = Math.addExact(offset, Math.toIntExact(n));
        return actualSkip;
    }
}
