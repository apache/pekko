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

package jdocs.actor;

// #bytebufserializer-with-manifest
import org.apache.pekko.serialization.ByteBufferSerializer;
import org.apache.pekko.serialization.SerializerWithStringManifest;

// #bytebufserializer-with-manifest
import java.nio.ByteBuffer;

public class ByteBufferSerializerDocTest {

  static // #bytebufserializer-with-manifest
  class ExampleByteBufSerializer extends SerializerWithStringManifest
      implements ByteBufferSerializer {

    @Override
    public int identifier() {
      return 1337;
    }

    @Override
    public String manifest(Object o) {
      return "serialized-" + o.getClass().getSimpleName();
    }

    @Override
    public byte[] toBinary(Object o) {
      // in production code, acquire this from a BufferPool
      final ByteBuffer buf = ByteBuffer.allocate(256);

      toBinary(o, buf);
      buf.flip();
      final byte[] bytes = new byte[buf.remaining()];
      buf.get(bytes);
      return bytes;
    }

    @Override
    public Object fromBinary(byte[] bytes, String manifest) {
      return fromBinary(ByteBuffer.wrap(bytes), manifest);
    }

    @Override
    public void toBinary(Object o, ByteBuffer buf) {
      // Implement actual serialization here
    }

    @Override
    public Object fromBinary(ByteBuffer buf, String manifest) {
      // Implement actual deserialization here
      return null;
    }
  }
  // #bytebufserializer-with-manifest

  static class OnlyForDocInclude {
    static
    // #ByteBufferSerializer-interface
    interface ByteBufferSerializer {
      /** Serializes the given object into the `ByteBuffer`. */
      void toBinary(Object o, ByteBuffer buf);

      /**
       * Produces an object from a `ByteBuffer`, with an optional type-hint; the class should be
       * loaded using ActorSystem.dynamicAccess.
       */
      Object fromBinary(ByteBuffer buf, String manifest);
    }
    // #ByteBufferSerializer-interface
  }
}
