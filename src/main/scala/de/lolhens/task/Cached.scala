package de.lolhens.task

import monix.eval.{MVar, Task}
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.duration._
import scala.ref.SoftReference
import scala.util.Try

object Cached {
  def main(args: Array[String]): Unit = {
    val task = Task.eval(println("Test"))
    val task1 = task.cache(Duration.Inf)
    val task2 = task.cache(500.millis)
    val task3 = task.cache(Duration.Undefined)

    for (_ <- 0 until 1000) {
      task2.runSyncUnsafe(Duration.Inf)
      println("a")
      Thread.sleep(100)
    }
  }

  implicit class CachedTaskOps[A](val task: Task[A]) extends AnyVal {
    def cache(duration: Duration = Duration.Undefined, cacheErrors: Boolean = true): Task[A] =
      duration match {
        case Duration.Inf =>
          if (cacheErrors) task.memoize
          else task.memoizeOnSuccess

        case Duration.Zero =>
          task

        case undef if undef eq Duration.Undefined =>
          (for {
            refVar <- MVar[SoftReference[Try[A]]](SoftReference(null)).memoize
            ref <- refVar.take
            elemOption = ref.get
            elem <- elemOption.map(Task.now).getOrElse(task.materialize)
            newRef = elemOption.map(_ => ref).getOrElse(
              if (elem.isSuccess || cacheErrors) SoftReference(elem)
              else SoftReference(null)
            )
            _ <- refVar.put(newRef)
          } yield
            elem)
            .dematerialize

        case ttl: FiniteDuration =>
          val millis = ttl.toMillis
          (for {
            elemOptionVar <- MVar[Option[(Try[A], Long)]](None).memoize
            elemOption <- elemOptionVar.take
            now = System.currentTimeMillis()
            elem <- elemOption.filter(_._2 + millis >= now).map(Task.now).getOrElse(
              task.materialize.map(_ -> now)
            )
            newElemOption = Some(elem).filter(_._1.isSuccess || cacheErrors)
            _ <- elemOptionVar.put(newElemOption)
          } yield
            elem._1)
            .dematerialize

        case _ =>
          task
      }

    def cacheOnSuccess(duration: Duration = Duration.Undefined): Task[A] =
      cache(duration, cacheErrors = false)
  }

}
