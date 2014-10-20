package psp
package std
package ops

import api.Eq

/** "Extensions" are classes which only exist to add methods to
 *  built-in types from the scala standard library. As we phase
 *  out the use of the standard library these will migrate into
 *  "Ops" classes, where we control the underlying class.
 */
final class Function1Ops[T, R](val f: T => R) extends AnyVal {
  def |:(label: String): LabeledFunction[T, R] = new LabeledFunction(f, label)
  def :|(label: String): LabeledFunction[T, R] = new LabeledFunction(f, label)

  def map[S](g: R => S): T => S                          = f andThen g
  def comap[S](g: S => T): S => R                        = g andThen f
  def sameAt(g: T => R)(implicit z: Eq[R]): Predicate[T] = x => f(x) === g(x)
  def on[S](g: (R, R) => S): (T, T) => S                 = (x, y) => g(f(x), f(y))
}
final class Function2Ops[T1, T2, R](val f: (T1, T2) => R) extends AnyVal {
  def andThen[S](g: R => S): (T1, T2) => S = (x, y) => g(f(x, y))
  def map[S](g: R => S): (T1, T2) => S     = (x, y) => g(f(x, y))
  def comap1[P](g: P => T1): (P, T2) => R  = (x, y) => f(g(x), y)
  def comap2[P](g: P => T2): (T1, P) => R  = (x, y) => f(x, g(y))
}
final class BiFunctionOps[T, R](val f: (T, T) => R) extends AnyVal {
  // def on[S](g: S => T): (S, S) => R = (x, y) => f(g(x), g(y))
}

final class OptionOps[A](val x: Option[A]) extends AnyVal {
  def | (alt: => A): A                             = x getOrElse alt
  def ||(alt: => A): Option[A]                     = x orElse Some(alt)
  def |?[A1 >: A](alt: => A1): A1                  = x getOrElse alt
  def ||?[A1 >: A](alt: => Option[A1]): Option[A1] = x orElse alt

  def pvec: pVector[A] = if (x.isEmpty) Direct() else Direct(x.get)
}

final class TryOps[A](val x: Try[A]) extends AnyVal {
  def | (expr: => A): A = x match {
    case Failure(_) => expr
    case Success(x) => x
  }
  def || (expr: => A): Try[A] = x match {
    case x @ Success(_) => x
    case Failure(_)     => Try(expr)
  }
  def fold[B](f: A => B, g: Throwable => B): B = x match {
    case Success(x) => f(x)
    case Failure(t) => g(t)
  }
}

/*** Java ***/

final class FileTimeOps(val time: jFileTime) extends AnyVal {
  def isNewer(that: jFileTime) = (time compareTo that) > 0
  def isOlder(that: jFileTime) = (time compareTo that) < 0
  def isSame(that: jFileTime)  = (time compareTo that) == 0
}

final class ClassLoaderOps(val loader: jClassLoader) extends ClassLoaderTrait

final class ClassOps(val clazz: jClass) extends AnyVal {
  private def transitiveClosure[A: Eq](root: A)(f: A => pVector[A]): pVector[A] = {
    val buf  = vectorBuilder[A]()
    val seen = scmSet[A]()
    def loop(root: A): Unit = if (!seen(root)) {
      seen += root
      buf += root
      f(root) foreach loop
    }
    loop(root)
    buf.result.pvec
  }
  def ancestorNames: pVector[String]    = ancestors map (_.rawName)
  def ancestors: pVector[jClass]        = transitiveClosure[jClass](clazz)(_.parents)(Eq.natural())
  def fields: pVector[jField]           = clazz.getFields.pvec
  def hasModuleName: Boolean            = rawName endsWith "$"
  def methods: pVector[jMethod]         = clazz.getMethods.pvec
  def nameSegments: pVector[String]     = rawName.dottedSegments
  def parentInterfaces: pVector[jClass] = clazz.getInterfaces.pvec
  def parents: pVector[jClass]          = superClass.pvec ++ parentInterfaces
  def qualifiedName: String             = rawName.mapSplit('.')(decodeName)
  def rawName: String                   = clazz.getName
  def shortName: String                 = unqualifiedName
  def superClass: Option[jClass]        = Option(clazz.getSuperclass)
  def unqualifiedName: String           = decodeName(nameSegments.last)
}
