package de.lolhens.task.remote

import de.lolhens.task.pickling.Pickler
import de.lolhens.task.remote.RpcRouter.Action._
import de.lolhens.task.remote.RpcRouter.{Action, ClientTaskId, Id, ServerTaskId}
import monix.eval.Task
import monix.execution.atomic.Atomic
import monix.execution.{Cancelable, CancelableFuture, Scheduler}

import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.util.Try

class RpcRouter {
  var r: RpcRouter = null

  object server {
    private val cancelables = Atomic(Map.empty[Id, Cancelable])

    def run(taskId: String, id: Id): Task[Unit] = Task.deferAction(scheduler => Task {
      val future = server.runTask(taskId)(scheduler)
      future.onComplete { result =>
        send(Result(id, result)).runAsync(scheduler)
      }(scheduler)
      val entry = id -> future
      server.cancelables.transform(_ + entry)
    })

    def cancel(id: Id): Task[Unit] = Task {
      server.cancelables.get.get(id).foreach { cancelable =>
        cancelable.cancel()
        server.cancelables.transform(_ - id)
      }
    }

    def serverTaskId[A](taskId: String): ServerTaskId[A] = {
      ???
    }

    def runTask(taskId: String)(scheduler: Scheduler): CancelableFuture[_] = {
      serverTaskId(taskId).task.runAsync(scheduler)
    }
  }

  object client {
    private val nextId = Atomic(0L)

    private def getNextId: Id = nextId.getAndIncrement()

    private val promises = Atomic(Map.empty[Id, Promise[_]])

    def result(id: Id, result: Try[_]): Task[Unit] = Task {
      promises.get.get(id).foreach { promise =>
        promise.asInstanceOf[Promise[Any]].tryComplete(result)
        promises.transform(_ - id)
      }
    }

    def task[A](taskId: ClientTaskId[A]): Task[A] = Task.deferFutureAction { scheduler =>
      val id: Id = getNextId
      val cancelable = Cancelable { () =>
        send(Cancel(id)).runAsync(scheduler)
      }
      val promise = Promise[A]()
      val entry = id -> promise
      promises.transform(_ + entry)
      val future = CancelableFuture(promise.future, cancelable)
      send(Run(taskId.id, id)).runAsync(scheduler)
      future
    }
  }


  def sendString(string: String)(implicit pickler: Pickler[Action[_]]): Task[Unit] =
    r.receiveString(string)(pickler)

  def send(action: Action[_]): Task[Unit] = {
    import de.lolhens.task.pickling.objectOutputStreamOps._
    for {
      string <- implicitly[Pickler[Action[_]]].pickle(action)
      _ <- sendString(string)
    } yield ()
  }

  def receiveString(string: String)(implicit pickler: Pickler[Action[_]]): Task[Unit] =
    for {
      action <- pickler.unpickle(string)
      _ <- receive(action)
    } yield ()

  def receive(action: Action[_]): Task[Unit] = action match {
    case Run(taskId, id) =>
      server.run(taskId, id)

    case Cancel(id) =>
      server.cancel(id)

    case Result(id, result) =>
      client.result(id, result)
  }
}

object RpcRouter {
  type Id = Long

  case class SerializedAction[A](taskId: String, action: Action[A])

  trait Action[A] {
    def id: Id
  }

  object Action {

    case class Run(taskId: String, id: Id) extends Action[Nothing]

    case class Result[A](id: Id, result: Try[A]) extends Action[A]

    case class Cancel(id: Id) extends Action[Nothing]

  }

  case class ClientTaskId[A](id: String)(implicit pickler: Pickler[Action[A]]) {
    def task(task: Task[A]): ServerTaskId[A] = new ServerTaskId[A](id)(task)
  }

  class ServerTaskId[A](id: String)(val task: Task[A])(implicit pickler: Pickler[Action[A]]) extends ClientTaskId[A](id)

  def main(args: Array[String]): Unit = {
    val server = new RpcRouter
    val client = new RpcRouter
    server.r = client
    client.r = server
    import de.lolhens.task.pickling.objectOutputStreamOps._
    import monix.execution.Scheduler.Implicits.global
    val task = client.client.task(ClientTaskId[Int]("test"))
    for (i <- 0 until 10)
      println("a: " + task.runSyncUnsafe(Duration.Inf))
  }
}
