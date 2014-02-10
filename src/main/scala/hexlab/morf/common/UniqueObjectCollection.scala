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

package hexlab.morf.common

import java.lang.IllegalArgumentException
import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * This class ...
 *
 * @author hex1r0
 */
trait UniqueObjectCollection[U <: Uid[_], O <: UniqueObject[U]] extends Serializable {
  protected val objects = new mutable.HashMap[U, O]()

  @inline
  def install(obj: O) {
    if (objects.contains(obj.uid.asInstanceOf[U])) {
      throw new IllegalArgumentException(obj.uid + " already exists")
    }

    objects += obj.uid.asInstanceOf[U] -> obj
  }

  @inline
  def remove(obj: O) = objects.remove(obj.uid)

  @inline
  def foreach[T: ClassTag](f: (T) => Unit): Unit = {
    objects.values.foreach {
      case c: T => f(c)
      case _ =>
    }
  }

  @inline
  def filter[T: ClassTag](f: (T) => Boolean): Iterable[T] = {
    objects.values.view.filter {
      case c: T => f(c)
      case _ => false
    }.map(_.asInstanceOf[T])
  }

  @inline
  def find[T: ClassTag](f: (T) => Boolean): Option[T] = {
    objects.values.view.find {
      case c: T => f(c)
      case _ => false
    }.map(_.asInstanceOf[T])
  }
}
