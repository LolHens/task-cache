package de.lolhens.task.pickling

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import monix.eval.Task

object objectOutputStreamOps {
  implicit def pickler[A]: Pickler.BinaryPickler[A] = new Pickler.BinaryPickler[A] {
    override def pickleBinary(value: A): Task[Array[Byte]] =
      Task(new ByteArrayOutputStream()).bracket { outputStream =>
        for {
          _ <- Task(new ObjectOutputStream(outputStream)).bracket { objectOutputStream =>
            Task(objectOutputStream.writeObject(value))
          }(e => Task.eval(e.close()))
          string <- Task(outputStream.toByteArray)
        } yield
          string
      }(e => Task.eval(e.close()))

    override def unpickleBinary(bytes: Array[Byte]): Task[A] =
      Task(new ByteArrayInputStream(bytes)).bracket { inputStream =>
        Task(new ObjectInputStream(inputStream)).bracket { objectInputStream =>
          Task(objectInputStream.readObject().asInstanceOf[A])
        }(e => Task.eval(e.close()))
      }(e => Task.eval(e.close()))
  }
}
