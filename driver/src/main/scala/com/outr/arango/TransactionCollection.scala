package com.outr.arango

import com.outr.arango.transaction.Transaction

<<<<<<< HEAD
class TransactionCollection[D <: Document[D]](override val collection: Collection[D], val currentTransaction: Transaction, val replicationFactor: Long) extends WrappedCollection[D] {
=======
class TransactionCollection[D <: Document[D]](override val collection: Collection[D],
                                              val currentTransaction: Transaction) extends WrappedCollection[D] {
  override def options: CollectionOptions = collection.options

>>>>>>> upstream/master
  override def transaction: Option[Transaction] = Some(currentTransaction)

  override protected def addCollection(): Unit = {
    // Don't auto-add
  }
}