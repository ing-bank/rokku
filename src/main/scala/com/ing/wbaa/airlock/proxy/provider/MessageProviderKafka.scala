package com.ing.wbaa.airlock.proxy.provider

import akka.Done
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.kafka.EventProducer
import akka.http.scaladsl.model.{ HttpMethod, HttpMethods, RemoteAddress }

import scala.concurrent.Future

trait MessageProviderKafka extends EventProducer with AWSMessageEventJsonSupport {

  private def incompeteData: Future[Nothing] = Future.failed(throw new Exception("Cannot send event to kafka, not enough input data"))

  def emitEvent(s3Request: S3Request, method: HttpMethod, principalId: String, clientIPAddress: RemoteAddress): Future[Done] =
    method match {
      case HttpMethods.POST | HttpMethods.PUT =>
        prepareAWSMessage(s3Request, method, principalId, clientIPAddress)
          .map { case jse =>
            simpleEmit(jse.toString(), kafkaSettings.createTopic)
          }
          .getOrElse(incompeteData)

      case HttpMethods.DELETE =>
        prepareAWSMessage(s3Request, method, principalId, clientIPAddress)
          .map { case jse =>
            simpleEmit(jse.toString(), kafkaSettings.deleteTopic)
          }
          .getOrElse(incompeteData)

      case _ => incompeteData
    }

}
