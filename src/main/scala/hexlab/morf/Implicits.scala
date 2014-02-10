/*
 * This file is part of M.O.R.F.
 *                      <https://github.com/HeXLaB/M.O.R.F.>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2013-2014
 *               HeXLaB Team
 *                           All rights reserved
 */

package hexlab.morf

import akka.util.ByteString
import java.math.BigInteger
import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID
import scala.Array
import hexlab.morf.util.HexUtil
import hexlab.morf.util.ByteArray.ByteArray
import hexlab.morf.util.SchedulerScope.TaskId

object Implicits {
  implicit def Func2Runnable(f: () => Unit) = new Runnable() {
    def run(): Unit = f()
  }

  implicit def String2TaskId(v: String) = new TaskId(v)

  implicit def Boolean2Byte(v: Boolean): Byte = if (v) 1 else 0

  implicit class BigIntegerExt(bi: BigInteger) {
    def toUByteArray = {
      val bytes = bi.toByteArray
      if (bytes(0) == 0) {
        java.util.Arrays.copyOfRange(bytes, 1, bytes.length)
      }
      else {
        bytes
      }
    }
  }

  implicit class ByteArrayExt(array: Array[Byte]) {
    def toHex = {
      HexUtil.toHexString(array)
    }
  }

  implicit def UUID2ByteArray(uuid: UUID): ByteArray = uuid.toByteArray

  implicit def UUID2IndexedSeqByte(uuid: UUID): IndexedSeq[Byte] = uuid.toByteArray

  implicit class UUIDExt(uuid: UUID) {
    def toByteArray: ByteArray = {
      val bb = ByteBuffer.allocate(16)

      def reverse(n: Int) {
        val tmp = new Array[Byte](n)
        val prev = bb.position
        bb.get(tmp)
        bb.position(prev)
        bb.put(tmp.reverse)
      }

      bb.order(ByteOrder.BIG_ENDIAN)
      bb.putLong(uuid.getMostSignificantBits)
      bb.putLong(uuid.getLeastSignificantBits)
      bb.flip
      reverse(4)
      reverse(2)
      reverse(2)

      bb.array()
    }
  }

  object GUID {
    def apply(bytes: IndexedSeq[Byte]): UUID = apply(bytes.toArray)

    def apply(bytes: ByteArray): UUID = {
      val bb = ByteString(bytes)
      val it = bb.iterator

      def reverse(n: Int) = {
        val tmp = new Array[Byte](n)
        it.getBytes(tmp)
        tmp.reverse
      }

      val msb = ByteString(reverse(4) ++ reverse(2) ++ reverse(2)).iterator.getLong(ByteOrder.BIG_ENDIAN)
      val lsb = it.getLong(ByteOrder.BIG_ENDIAN)

      new UUID(msb, lsb)
    }
  }

}