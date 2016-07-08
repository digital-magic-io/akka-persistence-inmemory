/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.inmemory.extension

import akka.actor.{ Actor, ActorLogging, ActorRef }

object InMemorySnapshotStorage {
  case class SnapshotData(persistenceId: String, sequenceNumber: Long, created: Long, snapshot: Array[Byte])

  // Success
  case class Delete(persistenceId: String, sequenceNr: Long)

  // Success
  case class DeleteAllSnapshots(persistenceId: String)

  // Success
  case class DeleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long)

  // Success
  case class DeleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long)

  // Success
  case class DeleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long)

  // Success
  case object Clear

  // Success
  case class Save(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: Array[Byte])

  // [Option[SnapshotData]]
  case class SnapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long)

  // [Option[SnapshotData]]
  case class SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, sequenceNr: Long, timestamp: Long)

  // [Option[SnapshotData]]
  case class SnapshotForMaxTimestamp(persistenceId: String, timestamp: Long)
}

class InMemorySnapshotStorage extends Actor with ActorLogging {
  import InMemorySnapshotStorage._

  var snapshot = Map.empty[String, Vector[SnapshotData]]

  def clear(ref: ActorRef): Unit = {
    snapshot = Map.empty[String, Vector[SnapshotData]]
    ref ! akka.actor.Status.Success("")
  }

  def delete(persistenceId: String, predicate: SnapshotData ⇒ Boolean): Unit = {
    import scalaz._
    import Scalaz._
    val pidEntries = snapshot.filter(_._1 == persistenceId)
    val notDeleted = pidEntries.mapValues(_.filterNot(predicate))
    snapshot = snapshot.filterNot(_._1 == persistenceId) |+| notDeleted
  }

  def delete(ref: ActorRef, persistenceId: String, sequenceNr: Long): Unit = {
    delete(persistenceId, _.sequenceNumber == sequenceNr)

    ref ! akka.actor.Status.Success("")
  }

  def deleteUpToMaxSequenceNr(ref: ActorRef, persistenceId: String, maxSequenceNr: Long): Unit = {
    delete(persistenceId, _.sequenceNumber <= maxSequenceNr)

    ref ! akka.actor.Status.Success("")
  }

  def deleteAllSnapshots(ref: ActorRef, persistenceId: String): Unit = {
    snapshot -= persistenceId

    ref ! akka.actor.Status.Success("")
  }

  def deleteUpToMaxTimestamp(ref: ActorRef, persistenceId: String, maxTimestamp: Long): Unit = {
    delete(persistenceId, _.created <= maxTimestamp)

    ref ! akka.actor.Status.Success("")
  }

  def deleteUpToMaxSequenceNrAndMaxTimestamp(ref: ActorRef, persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Unit = {
    delete(persistenceId, (x: SnapshotData) ⇒ x.sequenceNumber <= maxSequenceNr && x.created <= maxTimestamp)

    ref ! akka.actor.Status.Success("")
  }

  def save(ref: ActorRef, persistenceId: String, sequenceNr: Long, timestamp: Long, data: Array[Byte]): Unit = {
    import scalaz._
    import Scalaz._
    val key = persistenceId
    snapshot = snapshot |+| Map(key → Vector(SnapshotData(persistenceId, sequenceNr, timestamp, data)))

    ref ! akka.actor.Status.Success("")
  }

  def snapshotForMaxSequenceNr(ref: ActorRef, persistenceId: String, sequenceNr: Long): Unit = {
    val determine = snapshot.get(persistenceId).flatMap(xs ⇒ xs.filter(_.sequenceNumber <= sequenceNr).toList.sortBy(_.sequenceNumber).reverse.headOption)

    ref ! akka.actor.Status.Success(determine)
  }

  def snapshotFor(ref: ActorRef, persistenceId: String)(p: SnapshotData ⇒ Boolean): Unit = {
    val determine: Option[SnapshotData] = snapshot.get(persistenceId).flatMap(_.find(p))

    ref ! akka.actor.Status.Success(determine)
  }

  def snapshotForMaxSequenceNrAndMaxTimestamp(ref: ActorRef, persistenceId: String, sequenceNr: Long, timestamp: Long): Unit = {
    snapshotFor(ref, persistenceId)(snap ⇒ snap.sequenceNumber == sequenceNr)
  }

  def snapshotForMaxTimestamp(ref: ActorRef, persistenceId: String, timestamp: Long): Unit =
    snapshotFor(ref, persistenceId)(_.created < timestamp)

  override def receive: Receive = {
    case Delete(persistenceId: String, sequenceNr: Long)                                                        ⇒ delete(sender(), persistenceId, sequenceNr)
    case DeleteAllSnapshots(persistenceId: String)                                                              ⇒ deleteAllSnapshots(sender(), persistenceId)
    case DeleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long)                                      ⇒ deleteUpToMaxTimestamp(sender(), persistenceId, maxTimestamp)
    case DeleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long)                                    ⇒ deleteUpToMaxSequenceNr(sender(), persistenceId, maxSequenceNr)
    case DeleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long) ⇒ deleteUpToMaxSequenceNrAndMaxTimestamp(sender(), persistenceId, maxSequenceNr, maxTimestamp)
    case Clear                                                                                                  ⇒ clear(sender())
    case Save(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: Array[Byte])                  ⇒ save(sender(), persistenceId, sequenceNr, timestamp, snapshot)
    case SnapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long)                                      ⇒ snapshotForMaxSequenceNr(sender(), persistenceId, sequenceNr)
    case SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, sequenceNr: Long, timestamp: Long)      ⇒ snapshotForMaxSequenceNrAndMaxTimestamp(sender(), persistenceId, sequenceNr, timestamp)
    case SnapshotForMaxTimestamp(persistenceId: String, timestamp: Long)                                        ⇒ snapshotForMaxTimestamp(sender(), persistenceId, timestamp)
  }
}