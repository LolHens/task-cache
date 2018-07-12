package de.lolhens.task.rpc

import de.lolhens.task.rpc.RpcSocket.ActiveLocalRpc
import monix.execution.atomic.Atomic

import scala.concurrent.Future

case class RpcSocket[A](name: String)(implicit val registry: RpcRegistry) {
  registry.add(this)

  private val activeLocalRpcs: Atomic[Map[String, ActiveLocalRpc]] = Atomic(Map.empty[String, ActiveLocalRpc])
  private val activeRemoteRpcs: Atomic[Map[String, ActiveLocalRpc]] = Atomic(Map.empty[String, ActiveLocalRpc])

  def addActiveLocalRpc(activeLocalRpc: ActiveLocalRpc): Unit = {
    val entry = activeLocalRpc.id -> activeLocalRpc
    activeLocalRpcs.transform(_ + entry)
  }
}

object RpcSocket {
  class ActiveRpc(val id: String)

  class ActiveLocalRpc() extends ActiveRpc("")

  class ActiveRemoteRpc() extends ActiveRpc("") {
    val future: Future[_]
  }
}
