package com.ing.wbaa.rokku.proxy.persistence

import akka.http.scaladsl.model.{ HttpRequest, RemoteAddress }
import akka.persistence.{ PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer }
import com.ing.wbaa.rokku.proxy.data.User
import com.ing.wbaa.rokku.proxy.persistence.HttpRequestRecorder.{ ExecutedRequestCmd, Shutdown }
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

sealed trait Evt
case class ExecutedRequestEvt(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress) extends Evt

object HttpRequestRecorder {
  case class ExecutedRequestCmd(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)
  case object Shutdown
}

class HttpRequestRecorder extends PersistentActor with LazyLogging {
  var state: Any = _

  private def configuredPersistenceId = ConfigFactory.load().getString("rokku.requestPersistence.persistenceId")

  override def persistenceId: String = configuredPersistenceId

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snapshot) => state = snapshot
  }

  val snapShotInterval = 5 //todo: add as param
  override def receiveCommand: Receive = {
    case SaveSnapshotSuccess(metadata)  => logger.debug("{} snapshot saved successfully, seq: {}", HttpRequestRecorder.getClass.getName, metadata.sequenceNr)
    case SaveSnapshotFailure(_, reason) => logger.error("{} failed to save snapshot, reason: {}", HttpRequestRecorder.getClass.getName, reason.getMessage)
    case rc: ExecutedRequestCmd =>
      persist(ExecutedRequestEvt(rc.httpRequest, rc.userSTS, rc.clientIPAddress)) { e =>
        logger.debug("Received event for event sourcing {} from user: {}", e.httpRequest.uri, e.userSTS.userName)
        if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
          saveSnapshot(state)
      }
    case Shutdown =>
      context.stop(self)
    case _ => logger.debug(s"{} Got unsupported message type", HttpRequestRecorder.getClass.getName)
  }

}
