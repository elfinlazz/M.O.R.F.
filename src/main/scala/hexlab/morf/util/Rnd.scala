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

import java.util.concurrent.atomic.AtomicLong
import org.apache.commons.math3.random.MersenneTwister
import org.apache.commons.math3.random.RandomGenerator

/**
 * This class ...
 *
 * @author hex1r0
 */
/**
 * @author hex1r0
 */
object Rnd {

  /**
   * @return random number from 0 to 1
   */
  def get(): Float = rnd().nextFloat()

  /**
   * Gets a random number from 0(inclusive) to n(exclusive)
   *
   * @param n
   *            The superior limit (exclusive)
   * @return A number from 0 to n-1
   */
  def get(n: Int): Int = rnd().nextInt(n)

  def get(n: Long): Long = (rnd().nextDouble() * n).toLong

  /**
   * Get random number from min to max (not max-1)
   *
   * @param min
   * @param max
   * @return number from min to max (not max-1)
   */
  def get(min: Int, max: Int): Int = min + scala.math.floor(rnd().nextDouble() * (max - min + 1)).toInt

  /**
   * Get random number from min to max (not max-1)
   *
   * @param min
   * @param max
   * @return number from min to max (not max-1)
   */
  def get(min: Long, max: Long): Long = min + scala.math.floor(rnd().nextDouble() * (max - min + 1)).toLong

  /**
   * Get random number from min to max (not max-1)
   *
   * @param min
   * @param max
   * @return number from min to max (not max-1)
   */
  def get(min: Float, max: Float): Float = min + scala.math.floor(rnd().nextDouble() * (max - min + 1)).toFloat

  /**
   * Get random number from min to max (not max-1)
   *
   * @param min
   * @param max
   * @return number from min to max (not max-1)
   */
  def get(min: Double, max: Double): Double = min + scala.math.floor(rnd().nextDouble() * (max - min + 1))

  def get(out: Array[Byte]) { rnd().nextBytes(out) }

  def getBytes(n: Int) = {
    val bytes = new Array[Byte](n)
    rnd().nextBytes(bytes)
    bytes
  }

  def nextInt(): Int = rnd().nextInt()
  def nextDouble(): Double = rnd().nextDouble()
  def nextGaussian(): Double = rnd().nextGaussian()
  def nextBoolean(): Boolean = rnd().nextBoolean()

  private val _seedUniquifier: AtomicLong = new AtomicLong(8682522807148012L)
  private val _rnd: ThreadLocal[RandomGenerator] = new ThreadLocal[RandomGenerator] {
    override def initialValue(): RandomGenerator = new MersenneTwister(_seedUniquifier.getAndIncrement + Sys.nanos)
  }

  private def rnd(): RandomGenerator = _rnd.get()

}
