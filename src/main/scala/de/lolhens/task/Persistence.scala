package de.lolhens.task

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import monix.eval.{MVar, Task}
import upickle.default.ReadWriter

import scala.util.Try

trait Persistence {
  protected def get[A: ReadWriter]: Task[MVar[Option[A]]]

  def use[A: ReadWriter](f: Option[A] => Task[(Option[A], A)]): Task[A] =
    for {
      mvar <- get[A].memoize
      (_, result) <- mvar.take
        .bracketE(f)((_, e) => e match {
          case Right((put, _)) =>
            mvar.put(put)

          case _ =>
            mvar.put(None)
        })
    } yield
      result
}

object Persistence {

  object Memory extends Persistence {
    override def get[A: ReadWriter]: Task[MVar[Option[A]]] = MVar[Option[A]](None)
  }

  case class File(path: Path) extends Persistence {
    override def get[A: ReadWriter]: Task[MVar[Option[A]]] =
      for {
        lock <- MVar(())
      } yield
        new MVar[Option[A]] {
          override def put(value: Option[A]): Task[Unit] =
            for {
              serialized <- Task(upickle.default.write(value))
              bytes <- Task(serialized.getBytes(StandardCharsets.UTF_8))
              _ <- Task(Files.write(path, bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
              ))
              _ <- lock.put(())
            } yield ()

          override def take: Task[Option[A]] =
            for {
              _ <- lock.take
              value <- read
            } yield
              value

          override def read: Task[Option[A]] =
            for {
              serializedOption <- Task {
                for {
                  path <- Some(path).filter(Files.exists(_))
                  bytes <- Try(Files.readAllBytes(path)).toOption
                  serialized = new String(bytes, StandardCharsets.UTF_8)
                } yield
                  serialized
              }
              value <- Task(
                serializedOption.flatMap(upickle.default.read[Option[A]](_))
              )
            } yield
              value
        }
  }

}
