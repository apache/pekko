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

package org.apache.pekko.stream.javadsl.compression;

import org.apache.pekko.annotation.ApiMayChange;
import org.apache.pekko.annotation.InternalApi;
import org.apache.pekko.stream.impl.io.compression.ZstdDictionaryImpl;
import org.apache.pekko.util.ByteString;
import org.apache.pekko.util.OptionalUtil;
import scala.Option;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Configuration class based on the official C zstd library to specify dictionary options for
 * compression.
 *
 * @see <a
 *     href="https://github.com/facebook/zstd?tab=readme-ov-file#dictionary-compression-how-to">Zstd
 *     dictionary readme</a>
 * @see <a href="https://facebook.github.io/zstd/zstd_manual.html#Chapter10">Zstd C manual</a>
 * @since 2.0.0
 */
@ApiMayChange
public final class ZstdDictionary {
  private final ByteString dictionary;
  private final OptionalInt level;
  private final OptionalInt offset;
  private final OptionalInt length;

  private ZstdDictionary(
      ByteString dictionary, OptionalInt level, OptionalInt offset, OptionalInt length) {
    this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
    this.level = level;
    this.offset = offset;
    this.length = length;
  }

  private static OptionalInt optionalInt(Integer value) {
    return value == null ? OptionalInt.empty() : OptionalInt.of(value);
  }

  // the byte[] constructors copy the array, so later changes to it do not affect the dictionary

  public ZstdDictionary(byte[] dictionary) {
    this(ByteString.fromArray(dictionary));
  }

  public ZstdDictionary(byte[] dictionary, int level) {
    this(ByteString.fromArray(dictionary), level);
  }

  public ZstdDictionary(byte[] dictionary, Integer level) {
    this(ByteString.fromArray(dictionary), level);
  }

  public ZstdDictionary(byte[] dictionary, int level, int offset, int length) {
    this(ByteString.fromArray(dictionary), level, offset, length);
  }

  public ZstdDictionary(byte[] dictionary, Integer level, Integer offset, Integer length) {
    this(ByteString.fromArray(dictionary), level, offset, length);
  }

  public ZstdDictionary(ByteString dictionary) {
    this(dictionary, OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty());
  }

  public ZstdDictionary(ByteString dictionary, int level) {
    this(dictionary, OptionalInt.of(level), OptionalInt.empty(), OptionalInt.empty());
  }

  public ZstdDictionary(ByteString dictionary, Integer level) {
    this(dictionary, optionalInt(level), OptionalInt.empty(), OptionalInt.empty());
  }

  public ZstdDictionary(ByteString dictionary, int level, int offset, int length) {
    this(dictionary, OptionalInt.of(level), OptionalInt.of(offset), OptionalInt.of(length));
  }

  public ZstdDictionary(ByteString dictionary, Integer level, Integer offset, Integer length) {
    this(dictionary, optionalInt(level), optionalInt(offset), optionalInt(length));
  }

  public ZstdDictionary(String dictionary) {
    this(ByteString.fromString(dictionary, StandardCharsets.UTF_8));
  }

  public ZstdDictionary(String dictionary, int level) {
    this(ByteString.fromString(dictionary, StandardCharsets.UTF_8), level);
  }

  public ZstdDictionary(String dictionary, Integer level) {
    this(ByteString.fromString(dictionary, StandardCharsets.UTF_8), level);
  }

  public ZstdDictionary(String dictionary, int level, int offset, int length) {
    this(ByteString.fromString(dictionary, StandardCharsets.UTF_8), level, offset, length);
  }

  public ZstdDictionary(String dictionary, Integer level, Integer offset, Integer length) {
    this(ByteString.fromString(dictionary, StandardCharsets.UTF_8), level, offset, length);
  }

  public ByteString getDictionary() {
    return dictionary;
  }

  public OptionalInt getLevel() {
    return level;
  }

  public OptionalInt getOffset() {
    return offset;
  }

  public OptionalInt getLength() {
    return length;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ZstdDictionary)) return false;
    ZstdDictionary that = (ZstdDictionary) other;
    return dictionary.equals(that.dictionary)
        && level.equals(that.level)
        && offset.equals(that.offset)
        && length.equals(that.length);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dictionary, level, offset, length);
  }

  @Override
  public String toString() {
    return "ZstdDictionary("
        + dictionary.size()
        + " bytes, level="
        + level
        + ", offset="
        + offset
        + ", length="
        + length
        + ")";
  }

  /** INTERNAL API */
  @InternalApi
  public ZstdDictionaryImpl toImpl() {
    // a copy, so that whoever holds the impl cannot modify the dictionary
    final byte[] bytes = dictionary.toArray();
    return new ZstdDictionaryImpl() {
      @Override
      public byte[] dictionary() {
        return bytes;
      }

      @Override
      public Option<Object> level() {
        return OptionalUtil.convertOptionalToScala(level);
      }

      @Override
      public Option<Object> offset() {
        return OptionalUtil.convertOptionalToScala(offset);
      }

      @Override
      public Option<Object> length() {
        return OptionalUtil.convertOptionalToScala(length);
      }
    };
  }
}
