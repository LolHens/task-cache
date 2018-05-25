package de.lolhens.task

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.duration._
import scala.ref.SoftReference
import scala.util.Try

object CachedTask {
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
    def persist(persistence: Persistence,
                duration: Duration,
                cacheErrors: Boolean = true): Task[A] =
      duration match {
        case Duration.Inf =>
          (for {
            mvar <- persistence[Option[Try[A]]](None).memoize
            elemOption <- mvar.take
            elem <- elemOption.map(Task.now).getOrElse(task.materialize)
            newElemOption = Some(elem).filter(_.isSuccess || cacheErrors)
            _ <- mvar.put(newElemOption)
          } yield
            elem)
            .dematerialize

        case undef if undef eq Duration.Undefined =>
          val emptyRef = SoftReference(null)
          (for {
            mvar <- persistence[SoftReference[Try[A]]](emptyRef).memoize
            ref <- mvar.take
            elemOption = ref.get
            elem <- elemOption.map(Task.now).getOrElse(task.materialize)
            newRef = elemOption.map(_ => ref).getOrElse(
              if (elem.isSuccess || cacheErrors) SoftReference(elem)
              else emptyRef
            )
            _ <- mvar.put(newRef)
          } yield
            elem)
            .dematerialize

        case ttl: FiniteDuration =>
          val millis = ttl.toMillis
          (for {
            mvar <- persistence[Option[(Try[A], Long)]](None).memoize
            elemOption <- mvar.take
            now = System.currentTimeMillis()
            elem <- elemOption.filter(_._2 + millis > now).map(Task.now).getOrElse(
              task.materialize.map(_ -> now)
            )
            newElemOption = Some(elem).filter(_._1.isSuccess || cacheErrors)
            _ <- mvar.put(newElemOption)
          } yield
            elem._1)
            .dematerialize

        case _ =>
          task
      }

    def persistOnSuccess(persistence: Persistence,
                         duration: Duration): Task[A] =
      persist(persistence, duration, cacheErrors = false)

    def cache(duration: Duration,
              cacheErrors: Boolean = true): Task[A] =
      duration match {
        case Duration.Inf =>
          if (cacheErrors) task.memoize
          else task.memoizeOnSuccess

        case Duration.Zero =>
          task

        case _ =>
          persist(Persistence.Memory, duration, cacheErrors)
      }

    def cacheOnSuccess(duration: Duration): Task[A] =
      cache(duration, cacheErrors = false)
  }

}
