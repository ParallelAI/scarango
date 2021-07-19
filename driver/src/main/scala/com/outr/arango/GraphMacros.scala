package com.outr.arango

import scala.annotation.compileTimeOnly
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macro paradise to expand compile-time macros")
object GraphMacros {
  def store[T](c: blackbox.Context)
              (key: c.Expr[String])
              (implicit t: c.WeakTypeTag[T]): c.Expr[DatabaseStore[T]] = {
    import c.universe._

    val graph = c.prefix
    val tree =
      q"""
         DatabaseStore[$t]($key, $graph, Serialization.auto[$t])
       """
    c.Expr[DatabaseStore[T]](tree)
  }

  def queryBuilderAs[D](c: blackbox.Context)(implicit d: c.WeakTypeTag[D]): c.Expr[QueryBuilder[D]] = {
    import c.universe._

    val builder = c.prefix
    if (d.tpe <:< typeOf[Document[_]] && d.tpe.companion <:< typeOf[DocumentModel[_]]) {
      c.Expr[QueryBuilder[D]](q"$builder.as[$d](${d.tpe.typeSymbol.companion}.serialization)")
    } else {
      c.Expr[QueryBuilder[D]](q"$builder.as[$d](_root_.com.outr.arango.Serialization.auto[$d])")
    }
  }

  def vertex[D <: Document[D]](c: blackbox.Context)()(implicit d: c.WeakTypeTag[D]): c.Expr[DocumentCollection[D]] = {
    import c.universe._

    val graph = c.prefix
    vertexOptions[D](c)(c.Expr[CollectionOptions](q"$graph.defaultCollectionOptions"))(d)
  }

  def vertexOptions[D <: Document[D]](c: blackbox.Context)(options: c.Expr[CollectionOptions])
                              (implicit d: c.WeakTypeTag[D]): c.Expr[DocumentCollection[D]] = {
    import c.universe._

    val graph = c.prefix
    val companion = d.tpe.typeSymbol.companion
    c.Expr[DocumentCollection[D]](
      q"""
         import com.outr.arango._

<<<<<<< HEAD
         new DocumentCollection[$d]($graph, $companion, CollectionType.Document, $companion.indexes, None, 3L)
=======
         new DocumentCollection[$d]($graph, $companion, CollectionType.Document, $companion.indexes, None, $options)
>>>>>>> upstream/master
       """)
  }

  def edge[D <: Document[D]](c: blackbox.Context)()(implicit d: c.WeakTypeTag[D]): c.Expr[DocumentCollection[D]] = {
    import c.universe._

    val graph = c.prefix
    edgeOptions[D](c)(c.Expr[CollectionOptions](q"$graph.defaultCollectionOptions"))(d)
  }

  def edgeOptions[D <: Document[D]](c: blackbox.Context)(options: c.Expr[CollectionOptions])
                            (implicit d: c.WeakTypeTag[D]): c.Expr[DocumentCollection[D]] = {
    import c.universe._

    val graph = c.prefix
    val companion = d.tpe.typeSymbol.companion
    c.Expr[DocumentCollection[D]](
      q"""
         import com.outr.arango._

<<<<<<< HEAD
         new DocumentCollection[$d]($graph, $companion, CollectionType.Edge, $companion.indexes, None, 3L)
=======
         new DocumentCollection[$d]($graph, $companion, CollectionType.Edge, $companion.indexes, None, $options)
>>>>>>> upstream/master
       """)
  }
}