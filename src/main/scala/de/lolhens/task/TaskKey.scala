package de.lolhens.task

import de.lolhens.task.SerializableTask._
import de.lolhens.task.TaskKey.{KeyedTask, Router}
import monix.eval.Task
import monix.execution.CancelableFuture
import shapeless._

import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.util.Try

case class TaskKey[A, H <: HList](name: String)
                                 (val create: H => Task[A])
                                 (implicit reader: Reader[H], router: Router) {
  require(!name.contains(";"))
  router.add(this)

  def key(task: Task[A])(valuesTask: Task[H]): KeyedTask[A, H] = KeyedTask(this)(task, valuesTask)

  def unpickle(valuesString: Task[String]): Task[A] =
    for {
      values <- valuesString.unpickle[H]
      task <- create(values)
    } yield
      task
}

object TaskKey {

  abstract class Router {
    private var _keys = Map[String, TaskKey[_, _]]()

    private[TaskKey] def add(key: TaskKey[_, _]): Unit = _keys = _keys + (key.name -> key)

    def keys: Seq[TaskKey[_, _]] = _keys.values.toSeq

    protected implicit val router: Router = this

    def route(name: String, valuesString: String): Task[_] =
      _keys(name).unpickle(Task.now(valuesString))

    class ExecutionManager {

    }

    object ExecutionManager {
      abstract class ExecutedTask[A](val id: String) {
        def cancel: Unit
        def result(t: Try[A]): Unit
      }

      var localTasks: Seq[LocalTask[_]] = Seq.empty
      var remoteTasks: Seq[RemoteTask[_]] = Seq.empty

      class LocalTask[A](task: Task[A], id: String) extends ExecutedTask(id) {
        override def cancel: Unit = f.cancel

        val f = task.runAsync
        f.onComplete(result)

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

    def route0(stringTask: Task[String]): Task[_] =
      for {
        string <- stringTask
        (name, valuesString0) = string.span(_ != ';')
        result <- route(name, valuesString0.drop(1))
      } yield
        result
  }

  case class KeyedTask[A, H <: HList](key: TaskKey[A, H])
                                     (val task: Task[A], valuesTask: Task[H]) {
    def pickle(implicit writer: Writer[H]): Task[(String, String)] =
      for (valuesString <- valuesTask.pickle) yield (key.name, valuesString)

    def pickle0(implicit writer: Writer[H]): Task[String] =
      pickle.map(pickled => s"${pickled._1};${pickled._2}")
  }

  def main(args: Array[String]): Unit = {
    import SerializableTask.objectOutputStreamOps._

    object MainRouter extends Router {
      val task1 = TaskKey[String, String :: HNil]("Task1")((a: String :: HNil) => task(a.head).task)
    }

    def task(s: String): TaskKey.KeyedTask[String, String :: HNil] = MainRouter.task1.key(Task(s"Hello $s"))(Task.now(s :: HNil))

    import monix.execution.Scheduler.Implicits.global
    println(MainRouter.route0(task("test").pickle0).runSyncUnsafe(Duration.Inf))
  }
}
