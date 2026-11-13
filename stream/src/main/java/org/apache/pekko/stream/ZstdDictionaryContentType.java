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

package org.apache.pekko.stream;

import org.apache.pekko.annotation.ApiMayChange;

/**
 * How zstd interprets the bytes of a {@link
 * org.apache.pekko.stream.scaladsl.compression.ZstdDictionary} or {@link
 * org.apache.pekko.stream.javadsl.compression.ZstdDictionary}, the equivalent of {@code
 * ZSTD_dictContentType_e} in the C zstd library.
 *
 * <p>Any type other than {@link #AUTO} requires zstd-jni 1.5.7-26 or later.
 *
 * @see <a href="https://facebook.github.io/zstd/zstd_manual.html">Zstd C manual</a>
 * @since 2.1.0
 */
@ApiMayChange
public enum ZstdDictionaryContentType {
  /**
   * If the bytes start with the zstd dictionary magic number ({@code 0xEC30A437}), they are parsed
   * as a formatted dictionary, otherwise they are used as raw content. This is the default.
   */
  AUTO,
  /**
   * The bytes are always used as raw content, even if they start with the zstd dictionary magic
   * number. Use this when the dictionary can be any data, for example for the HTTP {@code dcz}
   * content coding of <a href="https://www.rfc-editor.org/rfc/rfc9842">RFC 9842</a>.
   */
  RAW_CONTENT,
  /** The bytes must be a formatted zstd dictionary, otherwise loading the dictionary fails. */
  FULL_DICT
}
