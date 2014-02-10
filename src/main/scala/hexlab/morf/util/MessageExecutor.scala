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

import akka.actor.{ActorSystem, ActorRef, Actor}
import java.util.concurrent.ScheduledExecutorService
import scala.collection.immutable.Queue
import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}
import scala.reflect.ClassTag
import hexlab.morf.util.MessageExecutor._
import hexlab.morf.util.SchedulerScope._

/**
 * This class ...
 *
 * @author hex1r0
 */
object MessageExecutor {

  type MessageFunc[T] = (T) => Any
  type SuccessFunc[T] = (T) => Unit
  type FailureFunc = (Throwable) => Unit
  type MessageFuncList = Queue[(MessageHandler, MessageFunc[_])]
  type ListenerMap = immutable.HashMap[Class[_], MessageFuncList]

  case class CreateHandler[T <: MessageHandler](clazz: Class[T], params: List[(Class[_], AnyRef)])
  case class SendRequest(target: ActorRef, arg: AnyRef, onSuccess: SuccessFunc[AnyRef], onFailure: FailureFunc)
  case class DoRequest(id: Int, message: AnyRef)
  case class RequestSucceed(id: Int, result: AnyRef)
  case class RequestFailed(id: Int, e: Throwable)
  case class NewExecutorTransactionStart(newExecutor: ActorRef)
  case class WaitForTasksBeforeExecutorTransactionEnd(actor: ActorRef, newExecutor: ActorRef)
  case class NewExecutorTransactionEnd(newExecutor: ActorRef)

  val EmptyMessageFuncList: MessageFuncList = Queue.empty

  def unhandledMessage(m: AnyRef) {
    Log[MessageExecutor.type].warn("Unhandled message = " + m.getClass)
  }

  def call(f: MessageFunc[_]): MessageFunc[AnyRef] = f.asInstanceOf[MessageFunc[AnyRef]]

  implicit class MessageFuncListExt(val list: MessageFuncList) extends AnyVal {
    def calleach(m: AnyRef) {
      list foreach {
        case (_, func) =>
          try call(func)(m)
          catch {
            case e: Throwable => defaultOnFailure(e)
          }
      }
    }
  }

  implicit class ListenerMapExt(val map: ListenerMap) extends AnyVal {
    def resolve(m: AnyRef): MessageFuncList = {
      map.getOrElse(m.getClass, EmptyMessageFuncList)
    }

    def bind[T: ClassTag](f: MessageFunc[T]): ListenerMap = bind(null, f)

    def bind[T: ClassTag](h: MessageHandler, f: MessageFunc[T]): ListenerMap = {
      val clazz = Erasure[T]

      var list = map.getOrElse(clazz, Queue.empty)
      list = list enqueue(h, f)
      map.updated(clazz, list)
    }
  }

  abstract class MessageHandler extends ConfigScope with DatapackScope with SchedulerScope {

    implicit val messageHandlerSelf: MessageHandler = this

    private[MessageExecutor] var _actorSystem: ActorSystem = _
    private[MessageExecutor] var _executor: ActorRef = _
    private[MessageExecutor] var _scheduler: ScheduledExecutorService = _
    private[MessageExecutor] var _listeners: ListenerMap = _

    private[MessageExecutor] var _activeMessage: AnyRef = _

    def activeMessage = _activeMessage
    def actorSystem: ActorSystem = _actorSystem
    def executor: ActorRef = _executor
    def scheduler: ScheduledExecutorService = _scheduler

    def init()

    protected final def bind[T: ClassTag](f: MessageFunc[T]) {
      _listeners = _listeners bind(this, f)

      _listeners.values foreach (_.foreach {
        case (h, _) => h._listeners = _listeners
      })
    }

    final def fire(m: AnyRef) {
      _listeners resolve m calleach m
    }

    protected final def request[T](target: ActorRef, arg: AnyRef)
                                  (onSuccess: SuccessFunc[T], onFailure: FailureFunc = defaultOnFailure) {
      executor ! SendRequest(target, arg, onSuccess.asInstanceOf[SuccessFunc[AnyRef]], onFailure)
    }
  }

  private def defaultOnFailure(e: Throwable) {
    val st = e.getStackTrace
    e.setStackTrace(st.take(st.length - 11))
    Log[MessageHandler].error("", e)
  }
}

// TODO probably need separate map of handlers
class MessageExecutor(scheduler: ScheduledExecutorService) extends Actor {

  import MessageExecutor._

  private val _listeners = new ListenerMap
  private val _requests = new mutable.HashMap[Int, (SuccessFunc[AnyRef], FailureFunc)]
  private var _nextRequestId = 0

  override def receive: Actor.Receive = {
    case CreateHandler(clazz, params) =>
      val paramTypes = params map (_._1)
      val paramValues = params map (_._2)
      val inst = clazz.getConstructor(paramTypes: _*).newInstance(paramValues: _*)
      inst._actorSystem = context.system
      inst._executor = self
      inst._scheduler = scheduler
      inst._listeners = _listeners
      inst.init()

    case SendRequest(target, arg, onSuccess, onFailure) =>
      _nextRequestId += 1
      _requests += _nextRequestId ->(onSuccess, onFailure)

      target ! DoRequest(_nextRequestId, arg)

    case RequestSucceed(id, result) =>
      _requests.remove(id) foreach { case (success, _) => success(result) }

    case RequestFailed(id, e) =>
      _requests.remove(id) foreach { case (_, failure) => failure(e) }

    case RunScheduledTask(task) =>
      try task()
      catch {
        case e: Throwable => defaultOnFailure(e)
      }

    case RunScheduledTaskWithId(taskId, task) =>
      try task(taskId)
      catch {
        case e: Throwable => defaultOnFailure(e)
      }

    case DoRequest(id, m) =>
      val list = _listeners resolve m
      if (list.isEmpty) {
        try unhandledMessage(m)
        catch {
          case e: Throwable => defaultOnFailure(e)
        }
      } else {
        list foreach {
          case (_, func) =>
            try {
              call(func)(m) match {
                case v: AnyRef => sender ! RequestSucceed(id, v)
                case _ =>
              }
            } catch {
              case e: Throwable => sender ! RequestFailed(id, e)
            }
        }
      }

    case WaitForTasksBeforeExecutorTransactionEnd(actor, newExecutor) =>
      actor ! NewExecutorTransactionEnd(newExecutor)

    // this case should be the last
    case m: AnyRef =>
      val list = _listeners resolve m
      if (list.isEmpty) {
        try unhandledMessage(m)
        catch {
          case e: Throwable => defaultOnFailure(e)
        }
      } else {
        list calleach m
      }
  }
}

trait Executable {
  private var _enquenedMessages = new ArrayBuffer[AnyRef]
  private var _execute: (AnyRef) => Unit = push
  protected var _executor: ActorRef = _

  final def transactionHandler: Actor.Receive = {
    case NewExecutorTransactionStart(newExecutor) =>
      if (_executor == null) {
        _executor = newExecutor
      } else {
        _execute = postpone
        _executor ! WaitForTasksBeforeExecutorTransactionEnd(selfRef, newExecutor)
      }

    case NewExecutorTransactionEnd(newExecutor) =>
      _executor = newExecutor
      _execute = push

      for (x <- onTransactionEnd()) yield _enquenedMessages.prepend(x)

      _enquenedMessages foreach execute
      _enquenedMessages.clear()
  }

  final protected def execute = _execute

  private def push(m: AnyRef) {
    _executor ! m
  }

  private def postpone(m: AnyRef) {
    _enquenedMessages += m
  }

  def selfRef: ActorRef

  def onTransactionEnd(): Option[AnyRef]
}

