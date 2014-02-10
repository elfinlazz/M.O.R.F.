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

package hexlab.morf.network

import akka.actor.IO.SocketHandle
import akka.actor._
import java.net.InetSocketAddress
import scala.collection.mutable

/**
 * @author hex1r0
 */
abstract class MMOServer[T <: MMOClientConnection](port: Int) extends Actor with ActorLogging {
  private val _connections = new mutable.HashMap[IO.Handle, ActorRef]

  override def preStart() {
    IOManager(context.system) listen new InetSocketAddress(port)
  }

  def receive = {
    case IO.NewClient(server) =>
      val socket = server.accept()
      val aref = newActorRef(context, socket)
      _connections += socket -> aref
      //log.debug("Connection from " + serverSocket.getInetAddress().getHostName())
      log.debug("New connection") // FIXME

    case IO.Read(socket, bytes) =>
      _connections(socket) ! IO.Read(socket, bytes)
      //log.debug("Read %d bytes" format bytes.length)

    case IO.Closed(socket, cause) =>
      val aref = _connections(socket)
      context stop aref
      _connections -= socket

      log.debug("Connection closed") // FIXME
  }

  def newActorRef(context: ActorContext, socket: SocketHandle): ActorRef = {
    null
  }
}
