package de.lolhens.task

import monix.eval.{MVar, Task}
import monix.reactive.Observable

object ObservableSink {
  def apply[T]: (T => Task[Unit], Observable[T]) = {
    val mvarTask = MVar.empty[T].memoize

    val f = { value: T =>
      for {
        mvar <- mvarTask
        _ <- mvar.put(value)
      } yield ()
    }

    val take: Task[T] = for {
      mvar <- mvarTask
      value <- mvar.take
    } yield value

    val observable = Observable.fromTask(
      Task.deferAction { implicit scheduler =>
        Task.now(Observable.repeatEvalF(take).share)
      })
      .cache
      .flatten


    f -> observable
  }

  def main(args: Array[String]): Unit = {
    val (f, observable) = ObservableSink[String]

    import monix.execution.Scheduler.Implicits.global

    observable.foreach(println)

    while (true) {
      f("TEST").runAsync
      Thread.sleep(1000)
    }
  }
}
