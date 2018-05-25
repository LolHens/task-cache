package de.lolhens.task

import java.nio.file.Paths

import monix.eval.{MVar, Task}
import monix.execution.Scheduler.Implicits.global
import upickle.default._

import scala.concurrent.duration._
import scala.ref.SoftReference
import scala.util.Try

object CachedTask {
  def main(args: Array[String]): Unit = {
    val task = Task.eval(println("Test"))
    val task1 = task.cache(Duration.Inf)
    val task2 = task.cache(500.millis)
    val task3 = task.cache(Duration.Undefined)
    val persisted = task.persist(Persistence.File(Paths.get("serialized.txt")), 500.millis)

    for (_ <- 0 until 1000) {
      persisted.runSyncUnsafe(Duration.Inf)
      println("a")
      Thread.sleep(100)
    }
  }

  implicit def tryReadWrite[T: ReadWriter]: ReadWriter[Try[T]] =
    readwriter[Either[String, T]].bimap[Try[T]](
      _.toEither.left.map(_.getMessage),
      _.left.map(new RuntimeException(_)).toTry
    )

  implicit class CachedTaskOps[A](val task: Task[A]) extends AnyVal {
    def persist(persistence: Persistence,
                duration: Duration,
                cacheErrors: Boolean = true)
               (implicit readWriter: ReadWriter[A]): Task[A] =
      duration match {
        case Duration.Inf =>
          (for {
            elem <- persistence.use[Try[A]] { elemOption =>
              for {
                elem <- elemOption.map(Task.now)
                  .getOrElse(task.materialize)
                newElemOption = Some(elem).filter(_.isSuccess || cacheErrors)
              } yield
                (newElemOption, elem)
            }
          } yield
            elem)
            .dematerialize

        case ttl: FiniteDuration =>
          val millis = ttl.toMillis
          (for {
            elem <- persistence.use[(Try[A], Long)] { elemOption =>
              val now = System.currentTimeMillis()
              for {
                elem <- elemOption.filter(_._2 + millis > now).map(Task.now)
                  .getOrElse(task.materialize.map(_ -> now))
                newElemOption = Some(elem).filter(_._1.isSuccess || cacheErrors)
              } yield
                (newElemOption, elem)
            }
          } yield
            elem._1)
            .dematerialize

        case undef if undef eq Duration.Undefined =>
          val emptyRef = SoftReference(null)
          (for {
            mvar <- MVar[SoftReference[Try[A]]](emptyRef).memoize
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

        case _ =>
          task
      }

    def persistOnSuccess(persistence: Persistence,
                         duration: Duration)
                        (implicit readWriter: ReadWriter[A]): Task[A] =
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
          persist(Persistence.Memory, duration, cacheErrors)(null)
      }

    def cacheOnSuccess(duration: Duration): Task[A] =
      cache(duration, cacheErrors = false)
  }

}
