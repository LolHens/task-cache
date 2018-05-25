package de.lolhens.task

import monix.eval.{MVar, Task}

trait Persistence {
  def empty[A]: Task[MVar[A]]

  def apply[A](default: A): Task[MVar[A]] =
    for {
      mvar <- empty[A]
      _ <- mvar.put(default)
    } yield
      mvar
}

object Persistence {

  object Memory extends Persistence {
    override def empty[A]: Task[MVar[A]] = MVar.empty
  }

}
