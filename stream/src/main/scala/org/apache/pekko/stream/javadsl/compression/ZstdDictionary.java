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

import org.apache.pekko.annotation.InternalApi;
import org.apache.pekko.stream.impl.io.compression.ZstdDictionaryImpl;
import org.apache.pekko.util.ByteString;
import org.apache.pekko.util.OptionalUtil;
import scala.Option;

import java.util.OptionalInt;

/**
 * Configuration class based on the official C zstd library to specify dictionary options for
 * compression.
 *
 * @see <a href="https://github.com/facebook/zstd?tab=readme-ov-file#dictionary-compression-how-to">Zstd dictionary readme</a>
 * @see <a href="https://facebook.github.io/zstd/zstd_manual.html#Chapter10">Zstd C manual</a>
 * @since 2.0.0
 */
public class ZstdDictionary {
    byte[] dictionary;
    private OptionalInt level = OptionalInt.empty();
    private OptionalInt offset = OptionalInt.empty();
    private OptionalInt length = OptionalInt.empty();

    public ZstdDictionary(byte[] dictionary) {
        this.dictionary = dictionary;
    }

    public ZstdDictionary(byte[] dictionary, int level) {
        this.dictionary = dictionary;
        this.level = OptionalInt.of(level);
    }

    public ZstdDictionary(byte[] dictionary, Integer level) {
        this.dictionary = dictionary;
        if (level != null) {
            this.level = OptionalInt.of(level);
        }
    }

    public ZstdDictionary(byte[] dictionary, int level, int offset, int length) {
        this.dictionary = dictionary;
        this.level = OptionalInt.of(level);
        this.offset = OptionalInt.of(offset);
        this.length = OptionalInt.of(length);
    }

    public ZstdDictionary(byte[] dictionary, Integer level, Integer offset, Integer length) {
        this.dictionary = dictionary;
        if (level != null) {
            this.level = OptionalInt.of(level);
        }
        if (offset != null) {
            this.offset = OptionalInt.of(offset);
        }
        if (length != null) {
            this.length = OptionalInt.of(length);
        }
    }

    public ZstdDictionary(ByteString dictionary) {
        this(dictionary.toArray());
    }

    public ZstdDictionary(ByteString dictionary, int level) {
        this(dictionary.toArray(), level);
    }

    public ZstdDictionary(ByteString dictionary, Integer level) {
        this(dictionary.toArray(), level);
    }

    public ZstdDictionary(ByteString dictionary, int level, int offset, int length) {
        this(dictionary.toArray(), level, offset, length);
    }

    public ZstdDictionary(ByteString dictionary, Integer level, Integer offset, Integer length) {
        this(dictionary.toArray(), level, offset, length);
    }

    public ZstdDictionary(String dictionary) {
        this(dictionary.getBytes());
    }

    public ZstdDictionary(String dictionary, int level) {
        this(dictionary.getBytes(), level);
    }

    public ZstdDictionary(String dictionary, Integer level) {
        this(dictionary.getBytes(), level);
    }

    public ZstdDictionary(String dictionary, int level, int offset, int length) {
        this(dictionary.getBytes(), level, offset, length);
    }

    public ZstdDictionary(String dictionary, Integer level, Integer offset, Integer length) {
        this(dictionary.getBytes(), level, offset, length);
    }

    public byte[] getDictionary() {
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

    public void setDictionary(byte[] dictionary) {
        this.dictionary = dictionary;
    }

    public void setLevel(int level) {
        this.level = OptionalInt.of(level);
    }

    public void setLevel(OptionalInt level) {
        this.level = level;
    }

    public void setLevel(Integer level) {
        if (level == null) {
            this.level = OptionalInt.empty();
        } else {
            this.level = OptionalInt.of(level);
        }
    }

    public void setOffset(int offset) {
        this.offset = OptionalInt.of(offset);
    }

    public void setOffset(Integer offset) {
        if (offset == null) {
            this.offset = OptionalInt.empty();
        } else {
            this.offset = OptionalInt.of(offset);
        }
    }

    public void setOffset(OptionalInt offset) {
        this.offset = offset;
    }

    public void setLength(int length) {
        this.length = OptionalInt.of(length);
    }

    public void setLength(Integer length) {
        if (length == null) {
            this.length = OptionalInt.empty();
        } else {
            this.length = OptionalInt.of(length);
        }
    }

    public void setLength(OptionalInt length) {
        this.length = length;
    }

    @InternalApi
    public ZstdDictionaryImpl toImpl() {
        return new ZstdDictionaryImpl() {
            @Override
            public byte[] dictionary() {
                return dictionary;
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
