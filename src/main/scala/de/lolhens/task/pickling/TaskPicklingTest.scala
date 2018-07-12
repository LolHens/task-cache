package de.lolhens.task.pickling

import de.lolhens.task.pickling.TaskPickler._
import monix.eval.Task

import scala.concurrent.duration.Duration

object TaskPicklingTest {
  def main(args: Array[String]): Unit = {
    val task = Task(List("ASDF", "JKLÖ")) //.map(_.map(_.toInt))

    import monix.execution.Scheduler.Implicits.global
    import upickleOps._
    println(task.pickle.pickle.unpickle[String].unpickle[List[String]].runSyncUnsafe(Duration.Inf))
  }
}
