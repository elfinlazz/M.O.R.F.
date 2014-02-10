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
import akka.actor.{IO, Actor}
import akka.util.ByteString
import java.net.SocketAddress
import org.slf4j.LoggerFactory

/**
 * @author hex1r0
 */
abstract class MMOClientConnection(socket: SocketHandle) extends Actor
/*                                                       */ with ClientPacketParser {
  val log = LoggerFactory.getLogger("hexlab.morf.network.client")
  val packetLog = LoggerFactory.getLogger("hexlab.morf.network.client.packet")
  val rawLog = LoggerFactory.getLogger("hexlab.morf.network.client.raw")
  val input = IO.IterateeRef.sync(parseCP)

  def receive: Actor.Receive = {
    case IO.Connected(s, a) =>
      onConnected(s, a)
    case IO.Closed(h, c) =>
      onClosed(h, c)
    case IO.Close(h) =>
      socket.close()
    case bytes: ByteString =>
      socket.write(onReadyWrite(bytes))
    case IO.Read(_, bytes) =>
      input(IO.Chunk(onReadyRead(bytes)))
      //input.flatMap(_ => parseCP)
  }

  def onConnected(socket: IO.SocketHandle, addr: SocketAddress) {}

  def onClosed(handle: IO.Handle, cause: IO.Input) {}

  def onReadyRead(bytes: ByteString) = bytes

  def onReadyWrite(bytes: ByteString) = bytes
}
