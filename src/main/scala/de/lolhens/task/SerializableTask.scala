package de.lolhens.task

import java.io._
import java.util.Base64

import monix.eval.Task

import scala.concurrent.duration.Duration

object SerializableTask {

  trait Reader[A] {
    def read(string: String): Task[A]
  }

  trait Writer[A] {
    def write(value: A): Task[String]
  }

  trait ByteReader[A] extends Reader[A] {
    override def read(string: String): Task[A] =
      Task(Base64.getDecoder.decode(string))
        .flatMap(readBytes)

    def readBytes(bytes: Array[Byte]): Task[A]
  }

  trait ByteWriter[A] extends Writer[A] {
    override def write(value: A): Task[String] =
      writeBytes(value)
        .flatMap(e => Task(Base64.getEncoder.encodeToString(e)))

    def writeBytes(value: A): Task[Array[Byte]]
  }

  implicit class SerializableTaskOps[A](val self: Task[A]) extends AnyVal {
    def pickle(implicit writer: Writer[A]): Task[String] =
      self.flatMap(e => writer.write(e))

    def unpickle[B](implicit reader: Reader[B], ev: A <:< String): Task[B] =
      self.flatMap(e => reader.read(e))
  }

  object upickleOps {
    implicit def reader[A](implicit reader: upickle.default.Reader[A]): Reader[A] = { string: String =>
      Task(upickle.default.read(string))
    }

    implicit def writer[A](implicit writer: upickle.default.Writer[A]): Writer[A] = { value: A =>
      Task(upickle.default.write(value))
    }
  }

  object objectOutputStreamOps {
    implicit def reader[A]: ByteReader[A] = { bytes: Array[Byte] =>
      Task(new ByteArrayInputStream(bytes)).bracket { inputStream =>
        Task(new ObjectInputStream(inputStream)).bracket { objectInputStream =>
          Task(objectInputStream.readObject().asInstanceOf[A])
        }(e => Task.eval(e.close()))
      }(e => Task.eval(e.close()))
    }

    implicit def writer[A]: ByteWriter[A] = { value: A =>
      Task(new ByteArrayOutputStream()).bracket { outputStream =>
        for {
          _ <- Task(new ObjectOutputStream(outputStream)).bracket { objectOutputStream =>
            Task(objectOutputStream.writeObject(value))
          }(e => Task.eval(e.close()))
          string <- Task(outputStream.toByteArray)
        } yield
          string
      }(e => Task.eval(e.close()))
    }
  }

  def main(args: Array[String]): Unit = {
    val task = Task(List("ASDF", "JKLÖ"))

    import monix.execution.Scheduler.Implicits.global
    import objectOutputStreamOps._
    println(task.pickle.unpickle[List[String]].runSyncUnsafe(Duration.Inf))
  }
}
