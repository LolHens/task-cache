package de.lolhens.task

import de.lolhens.task.TaskKey.ExecutionManager.{LocalTask, RemoteTask}
import de.lolhens.task.TaskKey.{KeyedTask, Router}
import de.lolhens.task.pickling.TaskPickler
import de.lolhens.task.pickling.TaskPickler._
import monix.eval.Task
import shapeless._

import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.util.Try

case class TaskKey[A, H <: HList](name: String)
                                 (val create: H => Task[A])
                                 (implicit val pickler: TaskPickler[H], router: Router) {
  router.add(this)

  def key(task: Task[A])(valuesTask: Task[H]): KeyedTask[A, H] = KeyedTask(this)(task, valuesTask)

  def unpickleValues(valuesString: Task[String]): Task[A] =
    for {
      values <- valuesString.unpickle[H]
      task <- create(values)
    } yield
      task
}

object TaskKey {

  abstract class Router {
    private var _keys = Map[String, TaskKey[_, _]]()

    private[TaskKey] def add(key: TaskKey[_, _ <: HList]): Unit = _keys = _keys + (key.name -> key)

    def keys: Seq[TaskKey[_, _]] = _keys.values.toSeq

    protected implicit val router: Router = this


    def route(name: String, valuesString: String): Task[_] =
      _keys(name).unpickleValues(Task.now(valuesString))

    def route0(stringTask: Task[String]): Task[_] =
      for {
        string <- stringTask
        (name, valuesString0) = string.span(_ != ';')
        result <- route(name, valuesString0.drop(1))
      } yield
        result
  }

  class ExecutionManager {
    def routeLocal(id: String, obj: String): LocalTask[_] = {
      localTasks.find(_.id == id).get
    }

    var localTasks: Seq[LocalTask[_]] = Seq.empty
    var remoteTasks: Seq[RemoteTask[_]] = Seq.empty
  }

  object ExecutionManager {

    abstract class ExecutedTask[A](val id: String) {
      def cancel: Unit
      def result(t: Try[A]): Unit
    }

    class LocalTask[A](task: Task[A], id: String) extends ExecutedTask[A](id) {
      override def cancel: Unit = ??? //f.cancel

      //val f = task.runAsync
      //f.onComplete(result)

      override def result(t: Try[A]): Unit = {
        // send result to remote
        // route result to right remote task
        // result on remote task
        ???
      }
    }

    class RemoteTask[A](id: String) extends ExecutedTask[A](id) {
      val promise = Promise[A]

      val task: Task[A] = Task.fromFuture(promise.future)

      override def cancel: Unit = {
        // send cancel to remote
        // route cancel to remote task
        // cancel remote task
        ???
      }

      override def result(t: Try[A]): Unit = promise.complete(t)
    }

  }

  case class KeyedTask[A, H <: HList](key: TaskKey[A, H])
                                     (val task: Task[A], valuesTask: Task[H]) {
    implicit private val pickler: TaskPickler[H] = key.pickler

    def pickle: Task[(String, String)] =
      for (valuesString <- valuesTask.pickle) yield (key.name, valuesString)

    def pickle0: Task[String] =
      pickle.map(pickled => s"${pickled._1};${pickled._2}")
  }

  def main(args: Array[String]): Unit = {
    import de.lolhens.task.pickling.objectOutputStreamOps._

    object MainRouter extends Router {
      val task1 = TaskKey[String, String :: HNil]("Task1")((a: String :: HNil) => task(a.head).task)
    }

    def task(s: String): TaskKey.KeyedTask[String, String :: HNil] = MainRouter.task1.key(Task(s"Hello $s"))(Task.now(s :: HNil))

    import monix.execution.Scheduler.Implicits.global
    println(MainRouter.route0(task("test").pickle0).runSyncUnsafe(Duration.Inf))
  }
}
