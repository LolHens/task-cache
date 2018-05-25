package de.lolhens.task

import scala.ref.SoftReference

class SoftRef[A](val softReference: SoftReference[Tuple1[A]]) extends AnyVal {
  def get: Option[A] = softReference.get.map(_._1)

  def clear(): Unit = softReference.clear()

  def isEnqueued: Boolean = softReference.isEnqueued
}

object SoftRef {
  def apply[A](value: A): SoftRef[A] = new SoftRef(SoftReference(Tuple1(value)))

  def unapply[A](ref: SoftRef[A]): Option[A] = ref.get

  def empty[A]: SoftRef[A] = new SoftRef(SoftReference(null))
}
