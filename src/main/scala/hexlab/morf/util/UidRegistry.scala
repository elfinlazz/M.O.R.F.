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

import java.util

/**
 * This class ...
 *
 * @author hex1r0
 */
class UidRegistry(capacity: Int) {
  private val _ids = new util.BitSet()
  _ids.set(0)

  private var _nextMinUid = 1

  def acquire() = {
    var uid = 0
    if (_nextMinUid == capacity)
      uid = capacity
    else
      uid = _ids.nextClearBit(_nextMinUid)

    if (uid == capacity) throw new IllegalStateException

    _nextMinUid = uid + 1
    _ids.set(uid)
    
    uid
  }

  def release(uid: Int) = {
    if (!isTaken(uid)) throw new IllegalStateException

    tryRelease(uid)
  }

  def tryRelease(uid: Int) = {
    _ids.clear(uid)
    if (uid < _nextMinUid || _nextMinUid == capacity)
      _nextMinUid = uid
  }

  def lock(uid: Int) {
    val status = _ids.get(uid)
    if (status) throw new IllegalStateException

    _ids.set(uid)
  }
  
  def isTaken(uid: Int) = _ids.get(uid)
}
