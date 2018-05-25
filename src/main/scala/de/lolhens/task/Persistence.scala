package de.lolhens.task

import java.nio.file.Path

import monix.eval.{MVar, Task}

trait Persistence[Key] {
  protected def get[A](key: Key): Task[MVar[Option[A]]]

  def use[A](key: Key)(f: Option[A] => Task[(Option[A], A)]): Task[A] =
    for {
      mvar <- get[A](key)
      (_, result) <- mvar.take.bracketE(f)((_, e) => e match {
        case Right((put, _)) =>
          mvar.put(put)

        case _ =>
          mvar.put(None)
      })
    } yield
      result
}

object Persistence {

  object Memory extends Persistence[Unit] {
    override def get[A](key: Unit): Task[MVar[Option[A]]] = MVar[Option[A]](None).memoize
  }

  object File extends Persistence[Path] {
    override def get[A](key: Path): Task[MVar[Option[A]]] =
      for {
        lock <- MVar(())
      } yield
        new MVar[Option[A]] {
          override def put(a: Option[A]): Task[Unit] = ???
          override def take: Task[Option[A]] = ???
          override def read: Task[Option[A]] = ???
        }
  }

}
