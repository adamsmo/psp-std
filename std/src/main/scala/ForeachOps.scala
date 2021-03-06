package psp
package std
package ops

import api._

final class InPlace[A](val xs: Array[A]) extends AnyVal {
  private def andThis(op: Unit): Array[A] = xs
  private def lastIndex = xs.length - 1
  private def sortRef(cmp: Comparator[A]) = java.util.Arrays.sort[A](xs.castTo[Array[Ref[A]]], cmp)
  private def isReference = (xs: Any) match {
    case _: Array[AnyRef] => true
    case _                => false
  }
  private def midpoint: Int  = xs.length / 2
  private def swap(i1: Int, i2: Int) {
    val tmp = xs(i1)
    xs(i1) = xs(i2)
    xs(i2) = tmp
  }

  def map(f: ToSelf[A]): Array[A]           = andThis(0 to lastIndex foreach (i => xs(i) = f(xs(i))))
  def sort(implicit z: Order[A]): Array[A]  = andThis(if (isReference) sortRef(z.toComparator) else Array sortInPlace xs)
  def sortBy[B: Order](f: A => B): Array[A] = sort(orderBy[A](f))
  def reverse(): Array[A]                   = andThis(0 until midpoint foreach (i => swap(i, lastIndex - i)))
  def shuffle(): Array[A]                   = andThis(0 until lastIndex foreach (i => swap(i, i + randomNat(lastIndex - i))))
}

final class ArraySpecificOps[A](val xs: Array[A]) extends AnyVal with HasPreciseSizeMethods {
  def size: IntSize                          = Precise(xs.length)
  def apply(idx: Index): A                   = xs(idx.getInt)
  def updated(idx: Index, value: A): xs.type = andThis(xs(idx.getInt) = value)
  def mapNow[B: CTag](f: A => B): Array[B]   = newArray[B](xs.length) doto (arr => foreachIntIndex(i => arr(i) = f(xs(i))))
  def inPlace: InPlace[A]                    = new InPlace[A](xs)
  private def andThis(op: Unit): xs.type     = xs
}

final class ForeachOps[A](val xs: Each[A]) extends AnyVal {
  def +:(elem: A): Each[A] = Each.join(Direct(elem), xs)
  def :+(elem: A): Each[A] = Each.join(xs, Direct(elem))

  def sized(size: Precise): Each[A] = new Each.Sized(xs, size)
  def memo: Indexed.Memo[A] = xs match {
    case xs: Indexed.Memo[A] => xs
    case _                   => new Indexed.Memo(xs)
  }
}

final class DirectOps[A](val xs: Direct[A]) extends AnyVal {
  def +:(x: A): Direct[A]          = Direct.prepend(x, xs)
  def :+(x: A): Direct[A]          = Direct.append(xs, x)
  def ++(ys: Direct[A]): Direct[A] = Direct.join(xs, ys)

  def apply(i: Index): A           = xs elemAt i
  def length: Int                  = xs.intSize
  def reverse: Direct[A]  = xs match {
    case Direct.Reversed(xs) => xs
    case _                   => new Direct.Reversed(xs)
  }
  def mapNow[B](f: A => B): Direct[B] = {
    val arr = newArray[Any](length)
    xs.indices foreach (i => arr(i.getInt) = f(xs(i)))
    Direct.wrapArray[B](arr)
  }
}

final class LinearOps[A](val xs: Linear[A]) extends AnyVal {
  def ::(x: A): Linear[A] = Linear.cons(x, xs)
}
