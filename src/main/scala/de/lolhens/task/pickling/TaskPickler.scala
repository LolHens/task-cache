package de.lolhens.task.pickling

import monix.eval.Task

import scala.util.Try

class TaskPickler[A](pickler: Pickler[Try[A]]) extends Pickler[Task[A]] {
  override def pickle(value: Task[A]): Task[String] =
    value.materialize.flatMap(pickler.pickle)

  override def unpickle(string: String): Task[Task[A]] =
    pickler.unpickle(string).dematerialize.map(Task.now)
}

object TaskPickler {

  implicit class TaskPicklerOps[A](val self: Task[A]) extends AnyVal {
    def pickle(implicit pickler: TaskPickler[A]): Task[String] =
      pickler.pickle(self)

    def unpickle[B](implicit pickler: TaskPickler[B], ev: A <:< String): Task[B] =
      self.flatMap(e => pickler.unpickle(e)).flatten
  }

  implicit def taskPickler[A](implicit pickler: Pickler[Try[A]]): TaskPickler[A] =
    new TaskPickler[A](pickler)
}
