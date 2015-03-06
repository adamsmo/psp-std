package psp
package std

import api._, StdEq._

/** Wrapping java classes.
 */
object NullClassLoader extends jClassLoader
object NullInputStream extends InputStream { def read(): Int = -1 }

/** Wrapper for java.lang.Class and other java friends.
 */
final class JavaClass(val clazz: jClass) extends AnyVal with ForceShowDirect {
  private def toPolicy(x: jClass): JavaClass = new JavaClass(x)
  def javaClass: JavaClass = this // implicit creation hook

  def isAnnotation     = clazz.isAnnotation
  def isAnonymousClass = clazz.isAnonymousClass
  def isArray          = clazz.isArray
  def isEnum           = clazz.isEnum
  def isInterface      = clazz.isInterface
  def isLocalClass     = clazz.isLocalClass
  def isMemberClass    = clazz.isMemberClass
  def isPrimitive      = clazz.isPrimitive
  def isSynthetic      = clazz.isSynthetic

  def ancestorNames: Direct[String]         = ancestors map (_.rawName)
  def ancestors: Direct[JavaClass]          = this transitiveClosure (_.parents) toDirect
  def exists: Boolean                       = clazz != null
  def fields: Direct[jField]                = clazz.getFields.toDirect
  def getCanonicalName: String              = clazz.getCanonicalName
  def getClassLoader: ClassLoader           = clazz.getClassLoader
  def getClasses: Direct[JavaClass]         = clazz.getClasses.toDirect map toPolicy
  def getComponentType: JavaClass           = clazz.getComponentType
  def getDeclaredClasses: Direct[JavaClass] = clazz.getDeclaredClasses.toDirect map toPolicy
  def getDeclaringClass: JavaClass          = clazz.getDeclaringClass
  def getEnclosingClass: JavaClass          = clazz.getEnclosingClass
  def getInterfaces: Direct[JavaClass]      = clazz.getInterfaces.toDirect map toPolicy
  def getSuperclass: Option[JavaClass]      = Option(clazz.getSuperclass) map toPolicy
  def hasModuleName: Boolean                = rawName endsWith "$"
  def methods: Direct[jMethod]              = clazz.getMethods.toDirect
  def nameSegments: Direct[String]          = rawName.dottedSegments
  def parentInterfaces: Direct[JavaClass]   = clazz.getInterfaces.toDirect map toPolicy
  def parents: Direct[JavaClass]            = getSuperclass.toDirect ++ parentInterfaces
  def qualifiedName: String                 = rawName.mapSplit('.')(decodeName)
  def rawName: String                       = clazz.getName
  def shortName: String                     = unqualifiedName
  def to_s: String                          = s"$clazz"
  def unqualifiedName: String               = decodeName(nameSegments.last)
}