package psp
package std

object HashEq {
  def universal[A]                                                  = apply[A](_ == _, _.##)
  def reference[A <: AnyRef]                                        = apply[A](_ eq _, System.identityHashCode)
  def shown[A: Show]                                                = apply[A](_.to_s == _.to_s, _.to_s.##)
  def apply[A](cmp: (A, A) => Boolean, hashFn: A => Int): HashEq[A] = api.HashEq(cmp, hashFn)

  final case class Wrap[A](value: A)(implicit heq: HashEq[A]) {
    override def hashCode = value.hash
    override def equals(x: Any): Boolean = x match {
      case Wrap(that) => value === that.asInstanceOf[A]
      case _          => false
    }
    override def toString = pp"$value"
  }
}
