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

import akka.actor.ActorRef
import java.util.concurrent.{ScheduledExecutorService, TimeUnit, ScheduledFuture}
import scala.collection.mutable
import hexlab.morf.util.SchedulerScope._

/**
 * This class ...
 *
 * @author hex1r0
 */
trait SchedulerScope extends Serializable {

  @transient val tasks = new mutable.HashMap[TaskId, ScheduledFuture[_]]

  def scheduler: ScheduledExecutorService
  def executor: ActorRef

  def task(taskId: TaskId) = {
    tasks.get(taskId)
  }

  def hasTask(taskId: TaskId) = {
    task(taskId).exists(t => !t.isCancelled && !t.isDone)
  }

  def schedule(taskId: TaskId, task: TaskWithId, timeout: Long) {
    val r = scheduler.schedule((taskId, task), timeout, TimeUnit.MILLISECONDS)
    tasks += taskId -> r
  }

  def schedule(taskId: TaskId, task: TaskWithId, timeout: Long, period: Long) {
    val r = scheduler.scheduleAtFixedRate((taskId, task), timeout, period, TimeUnit.MILLISECONDS)
    if (hasTask(taskId)) {
      throw new IllegalStateException(taskId + " already exists")
    }

    tasks += taskId -> r
  }

  def schedule(taskId: TaskId, task: Task, timeout: Long) {
    val r = scheduler.schedule(task, timeout, TimeUnit.MILLISECONDS)
    tasks += taskId -> r
  }

  def schedule(taskId: TaskId, task: Task, timeout: Long, period: Long) {
    val r = scheduler.scheduleAtFixedRate(task, timeout, period, TimeUnit.MILLISECONDS)
    if (hasTask(taskId)) {
      throw new IllegalStateException(taskId + " already exists")
    }

    tasks += taskId -> r
  }

  def cancelTask(taskId: TaskId) = for (t <- task(taskId)) yield t.cancel(false)

  def cancelAndRemoveTask(taskId: TaskId) = {
    cancelTask(taskId)
    tasks.remove(taskId)
  }

  private implicit def task2Runnable(t: Task): Runnable = new Runnable {
    def run(): Unit = executor ! RunScheduledTask(t)
  }

  private implicit def taskWithId2Runnable(t: (TaskId, TaskWithId)): Runnable = new Runnable {
    def run(): Unit = executor ! RunScheduledTaskWithId(t._1, t._2)
  }
}

object SchedulerScope {
  type TaskId = String
  type Task = () => Unit
  type TaskWithId = (TaskId) => Unit

  case class RunScheduledTask(task: Task)
  case class RunScheduledTaskWithId(taskId: TaskId, task: TaskWithId)
}
