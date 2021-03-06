package psp
package tests

import psp.std._, api._
import scala.{ collection => sc }
import sc.{ mutable => scm, immutable => sci }

object Probe {
  final case class Direct(range: IntRange, counter: RecorderCounter) extends api.Direct[Int] {
    def size                          = range.size
    def elemAt(i: Index)              = counter record range(i)
    def foreach(f: Int => Unit): Unit = size.indices foreach (i => f(elemAt(i)))
    def isEmpty                       = range.isEmpty
    def view                          = new DirectView(this)
    override def toString             = s"${this.shortClass} ($counter)"
  }
  final case class Linear(range: IntRange, counter: RecorderCounter) extends api.Linear[Int] {
    def head                          = counter record range.head
    def tail                          = { head ; new Linear(range.tail, counter) }
    def isEmpty                       = range.isEmpty
    def size                          = if (isEmpty) 0.size else 1.size.atLeast
    def foreach(f: Int => Unit): Unit = if (!isEmpty) { f(head) ; tail foreach f }
    def view                          = new LinearView(this)
    override def toString             = s"${this.shortClass} ($counter)"
  }
  final case class Sized(range: IntRange, counter: RecorderCounter) extends api.Linear[Int] {
    def head                          = counter record range.head
    def tail                          = { head ; new Sized(range.tail, counter) }
    def isEmpty                       = range.isEmpty
    def size                          = range.size
    def foreach(f: Int => Unit): Unit = if (!isEmpty) { f(head) ; tail foreach f }
    override def toString             = s"${this.shortClass} ($counter)"
  }
  final case class ScalaLinear(range: IntRange, counter: RecorderCounter) extends sci.LinearSeq[Int] with sc.LinearSeqLike[Int, ScalaLinear] with sc.LinearSeqOptimized[Int, ScalaLinear] {
    override def newBuilder: scmBuilder[Int, ScalaLinear] = sciVector.newBuilder[Int] mapResult (xs => ScalaLinear(xs.head to xs.last, counter))
    override def isEmpty                                  = range.isEmpty
    override def head: Int                                = counter record range.head
    override def tail: ScalaLinear                        = { head ; new ScalaLinear(range.tail, counter) }
    override def toString                                 = s"$view ($counter)"
  }
  final case class ScalaDirect(range: IntRange, counter: RecorderCounter) extends sci.IndexedSeq[Int] with sc.IndexedSeqLike[Int, ScalaDirect] with sc.IndexedSeqOptimized[Int, ScalaDirect] {
    override def newBuilder: scmBuilder[Int, ScalaDirect] = sciVector.newBuilder[Int] mapResult (xs => ScalaDirect(xs.head to xs.last, counter))
    def apply(index: Int): Int                            = counter record range(Index(index))
    def length: Int                                       = range.intSize
    override def toString                                 = s"$view ($counter)"
  }
}
