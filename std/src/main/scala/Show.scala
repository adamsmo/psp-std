package psp
package std

import api._
import StdEq.stringEq

final class Label(val label: String) extends AnyVal {
  def matches(r: Regex)   = r isMatch label
  def contains(s: String) = label contains s
  def containsOp          = contains("&&") || contains("||") || (label startsWith "!")
  def isSafe              = matches("""^[(](.*?)[)]$""".r) || !containsOp
  def isBool              = isZero || isOne
  def isZero              = label eq Label.Zero.label
  def isOne               = label eq Label.One.label

  override def toString = label
}
object Label {
  val Zero = new Label(new String(""))
  val One  = new Label(new String(""))
  def apply(s: String) = new Label(s)
}

/** When a type class is more trouble than it's worth.
 *  Not overriding toString here to leave open the possibility of
 *  using a synthetic toString, e.g. of case classes.
 */
trait ShowDirect extends Any { def to_s: String }
trait ForceShowDirect extends Any with ShowDirect {
  override def toString = to_s
}

class TryShow[-A](shows: Show[A]) {
  def show(x: A): String = if (shows == null) "" + x else shows show x
}
object TryShow {
  implicit def apply[A](implicit z: Show[A] = Show.natural()): TryShow[A] = new TryShow[A](z)
}
final case class TryShown(__shown_rep: String) extends AnyVal {
  override def toString = __shown_rep
}

/** Used to achieve type-safety in the show interpolator.
 *  It's the String resulting from passing a value through its Show instance.
 */
final case class Shown(to_s: String) extends AnyVal with ForceShowDirect {
  def ~ (that: Shown): Shown = new Shown(to_s + that.to_s)
}

object Shown {
  def empty: Shown             = new Shown("")
  def apply(ss: Shown*): Shown = ss.m zreduce (_ ~ _)
}

final class ShowDirectOps(val x: ShowDirect) extends AnyVal {
  /** Java-style String addition without abandoning type safety.
   */
  def + (that: ShowDirect): ShowDirect                = Shown(x.to_s + that.to_s)
  def + [A](that: A)(implicit z: Show[A]): ShowDirect = Shown(x.to_s + (z show that))
}

final class ShowInterpolator(val stringContext: StringContext) extends AnyVal {
  /** The type of args forces all the interpolation variables to
   *  be of a type which is implicitly convertible to Shown, which
   *  means they have a Show[A] in scope.
   */
  def show(args: Shown*): String  = StringContext(stringContext.parts: _*).raw(args: _*)
  def pp(args: TryShown*): String = StringContext(stringContext.parts: _*).raw(args: _*)
  def shown(args: Shown*): Shown  = Shown(show(args: _*))

  /** Can't see any way to reuse the standard (type-safe) f-interpolator, will
   *  apparently have to reimplement it entirely.
   */
  def fshow(args: Shown*): String = (stringContext.parts map (_.processEscapes) mkString "").format(args: _*)

  final def sm(args: Any*): String = {
    def isLineBreak(c: Char) = c == '\n' || c == '\f' // compatible with StringLike#isLineBreak
    def stripTrailingPart(s: String) = {
      val index        = s indexWhere isLineBreak
      val pre: String  = s take index.sizeExcluding force
      val post: String = s drop index.sizeExcluding force;
      pre ~ post.stripMargin
    }
    val stripped: sciList[String] = stringContext.parts.toList match {
      case head :: tail => head.stripMargin :: (tail map stripTrailingPart)
      case Nil          => Nil
    }
    (new StringContext(stripped: _*).raw(args: _*)).trim
  }
}

trait ShowCollections {
  def showZipped[A1: Show, A2: Show](xs: ZipView[A1, A2]): String
  def showPair[A1: Show, A2: Show](x: A1 -> A2): String
  def showEach[A: Show](xs: Each[A]): String
  def showMap[K: Show, V: Show](xs: InMap[K, V]): String
  def showSet[A: Show](xs: InSet[A]): String
  def showJava[A: Show](xs: jIterable[A]): String
  def showScala[A: Show](xs: sCollection[A]): String
}
object ShowCollections {
  implicit object DefaultShowCollections extends DefaultShowCollections

  class DefaultShowCollections extends ShowCollections {
    def maxElements = Precise(10)
    def minElements = Precise(3)

    private def internalEach[A: Show](xs: Each[A]): String = Each.show[A](xs, minElements, maxElements)

    def showZipped[A1: Show, A2: Show](xs: ZipView[A1, A2]): String =
      showEach[String](xs.pairs map showPair[A1, A2])(Show.natural())

    def showPair[A1: Show, A2: Show](x: A1 -> A2): String = fst(x).to_s + " -> " + snd(x).to_s
    def showEach[A: Show](xs: Each[A]): String = xs match {
      case xs: InSet[_] => showSet(xs.castTo[InSet[A]]) // not matching on InSet[A] due to lame patmat warning
      case _            => "[ " ~ internalEach[A](xs) ~ " ]"
    }
    def showMap[K: Show, V: Show](xs: InMap[K, V]): String = xs match {
      case xs: ExMap[K, V] => xs.entries.tabular(x => fst(x).to_s, _ => "->", x => snd(x).to_s)
      case xs              => s"$xs"
    }
    def showSet[A: Show](xs: InSet[A]): String = xs match {
      case xs: ExSet[A] => "{ " ~ internalEach[A](xs) ~ " }"
      case _            => InSet show xs
    }
    def showJava[A: Show](xs: jIterable[A]): String    = "j[ " ~ internalEach(Each fromJava xs) ~ " ]"
    def showScala[A: Show](xs: sCollection[A]): String = "s[ " ~ internalEach(Each fromScala xs) ~ " ]"
  }
}

object Show {
  def apply[A](f: ToString[A]): Show[A] = new impl.ShowImpl[A](f)
  def natural[A](): Show[A]             = ToString

  /** This of course is not implicit as that would defeat the purpose of the endeavor.
   */
  private case object ToString extends Show[Any] {
    def show(x: Any): String = x match {
      case null          => ""
      case x: ShowDirect => x.to_s
      case x             => x.toString
    }
  }
}

/** An incomplete selection of show compositors.
 *  Not printing the way scala does.
 */
trait ShowInstances extends ShowEach {
  implicit def showAttributeName : Show[jAttributeName]       = Show.natural()
  implicit def showBoolean: Show[Boolean]                     = Show.natural()
  implicit def showChar: Show[Char]                           = Show.natural()
  implicit def showDouble: Show[Double]                       = Show.natural()
  implicit def showInt: Show[Int]                             = Show.natural()
  implicit def showLong: Show[Long]                           = Show.natural()
  implicit def showPath: Show[Path]                           = Show.natural()
  implicit def showScalaNumber: Show[ScalaNumber]             = Show.natural()
  implicit def showString: Show[String]                       = Show.natural()

  implicit def showClass: Show[jClass]                        = Show(_.shortName)
  implicit def showDirect: Show[ShowDirect]                   = Show(_.to_s)
  implicit def showIndex: Show[Index]                         = showBy(_.get)
  implicit def showNth: Show[Nth]                             = showBy[Nth](_.nth)
  implicit def showOption[A: Show] : Show[Option[A]]          = Show(_.fold("-")(_.to_s))
  implicit def showStackTraceElement: Show[StackTraceElement] = Show("\tat" + _ + "\n")
  implicit def showPair[A: Show, B: Show] : Show[A -> B]      = Show(x => x._1 ~ " -> " ~ x._2 to_s)

  implicit def showSize: Show[Size] = Show[Size] {
    case IntSize(size)         => show"$size"
    case LongSize(size)        => show"$size"
    case Bounded(lo, Infinite) => show"$lo+"
    case Bounded(lo, hi)       => show"[$lo,$hi]"
    case Infinite              => "<inf>"
  }
}

trait ShowEach0 {
  implicit def showView[A: Show](implicit z: ShowCollections): Show[View[A]] = Show(xs => z showEach xs.toEach)
  implicit def showEach[A: Show](implicit z: ShowCollections): Show[Each[A]] = Show(xs => z showEach xs)
}
trait ShowEach1 extends ShowEach0 {
  implicit def showZipped[A1: Show, A2: Show](implicit z: ShowCollections): Show[ZipView[A1, A2]]              = Show(z.showZipped[A1, A2])
  implicit def showMap[K: Show, V: Show, CC[X, Y] <: InMap[X, Y]](implicit z: ShowCollections): Show[CC[K, V]] = Show(z.showMap[K, V])
  implicit def showSet[A: Show, CC[X] <: InSet[X]](implicit z: ShowCollections): Show[CC[A]]                   = Show(z.showSet[A])
  implicit def showJava [A: Show, CC[X] <: jIterable[X]](implicit z: ShowCollections): Show[CC[A]]             = Show(z.showJava[A])
  implicit def showScala[A: Show, CC[X] <: sCollection[X]](implicit z: ShowCollections): Show[CC[A]]           = Show(z.showScala[A])
}
trait ShowEach extends ShowEach1 {
  implicit def showJavaEnum[A <: jEnum[A]] : Show[jEnum[A]]                    = Show.natural()
  implicit def showArray[A: Show](implicit z: ShowCollections): Show[Array[A]] = showBy[Array[A]](Direct.fromArray)
}
