package com.ing.wbaa.airlock.proxy.provider

import akka.Done
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.kafka.EventProducer
import akka.http.scaladsl.model.{ HttpMethod, HttpMethods, RemoteAddress }
import com.ing.wbaa.airlock.proxy.provider.aws.{ s3ObjectCreated, s3ObjectRemoved }

import scala.concurrent.Future

trait MessageProviderKafka extends EventProducer with AWSMessageEventJsonSupport {

  private def incompleteData: Future[Nothing] = Future.failed(new Exception("Cannot send event to kafka, not enough input data"))

  def emitEvent(s3Request: S3Request, method: HttpMethod, principalId: String, clientIPAddress: RemoteAddress): Future[Done] =
    method match {
      case HttpMethods.POST | HttpMethods.PUT =>
        prepareAWSMessage(s3Request, method, principalId, clientIPAddress, s3ObjectCreated(method.value))
          .map { case jse =>
            sendSingleMessage(jse.toString(), kafkaSettings.createEventsTopic)
          }
          .getOrElse(incompleteData)

      case HttpMethods.DELETE =>
        prepareAWSMessage(s3Request, method, principalId, clientIPAddress, s3ObjectRemoved(method.value))
          .map { case jse =>
            sendSingleMessage(jse.toString(), kafkaSettings.deleteEventsTopic)
          }
          .getOrElse(incompleteData)

      case _ => incompleteData
    }

}
