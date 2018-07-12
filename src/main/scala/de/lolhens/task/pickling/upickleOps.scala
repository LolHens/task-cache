package de.lolhens.task.pickling

import monix.eval.Task

import scala.util.Try

object upickleOps {
  implicit def tryPickler[A](implicit eitherPickler: Pickler[Either[String, A]],
                             throwablePickler: Pickler[Throwable]): Pickler[Try[A]] =
    eitherPickler.bimapF {
      case Left(string) => throwablePickler.unpickle(string).map(Left(_))
      case Right(value) => Task.now(Right(value))
    } {
      case Left(throwable) => throwablePickler.pickle(throwable).map(Left(_))
      case Right(value) => Task.now(Right(value))
    }
      .bimap(_.toTry)(_.toEither)

  implicit def throwablePickler: Pickler[Throwable] = objectOutputStreamOps.pickler[Throwable]

  implicit def pickler[A](implicit readWriter: upickle.default.ReadWriter[A]): Pickler[A] = new Pickler[A] {
    override def pickle(value: A): Task[String] =
      Task(upickle.default.write(value)(readWriter))

    override def unpickle(string: String): Task[A] =
      Task(upickle.default.read(string)(readWriter))
  }
}
