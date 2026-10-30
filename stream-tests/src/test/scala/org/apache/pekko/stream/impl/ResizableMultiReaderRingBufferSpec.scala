/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import scala.util.Random

import org.apache.pekko
import pekko.stream.impl.ResizableMultiReaderRingBuffer._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ResizableMultiReaderRingBufferSpec extends AnyWordSpec with Matchers {

  "A ResizableMultiReaderRingBuffer" should {

    "initially be empty (1)" in new Test(iSize = 2, mSize = 4, cursorCount = 1) {
      inspect shouldEqual "0 0 (size=0, writeIx=0, readIx=0, cursors=1)"
    }

    "initially be empty (2)" in new Test(iSize = 4, mSize = 4, cursorCount = 3) {
      inspect shouldEqual "0 0 0 0 (size=0, writeIx=0, readIx=0, cursors=3)"
    }

    "fail reads if nothing can be read" in new Test(iSize = 4, mSize = 4, cursorCount = 3) {
      write(1) shouldEqual true
      write(2) shouldEqual true
      write(3) shouldEqual true
      inspect shouldEqual "1 2 3 0 (size=3, writeIx=3, readIx=0, cursors=3)"
      read(0) shouldEqual 1
      read(0) shouldEqual 2
      read(1) shouldEqual 1
      inspect shouldEqual "1 2 3 0 (size=3, writeIx=3, readIx=0, cursors=3)"
      read(0) shouldEqual 3
      read(0) shouldEqual null
      read(1) shouldEqual 2
      inspect shouldEqual "1 2 3 0 (size=3, writeIx=3, readIx=0, cursors=3)"
      read(2) shouldEqual 1
      inspect shouldEqual "0 2 3 0 (size=2, writeIx=3, readIx=1, cursors=3)"
      read(1) shouldEqual 3
      read(1) shouldEqual null
      read(2) shouldEqual 2
      read(2) shouldEqual 3
      inspect shouldEqual "0 0 0 0 (size=0, writeIx=3, readIx=3, cursors=3)"
    }

    "fail writes if there is no more space" in new Test(iSize = 4, mSize = 4, cursorCount = 2) {
      write(1) shouldEqual true
      write(2) shouldEqual true
      write(3) shouldEqual true
      write(4) shouldEqual true
      write(5) shouldEqual false
      read(0) shouldEqual 1
      write(5) shouldEqual false
      read(1) shouldEqual 1
      write(5) shouldEqual true
      read(0) shouldEqual 2
      read(0) shouldEqual 3
      read(0) shouldEqual 4
      read(1) shouldEqual 2
      write(6) shouldEqual true
      inspect shouldEqual "5 6 3 4 (size=4, writeIx=6, readIx=2, cursors=2)"
      read(0) shouldEqual 5
      read(0) shouldEqual 6
      read(0) shouldEqual null
      read(1) shouldEqual 3
      read(1) shouldEqual 4
      read(1) shouldEqual 5
      read(1) shouldEqual 6
      read(1) shouldEqual null
      inspect shouldEqual "0 0 0 0 (size=0, writeIx=6, readIx=6, cursors=2)"
      write(7) shouldEqual true
      write(8) shouldEqual true
      write(9) shouldEqual true
      inspect shouldEqual "9 0 7 8 (size=3, writeIx=9, readIx=6, cursors=2)"
      read(0) shouldEqual 7
      read(0) shouldEqual 8
      read(0) shouldEqual 9
      read(0) shouldEqual null
      read(1) shouldEqual 7
      read(1) shouldEqual 8
      read(1) shouldEqual 9
      read(1) shouldEqual null
      inspect shouldEqual "0 0 0 0 (size=0, writeIx=9, readIx=9, cursors=2)"
    }

    "automatically grow if possible" in new Test(iSize = 2, mSize = 8, cursorCount = 2) {
      write(1) shouldEqual true
      inspect shouldEqual "1 0 (size=1, writeIx=1, readIx=0, cursors=2)"
      write(2) shouldEqual true
      inspect shouldEqual "1 2 (size=2, writeIx=2, readIx=0, cursors=2)"
      write(3) shouldEqual true
      inspect shouldEqual "1 2 3 0 (size=3, writeIx=3, readIx=0, cursors=2)"
      write(4) shouldEqual true
      inspect shouldEqual "1 2 3 4 (size=4, writeIx=4, readIx=0, cursors=2)"
      read(0) shouldEqual 1
      read(0) shouldEqual 2
      read(0) shouldEqual 3
      read(1) shouldEqual 1
      read(1) shouldEqual 2
      write(5) shouldEqual true
      inspect shouldEqual "5 0 3 4 (size=3, writeIx=5, readIx=2, cursors=2)"
      write(6) shouldEqual true
      inspect shouldEqual "5 6 3 4 (size=4, writeIx=6, readIx=2, cursors=2)"
      write(7) shouldEqual true
      inspect shouldEqual "3 4 5 6 7 0 0 0 (size=5, writeIx=5, readIx=0, cursors=2)"
      read(0) shouldEqual 4
      read(0) shouldEqual 5
      read(0) shouldEqual 6
      read(0) shouldEqual 7
      read(0) shouldEqual null
      read(1) shouldEqual 3
      read(1) shouldEqual 4
      read(1) shouldEqual 5
      read(1) shouldEqual 6
      read(1) shouldEqual 7
      read(1) shouldEqual null
      inspect shouldEqual "0 0 0 0 0 0 0 0 (size=0, writeIx=5, readIx=5, cursors=2)"
    }

    "keep cursor reads linear when equally paced readers consume an element" in {
      class CountingCursor extends Cursor {
        private var index = 0
        var reads = 0
        def cursor: Int = {
          reads += 1
          index
        }
        def cursor_=(value: Int): Unit = index = value
      }

      val readers = List.fill(32)(new CountingCursor)
      val buffer = new TestBuffer(2, 4, new Cursors { def cursors = readers })
      for (element <- 1 to 10) {
        readers.foreach(_.reads = 0)
        buffer.write(element) shouldBe true
        val order = if (element % 2 == 0) readers.reverse else readers
        order.foreach(reader => buffer.read(reader) shouldBe element)
        buffer.isEmpty shouldBe true
        readers.map(_.reads).sum should be <= (4 * readers.size)
      }
    }

    "track the oldest readers across joining, removal, growth and wraparound" in {
      def newCursor(): Cursor = new Cursor { var cursor = 0 }
      val fast = newCursor()
      val slow = newCursor()
      var readers = List(fast, slow)
      val buffer = new TestBuffer(2, 4, new Cursors { def cursors = readers })

      buffer.write(1) shouldBe true
      buffer.write(2) shouldBe true
      buffer.read(fast) shouldBe 1
      buffer.read(fast) shouldBe 2

      val joining = newCursor()
      readers ::= joining
      buffer.initCursor(joining)
      readers = readers.filterNot(_ eq slow)
      buffer.onCursorRemoved(slow)
      buffer.size shouldBe 2
      buffer.write(3) shouldBe true // grow while two readers share the oldest position
      buffer.read(fast) shouldBe 3
      buffer.read(joining) shouldBe 1
      buffer.size shouldBe 2

      val late = newCursor()
      readers ::= late
      buffer.initCursor(late)
      buffer.read(joining) shouldBe 2
      readers = readers.filterNot(_ eq joining)
      buffer.onCursorRemoved(joining)
      buffer.size shouldBe 2
      buffer.read(late) shouldBe 2
      buffer.size shouldBe 1
      buffer.read(late) shouldBe 3
      buffer.isEmpty shouldBe true
      readers = readers.filterNot(_ eq late)
      buffer.onCursorRemoved(late)

      for (element <- 4 to 12) {
        buffer.write(element) shouldBe true
        buffer.read(fast) shouldBe element
        buffer.isEmpty shouldBe true
      }
      buffer.write(13) shouldBe true
      readers = Nil
      buffer.onCursorRemoved(fast)
      buffer.isEmpty shouldBe true
      buffer.inspect should startWith("0 0 0 0 ")

      readers = List(late)
      buffer.initCursor(late)
      buffer.write(14) shouldBe true
      buffer.read(late) shouldBe 14
      buffer.isEmpty shouldBe true
    }

    "pass the stress test" in {
      // create 100 buffers with an initialSize of 1 and a maxSize of 1 to 64,
      // for each one attach 1 to 8 cursors and randomly try reading and writing to the buffer;
      // in total 200 elements need to be written to the buffer and read in the correct order by each cursor
      val MAXSIZEBIT_LIMIT = 6 // 2 ^ (this number)
      val COUNTER_LIMIT = 200
      val LOG = false
      val sb = new java.lang.StringBuilder
      def log(s: => String): Unit = if (LOG) sb.append(s)

      class StressTestCursor(cursorNr: Int, run: Int) extends Cursor {
        var cursor: Int = _
        var counter = 1
        def tryReadAndReturnTrueIfDone(buf: TestBuffer): Boolean = {
          log(s"  Try reading of $toString: ")
          try {
            val x = buf.read(this)
            log("OK\n")
            if (x != counter)
              fail(s"""|Run $run, cursorNr $cursorNr, counter $counter: got unexpected $x
                         |  Buf: ${buf.inspect}
                         |  Cursors: ${buf.cursors.cursors.mkString("\n           ")}
                         |Log:\n$sb
                      """.stripMargin)
            counter += 1
            counter == COUNTER_LIMIT
          } catch {
            case NothingToReadException => log("FAILED\n"); false // ok, we currently can't read, try again later
          }
        }
        override def toString: String = s"cursorNr $cursorNr, ix $cursor, counter $counter"
      }

      val random = new Random
      for {
        bit <- 1 to MAXSIZEBIT_LIMIT
        _ <- 1 to 2
      } {
        var counter = 1
        var activeCursors = List.tabulate(random.nextInt(8) + 1)(new StressTestCursor(_, 1 << bit))
        var stillWriting = 2 // give writing a slight bias, so as to somewhat "stretch" the buffer
        val buf = new TestBuffer(1, 1 << bit, new Cursors { def cursors = activeCursors })
        sb.setLength(0)
        while (activeCursors.nonEmpty) {
          log(s"Buf: ${buf.inspect}\n")
          val activeCursorCount = activeCursors.size
          val index = random.nextInt(activeCursorCount + stillWriting)
          if (index >= activeCursorCount) {
            log(s"  Writing $counter: ")
            if (buf.write(counter)) {
              log("OK\n")
              counter += 1
            } else {
              log("FAILED\n")
              if (counter == COUNTER_LIMIT) stillWriting = 0
            }
          } else {
            val cursor = activeCursors(index)
            if (cursor.tryReadAndReturnTrueIfDone(buf)) {
              activeCursors = activeCursors.filter(_ != cursor)
              buf.onCursorRemoved(cursor)
            }
          }
        }
      }
    }
  }

  class TestBuffer(iSize: Int, mSize: Int, cursors: Cursors)
      extends ResizableMultiReaderRingBuffer[Int](iSize, mSize, cursors) {
    def inspect: String =
      underlyingArray.map(x => if (x == null) 0 else x).mkString("", " ", " " + toString.dropWhile(_ != '('))
  }

  class Test(iSize: Int, mSize: Int, cursorCount: Int)
      extends TestBuffer(iSize, mSize, new SimpleCursors(cursorCount)) {
    def read(cursorIx: Int): Integer =
      try read(cursors.cursors(cursorIx))
      catch { case NothingToReadException => null }
  }

  class SimpleCursors(cursorCount: Int) extends Cursors {
    val cursors: List[Cursor] = List.fill(cursorCount)(new Cursor { var cursor: Int = _ })
  }
}
