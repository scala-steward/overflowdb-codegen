package overflowdb.codegen

import java.lang.System.lineSeparator
import overflowdb.algorithm.LowestCommonAncestors
import overflowdb.schema._
import overflowdb.schema.Property.ValueType

import scala.annotation.tailrec

object DefaultNodeTypes {
  /** root type for all nodes */
  val AbstractNodeName = "ABSTRACT_NODE"
  val AbstractNodeClassname = "AbstractNode"

  val StoredNodeName = "STORED_NODE"
  val StoredNodeClassname = "StoredNode"

  lazy val AllClassNames = Set(AbstractNodeClassname, StoredNodeClassname)
}

object Helpers {

  /* surrounds input with `"` */
  def quoted(strings: Iterable[String]): Iterable[String] =
    strings.map(quote)

  def quote(string: String): String =
    s""""$string""""

  def stringToOption(s: String): Option[String] = s.trim match {
    case "" => None
    case nonEmptyString => Some(nonEmptyString)
  }

  def typeFor[A](property: Property[A], nullable: Boolean = false): String = {
    property.valueType match {
      case ValueType.Boolean => if (nullable) "java.lang.Boolean" else "Boolean"
      case ValueType.String => "String"
      case ValueType.Byte => if (nullable) "java.lang.Byte" else "Byte"
      case ValueType.Short => if (nullable) "java.lang.Short" else "Short"
      case ValueType.Int => if (nullable) "Integer" else "scala.Int"
      case ValueType.Long => if (nullable) "java.lang.Long" else "Long"
      case ValueType.Float => if (nullable) "java.lang.Float" else "Float"
      case ValueType.Double => if (nullable) "java.lang.Double" else "Double"
      case ValueType.Char => if (nullable) "Character" else "scala.Char"
      case ValueType.List => "Seq[_]"
      case ValueType.NodeRef => "overflowdb.NodeRef[_]"
      case ValueType.Unknown => "java.lang.Object"
    }
  }

  def accessorName(neighborInfoForNode: NeighborInfoForNode): String = {
    neighborInfoForNode.customStepName.getOrElse {
      val neighborNodeName = neighborInfoForNode.neighborNode.name
      val edgeName = neighborInfoForNode.edge.className
      val direction = neighborInfoForNode.direction.toString
       s"_${camelCase(neighborNodeName)}Via$edgeName${camelCaseCaps(direction)}"
    }
  }

  def docAnnotationMaybe(customStepDoc: Option[String]): String = {
    customStepDoc.map(escapeJava) match {
      case Some(doc) =>
        s"""/** $doc */
           |@overflowdb.traversal.help.Doc(info = \"\"\"$doc\"\"\")""".stripMargin
      case None => ""
    }
  }

  /** escape things like quotes, backslashes, end of comment ('* /' without the space) etc. */
  def escapeJava(src: String): String = {
    src
      .replace("\"", "\\\"")
      .replace("/*", "\\/\\*")
      .replace("*/", "\\*\\/")
  }

  def isNodeBaseTrait(baseTraits: Seq[NodeBaseType], nodeName: String): Boolean =
    nodeName == DefaultNodeTypes.AbstractNodeName || baseTraits.map(_.name).contains(nodeName)

  def camelCaseCaps(snakeCase: String): String = camelCase(snakeCase).capitalize

  def camelCase(snakeCase: String): String = {
    val corrected = // correcting for internal keys, like "_KEY" -> drop leading underscore
      if (snakeCase.startsWith("_")) snakeCase.drop(1)
      else snakeCase

    val elements: Seq[String] = corrected.split("_").map(_.toLowerCase).toList match {
      case head :: tail => head :: tail.map(_.capitalize)
      case Nil          => Nil
    }
    elements.mkString
  }

  /**
   * Converts from camelCase to snake_case
   * e.g.: camelCase => camel_case
   *
   * copy pasted from https://gist.github.com/sidharthkuruvila/3154845#gistcomment-2622928
   */
  def snakeCase(camelCase: String): String = {
    @tailrec
    def go(accDone: List[Char], acc: List[Char]): List[Char] = acc match {
      case Nil => accDone
      case a::b::c::tail if a.isUpper && b.isUpper && c.isLower => go(accDone ++ List(a, '_', b, c), tail)
      case a::b::tail if a.isLower && b.isUpper => go(accDone ++ List(a, '_', b), tail)
      case a::tail => go(accDone :+ a, tail)
    }
    go(Nil, camelCase.toList).mkString.toLowerCase
  }

  def singularize(str: String): String = {
    if (str.endsWith("ies")) {
      // e.g. Strategies -> Strategy
      s"${str.dropRight(3)}y"
    } else {
      // e.g. Types -> Type
      str.dropRight(1)
    }
  }

  def getCompleteType[A](property: Property[?], nullable: Boolean = true): String =
    getCompleteType(property.cardinality, typeFor(property, nullable))

  def typeFor(containedNode: ContainedNode): String = {
    containedNode.nodeType match {
      case anyNode: AnyNodeType => "AbstractNode"
      case nodeType =>
        val className = containedNode.nodeType.className
        if (DefaultNodeTypes.AllClassNames.contains(className)) className
        else className + "Base"
    }
  }

  def getCompleteType(containedNode: ContainedNode): String =
    getCompleteType(containedNode.cardinality, typeFor(containedNode))

  def getCompleteType(cardinality: Property.Cardinality, valueType: String): String = {
    import Property.Cardinality
    cardinality match {
      case Cardinality.One(_)    => valueType
      case Cardinality.ZeroOrOne => s"Option[$valueType]"
      case Cardinality.List      => s"IndexedSeq[$valueType]"
    }
  }

  def propertyKeyDef(name: String, baseType: String, cardinality: Property.Cardinality) = {
    val completeType = cardinality match {
      case Property.Cardinality.One(_)    => baseType
      case Property.Cardinality.ZeroOrOne => baseType
      case Property.Cardinality.List      => s"IndexedSeq[$baseType]"
    }
    s"""val ${camelCaseCaps(name)} = new overflowdb.PropertyKey[$completeType]("$name") """
  }

  def defaultValueImpl[A](default: Property.Default[A]): String =
    default.value match {
      case str: String => s"$quotes$str$quotes"
      case char: Char => s"'$char'"
      case byte: Byte => s"$byte: Byte"
      case short: Short => s"$short: Short"
      case int: Int => s"$int: Int"
      case long: Long => s"$long: Long"
      case float: Float if float.isNaN => "Float.NaN"
      case float: Float => s"${float}f"
      case double: Double if double.isNaN => "Double.NaN"
      case double: Double => s"${double}d"
      case other => s"$other"
    }

  def defaultValueCheckImpl[A](memberName: String, default: Property.Default[A]): String = {
    val defaultValueSrc = defaultValueImpl(default)
    default.value match {
      case float: Float if float.isNaN => s"$memberName.isNaN"
      case double: Double if double.isNaN => s"$memberName.isNaN"
      case _ => s"($defaultValueSrc) == $memberName"
    }
  }

  def propertyDefaultValueImpl(propertyDefaultsPath: String, properties: Seq[Property[?]]): String = {
    val propertyDefaultValueCases = properties.collect {
      case property if property.hasDefault =>
        s"""case "${property.name}" => $propertyDefaultsPath.${property.className}"""
    }.mkString(lineSeparator)

    s"""override def propertyDefaultValue(propertyKey: String) = {
       |  propertyKey match {
       |    $propertyDefaultValueCases
       |    case _ => super.propertyDefaultValue(propertyKey)
       |  }
       |}
       |""".stripMargin
  }

  def propertyDefaultCases(properties: Seq[Property[?]]): String = {
    properties.collect {
      case p if p.hasDefault =>
        s"""val ${p.className} = ${defaultValueImpl(p.default.get)}"""
    }.mkString(s"$lineSeparator|    ")
  }

  def propertyAccessors(properties: Seq[Property[?]], nullable: Boolean = true): String = {
    properties.map { property =>
      val camelCaseName = camelCase(property.name)
      val tpe = getCompleteType(property, nullable = nullable)
      s"def $camelCaseName: $tpe"
    }.mkString(lineSeparator)
  }

  val propertyErrorRegisterImpl =
    s"""object PropertyErrorRegister {
       |  private var errorMap = Set[(Class[?], String)]()
       |  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
       |
       |  def logPropertyErrorIfFirst(clazz: Class[?], propertyName: String): Unit = {
       |    if (!errorMap.contains((clazz, propertyName))) {
       |      logger.warn("Property " + propertyName + " is deprecated for " + clazz.getName + ".")
       |      errorMap += ((clazz, propertyName))
       |    }
       |  }
       |}
       |""".stripMargin

  /** obtained from repl via
   * {{{
   * :power
   * nme.keywords
   * }}}
   */
  val scalaReservedKeywords = Set(
    "abstract", ">:", "if", ".", "catch", "protected", "final", "super", "while", "true", "val", "do", "throw",
    "<-", "package", "_", "macro", "@", "object", "false", "this", "then", "var", "trait", "with", "def", "else",
    "class", "type", "#", "lazy", "null", "=", "<:", "override", "=>", "private", "sealed", "finally", "new",
    "implicit", "extends", "for", "return", "case", "import", "forSome", ":", "yield", "try", "match", "<%")

  def escapeIfKeyword(value: String) =
    if (scalaReservedKeywords.contains(value)) s"`$value`"
    else value

  def fullScalaType(neighborNode: AbstractNodeType, cardinality: EdgeType.Cardinality): String = {
    val neighborNodeClass = neighborNode.className
    cardinality match {
      case EdgeType.Cardinality.List => s"overflowdb.traversal.Traversal[$neighborNodeClass]"
      case EdgeType.Cardinality.ZeroOrOne => s"Option[$neighborNodeClass]"
      case EdgeType.Cardinality.One => s"$neighborNodeClass"
    }
  }

  def deriveCommonRootType(neighborNodeInfos: Set[AbstractNodeType]): Option[AbstractNodeType] = {
    lowestCommonAncestor(neighborNodeInfos)
      .orElse(findSharedRoot(neighborNodeInfos))
  }

  /** In theory there can be multiple candidates - we're just returning one of those for now.
   * We want the results to be stable between different codegen runs, so we simply return the first
   * in alphabetical order... */
  def lowestCommonAncestor(nodes: Set[AbstractNodeType]): Option[AbstractNodeType] = {
    LowestCommonAncestors(nodes)(_.extendzRecursively.toSet).toSeq.sortBy(_.name).headOption
  }

  /** from the given node types, find one that is part of the complete type hierarchy of *all* other node types */
  def findSharedRoot(nodeTypes: Set[AbstractNodeType]): Option[AbstractNodeType] = {
    if (nodeTypes.size == 1) {
      Some(nodeTypes.head)
    } else if (nodeTypes.size > 1) {
      // trying to keep it deterministic...
      val sorted = nodeTypes.toSeq.sortBy(_.className)
      val (first, otherNodes) = (sorted.head, sorted.tail)
      completeTypeHierarchy(first).find { candidate =>
        otherNodes.forall { otherNode =>
          completeTypeHierarchy(otherNode).contains(candidate)
        }
      }
    } else {
      None
    }
  }

  def completeTypeHierarchy(node: AbstractNodeType): Seq[AbstractNodeType] =
    node +: node.extendzRecursively

  val quotes = '"'
}
