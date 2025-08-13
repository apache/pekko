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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static java.lang.invoke.MethodType.methodType;

/**
 * Cleans a direct {@link ByteBuffer}. Without manual intervention, direct ByteBuffers will be
 * cleaned eventually upon garbage collection. However, this should not be relied upon since it may
 * not occur in a timely fashion - especially since off heap ByteBuffers don't put pressure on the
 * garbage collector.
 *
 * <p><strong>Warning:</strong> Do not attempt to use a direct {@link ByteBuffer} that has been
 * cleaned or bad things will happen. Don't use this class unless you can ensure that the cleaned
 * buffer will not be accessed anymore.
 *
 * <p>See <a href=https://bugs.openjdk.java.net/browse/JDK-4724038>JDK-4724038</a>
 */
final class ByteBufferCleaner {

  // adapted from
  // https://github.com/apache/commons-io/blob/441115a4b5cd63ae808dd4c40fc238cb52c8048f/src/main/java/org/apache/commons/io/input/ByteBufferCleaner.java

  private interface Cleaner {
    void clean(ByteBuffer buffer) throws Throwable;
  }

  private static final class Java9Cleaner implements Cleaner {

    private final MethodHandle invokeCleaner;

    private Java9Cleaner() throws ReflectiveOperationException {
      final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      final Field field = unsafeClass.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      final Object theUnsafe = field.get(null);
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      MethodHandle invokeCleaner =
          lookup.findVirtual(
              unsafeClass, "invokeCleaner", methodType(void.class, ByteBuffer.class));
      this.invokeCleaner = invokeCleaner.bindTo(theUnsafe);
    }

    @Override
    public void clean(final ByteBuffer buffer) throws Throwable {
      invokeCleaner.invokeExact(buffer);
    }
  }

  private static final Cleaner INSTANCE = getCleaner();

  /**
   * Releases memory held by the given {@link ByteBuffer}.
   *
   * @param buffer to release.
   * @throws IllegalStateException on internal failure.
   */
  static void clean(final ByteBuffer buffer) {
    try {
      INSTANCE.clean(buffer);
    } catch (final Throwable t) {
      throw new IllegalStateException("Failed to clean direct buffer.", t);
    }
  }

  private static Cleaner getCleaner() {
    Cleaner cleaner = null;
    try {
      cleaner = new Java9Cleaner();
    } catch (final Exception e1) {
      System.err.println(
          "WARNING: Failed to initialize a ByteBuffer Cleaner. This means "
              + "direct ByteBuffers will only be cleaned upon garbage collection. Reason: "
              + e1);
    }
    if (cleaner != null) {
      try {
        ByteBuffer testByteBuffer = ByteBuffer.allocateDirect(1);
        cleaner.clean(testByteBuffer);
      } catch (final Throwable t) {
        cleaner = null;
        System.err.println(
            "WARNING: ByteBuffer Cleaner failed to clean a test buffer. ByteBuffer Cleaner "
                + "has been disabled. This means direct ByteBuffers will only be cleaned upon garbage collection. "
                + "Reason: "
                + t);
      }
    }
    return cleaner;
  }

  /**
   * Tests if were able to load a suitable cleaner for the current JVM. Attempting to call {@code
   * ByteBufferCleaner#clean(ByteBuffer)} when this method returns false will result in an
   * exception.
   *
   * @return {@code true} if cleaning is supported, {@code false} otherwise.
   */
  static boolean isSupported() {
    return INSTANCE != null;
  }
}
