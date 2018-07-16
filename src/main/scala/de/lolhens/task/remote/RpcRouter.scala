package de.lolhens.task.remote

import de.lolhens.task.pickling.Pickler
import de.lolhens.task.remote.RpcRouter.Action._
import de.lolhens.task.remote.RpcRouter.{Action, Id}
import monix.eval.Task
import monix.execution.atomic.Atomic
import monix.execution.{Cancelable, CancelableFuture, Scheduler}

import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.util.Try

class RpcRouter {
  var r: RpcRouter = null

  def sendString(string: String)(implicit pickler: Pickler[Action]): Task[Unit] =
    r.receiveString(string)(pickler)

  def send(action: Action): Task[Unit] = {
    import de.lolhens.task.pickling.objectOutputStreamOps._
    for {
      string <- implicitly[Pickler[Action]].pickle(action)
      _ <- sendString(string)
    } yield ()
  }

  def receiveString(string: String)(implicit pickler: Pickler[Action]): Task[Unit] =
    for {
      action <- pickler.unpickle(string)
      _ <- receive(action)
    } yield ()

  def receive(action: Action): Task[Unit] = action match {
    case Run(name, id) => Task.deferAction(scheduler => Task {
      val future = runTask(name)(scheduler)
      future.onComplete { result =>
        send(Result(id, result)).runAsync(scheduler)
      }(scheduler)
      val entry = id -> future
      cancelables.transform(_ + entry)
    })

    case Cancel(id) => Task {
      cancelables.get.get(id).foreach { cancelable =>
        cancelable.cancel()
        cancelables.transform(_ - id)
      }
    }

    case Result(id, result) => Task {
      promises.get.get(id).foreach { promise =>
        promise.asInstanceOf[Promise[Any]].tryComplete(result)
        promises.transform(_ - id)
      }
    }
  }

  private val cancelables: Atomic[Map[Id, Cancelable]] = Atomic(Map.empty[Id, Cancelable])
  private val promises: Atomic[Map[Id, Promise[_]]] = Atomic(Map.empty[Id, Promise[_]])

  private def runTask(name: String)(scheduler: Scheduler): CancelableFuture[_] = {
    Task {
      println("test")
      5
    }.runAsync(Scheduler.global)
  }

  def task[A](name: String): Task[A] = Task.deferFutureAction { scheduler =>
    val id: Id = 0
    val cancelable = Cancelable { () =>
      send(Cancel(id)).runAsync(scheduler)
    }
    val promise = Promise[A]()
    val entry = id -> promise
    promises.transform(_ + entry)
    val future = CancelableFuture(promise.future, cancelable)
    send(Run(name, id)).runAsync(scheduler)
    future
  }
}

object RpcRouter {
  type Id = Long

  trait Action {
    def id: Id
  }

  object Action {

    case class Run(name: String, id: Id) extends Action

    case class Result[A](id: Id, result: Try[A]) extends Action

    case class Cancel(id: Id) extends Action

  }

  trait TaskID[A] {
    def id: String
  }

  case class ServerTaskID[A](id: String)(task: Task[A]) extends TaskID[A]

  def main(args: Array[String]): Unit = {
    val server = new RpcRouter
    val client = new RpcRouter
    server.r = client
    client.r = server
    import monix.execution.Scheduler.Implicits.global
    val task = client.task[Int]("test")
    for (i <- 0 until 10)
      println("a: " + task.runSyncUnsafe(Duration.Inf))
  }
}
