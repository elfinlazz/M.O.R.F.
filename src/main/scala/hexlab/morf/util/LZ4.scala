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

package hexlab.morf.util

import akka.util.ByteString
import java.nio.ByteOrder
import hexlab.morf.util.ByteArray.ByteArray
import net.jpountz.lz4.LZ4Factory

/**
 * This class ...
 *
 * @author hex1r0
 */
class LZ4 {
  private val compressor = LZ4Factory.fastestJavaInstance().fastCompressor()
  private implicit val endian = ByteOrder.LITTLE_ENDIAN

  def comress(bytes: ByteArray) = {
    val compressed = compressor.compress(bytes)
    ByteString.newBuilder
      .putShort(compressed.length)
      .putShort(bytes.length)
      .putBytes(compressed)
      .result()
  }
}
