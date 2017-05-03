package com.outr.arango.managed

import com.outr.arango._
import com.outr.arango.rest.{CreateInfo, GraphResponse, QueryResponse}
import io.circe.{Decoder, Encoder}
import reactify.{Channel, TransformableChannel}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.experimental.macros

trait AbstractCollection[T <: DocumentOption] {
  def graph: Graph
  def name: String
  protected[managed] lazy val collection: ArangoCollection = graph.instance.db.collection(name)

  implicit val encoder: Encoder[T]
  implicit val decoder: Decoder[T]
  protected def updateDocument(document: T, info: CreateInfo): T

  lazy val inserting: TransformableChannel[T] = TransformableChannel[T]
  lazy val inserted: Channel[T] = Channel[T]
  lazy val replacing: TransformableChannel[T] = TransformableChannel[T]
  lazy val replaced: Channel[T] = Channel[T]
  lazy val upserting: TransformableChannel[T] = TransformableChannel[T]
  lazy val upserted: Channel[T] = Channel[T]
  lazy val deleting: TransformableChannel[T] = TransformableChannel[T]
  lazy val deleted: Channel[T] = Channel[T]

  graph.synchronized {
    graph.managedCollections = graph.managedCollections ::: List(this)
  }

  def create(waitForSync: Boolean = false): Future[GraphResponse]
  def delete(): Future[GraphResponse]
  def get(key: String): Future[Option[T]]
  final def apply(key: String): Future[T] = get(key).map(_.getOrElse(throw new RuntimeException(s"Key not found: $key.")))

  def index: ArangoIndexing = collection.index

  def insert(document: T): Future[T] = macro Macros.insert[T]
  def upsert(document: T): Future[T] = macro Macros.upsert[T]
  def replace(document: T): Future[T] = macro Macros.replace[T]
  def delete(document: T): Future[Boolean] = macro Macros.delete[T]

  object managed {
    def insert(document: T): Future[T] = {
      inserting.transform(document) match {
        case Some(modified) => {
          insertInternal(modified).map(updateDocument(document, _)).map { value =>
            inserted := value
            value
          }
        }
        case None => Future.failed(new CancelledException("Insert cancelled."))
      }
    }
    def upsert(document: T): Future[T] = {
      upserting.transform(document) match {
        case Some(modified) => {
          collection.document.upsert(modified)
        }
        case None => Future.failed(new CancelledException("Upsert cancelled."))
      }
    }
    def replace(document: T): Future[T] = {
      replacing.transform(document) match {
        case Some(modified) => {
          replaceInternal(modified).map(_ => modified).map { value =>
            replaced := value
            value
          }
        }
        case None => Future.failed(new CancelledException("Replace cancelled."))
      }
    }
    def delete(document: T): Future[Boolean] = {
      deleting.transform(document) match {
        case Some(modified) => {
          deleteInternal(modified).map { success =>
            deleted := modified
            success
          }
        }
        case None => Future.failed(new CancelledException("Delete cancelled."))
      }
    }
  }
  def cursor(query: Query, batchSize: Int = 100): Future[QueryResponse[T]] = {
    graph.cursor.apply[T](query, count = true, batchSize = Some(batchSize))
  }
  // TODO: support for proper pagination
  def all(batchSize: Int = 100): Future[QueryResponse[T]] = cursor(Query(s"FOR x IN $name RETURN x", Map.empty))

  protected def insertInternal(document: T): Future[CreateInfo]
  protected def replaceInternal(document: T): Future[Unit]
  protected def deleteInternal(document: T): Future[Boolean]
}