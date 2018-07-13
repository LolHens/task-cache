package de.lolhens.task.remote

import de.lolhens.task.remote.RpcRouter.Action.{Cancel, Result, Sync}
import de.lolhens.task.remote.RpcRouter.{Action, Id}
import monix.eval.Task
import monix.execution.{Cancelable, CancelableFuture}

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.util.Try

class RpcRouter {
  var r: RpcRouter = null


  def send(action: Action): Task[Unit] = {
    r.receive(action)
  }

  def receive(action: Action): Task[Unit] = action match {
    case Sync(id) =>
      println(handlers)
      handlers.get(id).map {handler =>
        Task.deferAction { scheduler => Task {
          val promise = Promise[Any]()
          val cancelableFuture = CancelableFuture(promise.future, Cancelable { () =>
            send(Cancel(id)).runAsync(scheduler)
          })
          remoteFutures = remoteFutures + (id -> promise)
          handler(cancelableFuture)
        }}
      }.get//.getOrElse(Task.unit)

    case Result(id, result) =>
      Task{
        remoteFutures(id).asInstanceOf[Promise[Any]].complete(result)
      }

    case Cancel(id) =>
      Task(localFutures(id).cancel())
  }

  private var handlers: Map[Id, CancelableFuture[_] => Unit] = Map.empty
  private var remoteFutures: Map[Id, Promise[_]] = Map.empty
  private var localFutures: Map[Id, CancelableFuture[_]] = Map.empty

  def sendFuture[A](future: CancelableFuture[A]): Task[Id] = Task.deferAction { implicit scheduler =>
    val id = 0L
    localFutures = localFutures + (id -> future)
    send(Sync(id)).flatMap { _ =>
      future.onComplete(r => send(Result(id, r)).runAsync)
      Task.now(id)
    }
  }

  def putHandler[A](id: Id, handler: CancelableFuture[A] => Unit): Task[Unit] =
    Task{
      handlers = handlers + (id -> handler.asInstanceOf[CancelableFuture[Any] => Unit])
    }
}

object RpcRouter {
  type Id = Long

  trait Action {
    def id: Id
  }

  object Action {
    case class Sync(id: Id) extends Action
    case class Result[A](id: Id, result: Try[A]) extends Action
    case class Cancel(id: Id) extends Action
  }

  def main(args: Array[String]): Unit = {
    val server = new RpcRouter
    val client = new RpcRouter
    server.r = client
    client.r = server
    import monix.execution.Scheduler.Implicits.global
    val f = CancelableFuture(Future(4), Cancelable())
    client.putHandler[Int](0L, {future =>
      future.foreach(println)
    }).runSyncUnsafe(Duration.Inf)
    server.sendFuture(f).runSyncUnsafe(Duration.Inf)
    Thread.sleep(5000)
  }
}
