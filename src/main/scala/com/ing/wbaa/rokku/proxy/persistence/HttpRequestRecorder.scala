package com.ing.wbaa.rokku.proxy.persistence

import akka.http.scaladsl.model.{ HttpRequest, RemoteAddress }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer }
import com.ing.wbaa.rokku.proxy.data.User
import com.ing.wbaa.rokku.proxy.persistence.HttpRequestRecorder.{ ExecutedRequestCmd, LatestRequests, Shutdown }
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

sealed trait Evt
case class ExecutedRequestEvt(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress) extends Evt

object HttpRequestRecorder {
  case class ExecutedRequestCmd(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)
  case class LatestRequests(amount: Int)
  case object Shutdown
}

case class CurrentRequestsState(requests: List[ExecutedRequestEvt] = Nil) {
  def add(e: ExecutedRequestEvt): CurrentRequestsState = {
    if (size > 200) { copy(requests.reverse.drop(100)) }
    copy(e :: requests)
  }
  def getRequests(n: Int = 100): List[ExecutedRequestEvt] = requests.reverse.take(n)
  def size: Int = requests.size
}

class HttpRequestRecorder extends PersistentActor with LazyLogging {
  var state: CurrentRequestsState = CurrentRequestsState()
  val snapShotInterval = ConfigFactory.load().getInt("rokku.requestPersistence.snapshotInterval")

  override def persistenceId: String = ConfigFactory.load().getString("rokku.requestPersistence.persistenceId")

  override def receiveRecover: Receive = {
    case e: ExecutedRequestEvt => {
      logger.debug("No snapshot, replying event sequence {}", lastSequenceNr)
      state.add(e)
    }
    case SnapshotOffer(metadata, snapshot: CurrentRequestsState) => {
      logger.debug("Received snapshot offer, timestamp: {} for persistenceId: {} ", metadata.timestamp, metadata.persistenceId)
      state = snapshot
    }
    case RecoveryCompleted => logger.debug("Actor State recovery completed!")
  }

  override def receiveCommand: Receive = {
    case SaveSnapshotSuccess(metadata)  => logger.debug("Snapshot saved successfully, seq: {}", metadata.sequenceNr)

    case SaveSnapshotFailure(_, reason) => logger.error("Failed to save snapshot, reason: {}", reason.getMessage)

    case rc: ExecutedRequestCmd =>
      persist(ExecutedRequestEvt(rc.httpRequest, rc.userSTS, rc.clientIPAddress)) { e =>
        logger.debug("Received event for event sourcing {} from user: {}", e.httpRequest.uri, e.userSTS.userName)
        if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
          saveSnapshot(state)
      }

    case get: LatestRequests => sender() ! state.getRequests(get.amount)

    case Shutdown            => context.stop(self)

    case _                   => logger.debug(s"{} Got unsupported message type", HttpRequestRecorder.getClass.getName)
  }

}
