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
    val task = Task.eval {
      println("Test");
      "asdf!"
    }
    val task1 = task.cache(Duration.Inf)
    val task2 = task.cache(500.millis)
    val task3 = task.cache(Duration.Undefined)
    val persisted = task.persist(Persistence.File(Paths.get("serialized.txt")), 10.seconds)

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

  implicit class CachedTaskOps[T](val task: Task[T]) extends AnyVal {
    def cache(ttl: Duration,
              cacheErrors: Boolean = true): Task[T] =
      ttl match {
        case Duration.Inf =>
          if (cacheErrors) task.memoize
          else task.memoizeOnSuccess

        case undef if undef eq Duration.Undefined =>
          val emptyRef = SoftReference(null)
          (for {
            mvar <- MVar[SoftReference[Try[T]]](emptyRef).memoize
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

        case Duration.Zero =>
          task

        case ttl: FiniteDuration =>
          (for {
            mvar <- MVar[Option[(Try[T], Long)]](None).memoize
            elemOption <- mvar.take
            now = System.currentTimeMillis()
            elem <- elemOption.filter(_._2 < now).map(Task.now).getOrElse(
              task.materialize.map(_ -> (now + ttl.toMillis))
            )
            newElemOption = Some(elem).filter(_._1.isSuccess || cacheErrors)
            _ <- mvar.put(newElemOption)
          } yield
            elem._1)
            .dematerialize

        case _ =>
          task
      }

    def cacheOnSuccess(duration: Duration): Task[T] =
      cache(duration, cacheErrors = false)

    def persist(persistence: Persistence[T],
                ttl: Duration,
                cacheErrors: Boolean = true): Task[T] =
      persistence.use { elemOption =>
        val now = System.currentTimeMillis()

        def timeout: Long = ttl match {
          case finite: FiniteDuration =>
            now + finite.toMillis

          case Duration.Inf =>
            Long.MaxValue

          case undef if undef eq Duration.Undefined =>
            Long.MaxValue

          case _ =>
            0
        }

        for {
          elem <- elemOption.filter(_._2 < now).map(Task.now)
            .getOrElse(task.materialize.map(_ -> timeout))
          newElemOption = Some(elem).filter(_._1.isSuccess || cacheErrors)
        } yield
          (newElemOption, elem._1)
      }
        //.dematerialize

    def persistOnSuccess(persistence: Persistence[T],
                         ttl: Duration): Task[T] =
      persist(persistence, ttl, cacheErrors = false)
  }

}
