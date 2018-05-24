package de.lolhens.task

import java.time.Instant

import monix.eval.{MVar, Task}
import monix.execution.atomic.Atomic

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.ref.SoftReference

class Cached[A](val task: Task[A]) extends AnyVal {
  def memoize: Task[A] = task.memoize

  def ttl(duration: Duration): Task[A] = {

  }
}

object Cached {
  def main(args: Array[String]): Unit = {
    val task = Task("test")
    task.memoize
  }

  val softReference = SoftReference(4)

  softReference.get

  val atomic = Atomic(4)
  softReference
  .

  implicit class CachedTaskOps[A](val task: Task[A]) extends AnyVal {
    def cache(duration: Duration = Duration.Undefined, cacheErrors: Boolean = true): Task[A] =
      duration match {
        case Duration.Inf => task.memoize
        case Duration.MinusInf => task
        case Duration.Zero => task
        case Duration.Undefined =>
          for {
            refVar <- MVar[SoftReference[Tuple1[A]]](SoftReference(null)).memoize
            ref <- refVar.take
            elemOption = ref.get
            boxedElem <- elemOption.map(Task.now).getOrElse(
              task.map(Tuple1(_))
            )
            newRef = elemOption.map(_ => ref).getOrElse(
              SoftReference(boxedElem)
            )
            _ <- refVar.put(newRef)
          } yield
            boxedElem._1

        case ttl: FiniteDuration =>
          val millis = ttl.toMillis
          for {
            elemOptionVar <- MVar[Option[(A, Long)]](None).memoize
            elemOption <- elemOptionVar.take
            now = System.currentTimeMillis()
            elem = elemOption.filter(_._2 + millis >= now).map(Task.now).getOrElse(

            )
            elemOption = elemOption.get
            boxedElem <- elemOption.map(Task.now).getOrElse(
              task.map(Tuple1(_))
            )
            newRef = elemOption.map(_ => elemOption).getOrElse(
              SoftReference(boxedElem)
            )
            _ <- elemOptionVar.put(newRef)
          } yield
            boxedElem._1
      }

    def cacheOnSuccess(duration: Duration = Duration.Undefined): Task[A] =
      cache(duration, cacheErrors = false)
  }

}
