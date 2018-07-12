package de.lolhens.task.rpc

import monix.execution.atomic.Atomic

abstract class RpcRegistry {
  protected implicit def registry: RpcRegistry = this

  private val sockets: Atomic[Map[String, RpcSocket[_]]] = Atomic(Map.empty[String, RpcSocket[_]])

  def add(socket: RpcSocket[_]): Unit = {
    val entry = socket.name -> socket
    sockets.transform(_ + entry)
  }

  def socket(name: String): Option[RpcSocket[_]] = sockets.get.get(name)
}
