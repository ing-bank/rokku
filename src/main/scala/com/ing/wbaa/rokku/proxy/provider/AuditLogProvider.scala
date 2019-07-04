package com.ing.wbaa.rokku.proxy.provider

import akka.Done
import akka.http.scaladsl.model.HttpRequest
import com.ing.wbaa.rokku.proxy.data.{ AWSMessageEventJsonSupport, RequestId, S3Request }
import com.ing.wbaa.rokku.proxy.provider.aws.s3ObjectAudit
import com.ing.wbaa.rokku.proxy.provider.kafka.EventProducer
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

trait AuditLogProvider extends EventProducer with AWSMessageEventJsonSupport {

  protected[this] def auditEnabled = ConfigFactory.load().getBoolean("rokku.auditEnable")

  def auditLog(s3Request: S3Request, httpRequest: HttpRequest, user: String)(implicit id: RequestId): Future[Done] = {

    if (auditEnabled) {
      prepareAWSMessage(s3Request, httpRequest.method, user, s3Request.clientIPAddress, s3ObjectAudit(httpRequest.method.value))
        .map(jse =>
          sendSingleMessage(jse.toString(), kafkaSettings.auditEventsTopic))
        .getOrElse(Future(Done))
    } else {
      Future(Done)
    }
  }
}
