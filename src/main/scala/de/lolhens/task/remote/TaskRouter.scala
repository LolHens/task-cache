package de.lolhens.task.remote

import de.lolhens.task.remote.TaskRouter.{LocalTaskHandle, RemoteTaskHandle}
import monix.eval.Task
import monix.execution.atomic.{Atomic, AtomicLong}

class TaskRouter {
  private val nextId = Atomic(0L)

  def localHandle[A](task: Task[A]): LocalTaskHandle[A] = {
    LocalTaskHandle(nextId.getAndIncrement(), task)
  }

  def remoteHandle[A](id: Long): RemoteTaskHandle[A] = ???
}

object TaskRouter {
  case class LocalTaskHandle[A](id: Long, task: Task[A])

  case class RemoteTaskHandle[A](id: Long)
}
