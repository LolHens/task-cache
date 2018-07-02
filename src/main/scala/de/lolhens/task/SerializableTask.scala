package de.lolhens.task

import java.io._
import java.nio.charset.StandardCharsets

import monix.eval.Task

object SerializableTask {

  trait Reader[A] {
    def read(string: String): Task[A]
  }

  trait Writer[A] {
    def write(value: A): Task[String]
  }

  implicit class SerializableTaskOps[A](val self: Task[A]) extends AnyVal {
    def pickle(implicit writer: Writer[A]): Task[String] =
      self.flatMap(e => writer.write(e))

    def unpickle(implicit reader: Reader[A], ev: A <:< String): Task[A] =
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
    private val charset = StandardCharsets.UTF_8

    implicit def reader[A]: Reader[A] = { string: String =>
      Task(new ByteArrayInputStream(string.getBytes(charset))).bracket { inputStream =>
        Task(new ObjectInputStream(inputStream)).bracket { objectInputStream =>
          Task(objectInputStream.readObject().asInstanceOf[A])
        }(e => Task.eval(e.close()))
      }(e => Task.eval(e.close()))
    }

    implicit def writer[A]: Writer[A] = { value: A =>
      Task(new ByteArrayOutputStream()).bracket { outputStream =>
        for {
          _ <- Task(new ObjectOutputStream(outputStream)).bracket { objectOutputStream =>
            Task(objectOutputStream.writeObject(value))
          }(e => Task.eval(e.close()))
          string <- Task(new String(outputStream.toByteArray, charset))
        } yield
          string
      }(e => Task.eval(e.close()))
    }
  }

}
