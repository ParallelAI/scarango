package com.outr.arango.managed

import com.outr.arango.{ArangoEdge, DocumentOption, Edge}
import com.outr.arango.rest.{CreateInfo, GraphResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

abstract class EdgeCollection[T <: Edge with DocumentOption](override val graph: Graph,
                                                             override val name: String,
                                                             from: List[String],
                                                             to: List[String]) extends AbstractCollection[T] {
  private lazy val edge: ArangoEdge = graph.instance.edge(name)

  override def create(waitForSync: Boolean = false): Future[GraphResponse] = edge.create(from, to, waitForSync)
  override def delete(): Future[GraphResponse] = edge.delete(dropCollection = true)

  override def get(key: String): Future[Option[T]] = edge[T](key).map(_.edge)

  override protected def insertInternal(document: T): Future[CreateInfo] = {
    edge.insert[T](document).map(_.edge)
  }

  override protected def replaceInternal(document: T): Future[Unit] = {
    edge.replace[T](document._key.get, document).map(_ => ())
  }

  override protected def deleteInternal(document: T): Future[Boolean] = {
    edge.delete(document._key.get).map(!_.error)
  }
}
