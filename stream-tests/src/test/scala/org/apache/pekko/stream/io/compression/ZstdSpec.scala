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

package org.apache.pekko.stream.io.compression

import com.github.luben.zstd.{ ZstdIOException, ZstdInputStream, ZstdOutputStream }

import org.apache.pekko
import pekko.stream.impl.io.compression.{ Compressor, ZstdCompressor }
import pekko.stream.scaladsl.{ Compression, Flow }
import pekko.util.ByteString

import java.io.{ InputStream, OutputStream }

class ZstdSpec extends CoderSpec[ZstdIOException]("zstd") {
  import CompressionTestingTools._

  override protected def newCompressor(): Compressor = new ZstdCompressor

  override protected def encoderFlow: Flow[ByteString, ByteString, Any] = Compression.zstd

  override protected def decoderFlow(maxBytesPerChunk: Int): Flow[ByteString, ByteString, Any] =
    Compression.zstdDecompress(maxBytesPerChunk)

  override protected def newDecodedInputStream(underlying: InputStream): InputStream =
    new ZstdInputStream(underlying)

  override protected def newEncodedOutputStream(underlying: OutputStream): OutputStream =
    new ZstdOutputStream(underlying)

  override def extraTests(): Unit = {
    "decode concatenated compressions" in {
      ourDecode(Seq(encode("Hello, "), encode("dear "), encode("User!")).join) should readAs("Hello, dear User!")
    }
  }
}
