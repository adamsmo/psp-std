package psp
package tests

import org.scalacheck._, Prop._, Gen._
import psp.std._, SizeInfo._

trait PspArb0                 { implicit def arbSize: Arbitrary[Size]         = Arbitrary(genSize)     }
trait PspArb1 extends PspArb0 { implicit def arbSizeInfo: Arbitrary[SizeInfo] = Arbitrary(genSizeInfo) }
trait PspArb2 extends PspArb1 { implicit def arbAtomic: Arbitrary[Atomic]     = Arbitrary(genAtomic)   }
trait PspArb3 extends PspArb2 { implicit def arbPrecise: Arbitrary[Precise]   = Arbitrary(genPrecise)  }

class SizeInfoSpec extends ScalacheckBundle with PspArb3 {
  type SI = SizeInfo
  type BinOp[T] = (T, T) => T
  type Tried[T] = Either[Throwable, T]

  private def tried[T](op: => T) = try Right(op) catch { case t: Throwable => Left(t) }

  // When testing e.g. associativity and the sum overflows, we
  // need to do more than compare values for equality.
  private def sameOutcome[T](p1: => T, p2: => T): Boolean = (tried(p1), tried(p2)) match {
    case (Right(x1), Right(x2)) => x1 == x2
    case (Left(t1), Left(t2))   => t1.getClass == t2.getClass
    case _                      => false
  }

  def commutative[T: Arbitrary](op: BinOp[T]): Prop = forAll((p1: T, p2: T) => sameOutcome(op(p1, p2), op(p2, p1)))
  def associative[T: Arbitrary](op: BinOp[T]): Prop = forAll((p1: T, p2: T, p3: T) => sameOutcome(op(op(p1, p2), p3), op(p1, op(p2, p3))))

  def certain[T: Arbitrary](f: T => ThreeValue): Prop                    = forAll((p1: T) => f(p1).isTrue)
  def certain[T: Arbitrary, U: Arbitrary](f: (T, U) => ThreeValue): Prop = forAll((p1: T, p2: U) => f(p1, p2).isTrue)

  def flip(r: Prop.Result): Prop.Result = r match {
    case Prop.Result(Prop.True, _, _, _)  => r.copy(status = Prop.False)
    case Prop.Result(Prop.False, _, _, _) => r.copy(status = Prop.True)
    case _                                => r
  }

  def bundle = "SizeInfo"
  // ...Aaaaand right on cue, a bunch of these tests broke until I added a type annotation.
  def props = Seq[NamedProp](
    "`+` on precises"      -> forAll((s: Precise, n: Size) => (s + n).size == s.size + n),
    "s1 <= (s1 max s2)"    -> certain[Atomic, Atomic]((s1, s2) => (s1: SI) <= (s1 max s2)),
    "s1 >= (s1 min s2)"    -> certain[Atomic, Atomic]((s1, s2) => (s1: SI) >= (s1 min s2)),
    "s1 <= (s1 + s2)"      -> certain[Atomic, Atomic]((s1, s2) => (s1: SI) <= (s1 + s2)),
    "s1 >= (s1 - s2)"      -> certain[Atomic, Precise]((s1, s2) => (s1: SI) >= (s1 - s2)),
    "<inf> + n"            -> certain((s1: SI) => (Infinite + s1) <==> Infinite),
    "`+` is associative"   -> associative[SI](_ + _),
    "`max` is associative" -> associative[SI](_ max _),
    "`min` is associative" -> associative[SI](_ min _),
    "`+` is commutative"   -> commutative[SI](_ + _),
    "`max` is commutative" -> commutative[SI](_ max _),
    "`min` is commutative" -> commutative[SI](_ min _)
  )
}
