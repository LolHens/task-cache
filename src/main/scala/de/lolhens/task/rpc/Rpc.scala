package de.lolhens.task.rpc

import monix.eval.Task

case class Rpc[A](socket: RpcSocket)(task: Task[A]) {
  def local: Task[A] = task
  def remote: Task[A] = ???


}
