package de.lolhens.task.pickling

import java.util.Base64

import monix.eval.Task

import scala.language.{higherKinds, implicitConversions}

trait Pickler[A] {
  def pickle(value: A): Task[String]
  def unpickle(string: String): Task[A]

  def bimap[B](to: A => B)(from: B => A): Pickler[B] = new Pickler[B] {
    override def pickle(value: B): Task[String] = Pickler.this.pickle(from(value))
    override def unpickle(string: String): Task[B] = Pickler.this.unpickle(string).map(to)
  }

  def bimapF[B](to: A => Task[B])(from: B => Task[A]): Pickler[B] = new Pickler[B] {
    override def pickle(value: B): Task[String] = from(value).flatMap(Pickler.this.pickle)
    override def unpickle(string: String): Task[B] = Pickler.this.unpickle(string).flatMap(to)
  }
}

object Pickler {

  trait BinaryPickler[A] extends Pickler[A] {
    override def pickle(value: A): Task[String] =
      pickleBinary(value)
        .flatMap(e => Task(Base64.getEncoder.encodeToString(e)))

    override def unpickle(string: String): Task[A] =
      Task(Base64.getDecoder.decode(string))
        .flatMap(unpickleBinary)

    def pickleBinary(value: A): Task[Array[Byte]]
    def unpickleBinary(bytes: Array[Byte]): Task[A]
  }

}
