package com.outr.arango

import io.youi.Unique

trait DocumentModel[D <: Document[D]] {
  val collectionName: String
  implicit val serialization: Serialization[D]

  protected def generateId(): String = Unique()

  def id(value: String = generateId()): Id[D] = if (value.startsWith(collectionName)) {
    Id[D](value.substring(collectionName.length + 1), collectionName)
  } else {
    Id[D](value, collectionName)
  }
}