package de.lolhens.task

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import de.lolhens.task.Persistence.CacheEntry
import monix.eval.{MVar, Task}
import upickle.default.ReadWriter

import scala.util.Try

abstract class Persistence[T] {
  implicit val readWriter: ReadWriter[T]

  def get: Task[MVar[CacheEntry[T]]]

  def use(f: CacheEntry[T] => Task[(CacheEntry[T], Try[T])]): Task[T] =
    for {
      mvar <- get.memoize
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
  type CacheEntry[T] = Option[(Try[T], Long)]

  case class Memory[T]() extends Persistence[T] {
    override implicit val readWriter: ReadWriter[T] = null

    override def get: Task[MVar[Option[T]]] = MVar[Option[T]](None)
  }

  case class File[T](path: Path)(implicit val readWriter: ReadWriter[T]) extends Persistence[T] {
    override def get: Task[MVar[Option[T]]] =
      for {
        lock <- MVar(())
      } yield
        new MVar[Option[T]] {
          override def put(value: Option[T]): Task[Unit] =
            for {
              serialized <- Task(upickle.default.write(value))
              bytes <- Task(serialized.getBytes(StandardCharsets.UTF_8))
              _ <- Task(Files.write(path, bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
              ))
              _ <- lock.put(())
            } yield ()

          override def take: Task[Option[T]] =
            for {
              _ <- lock.take
              value <- read
            } yield
              value

          override def read: Task[Option[T]] =
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
                serializedOption.flatMap(upickle.default.read[Option[T]](_))
              )
            } yield
              value
        }
  }

}
