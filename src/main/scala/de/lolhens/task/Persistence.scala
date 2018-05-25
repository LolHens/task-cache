package de.lolhens.task

import monix.eval.{MVar, Task}

trait Persistence[Key] {
  def get[A](key: Key): Task[MVar[Option[A]]]

  /*def apply[A](default: A): Task[MVar[A]] =
    for {
      mvar <- empty[A]
      _ <- mvar.put(default)
    } yield
      mvar*/
}

object Persistence {
  object Memory extends Persistence[Unit] {
    override def get[A](key: Unit): Task[MVar[Option[A]]] = MVar(None)
  }

}
