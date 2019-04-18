package com.ing.wbaa.rokku.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.ing.wbaa.rokku.proxy.config.StsSettings
import com.ing.wbaa.rokku.proxy.data.{ AwsRequestCredential, JsonProtocols, RequestId, User, UserRawJson }
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.util.JwtToken

import scala.concurrent.{ ExecutionContext, Future }

trait AuthenticationProviderSTS extends JsonProtocols with JwtToken {

  private val logger = new LoggerHandlerWithId

  import AuthenticationProviderSTS.STSException
  import spray.json._

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def materializer: Materializer

  protected[this] def stsSettings: StsSettings

  protected[this] def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]] = {
    val QueryParameters =
      Map("accessKey" -> awsRequestCredential.accessKey.value) ++
        awsRequestCredential.sessionToken.map(s => "sessionToken" -> s.value)

    val uri = stsSettings.stsBaseUri
      .withPath(Uri.Path("/isCredentialActive"))
      .withQuery(Uri.Query(QueryParameters))

    Http()
      .singleRequest(
        HttpRequest(uri = uri).addHeader(RawHeader("Authorization", createInternalToken))
      )
      .flatMap { response =>
        response.status match {

          case StatusCodes.OK =>
            Unmarshal(response.entity).to[String].map { jsonString =>
              Some(User(jsonString.parseJson.convertTo[UserRawJson]))
            }

          case StatusCodes.Forbidden =>
            logger.error(s"User not authenticated " +
              s"with accessKey (${awsRequestCredential.accessKey.value}) " +
              s"and sessionToken (${awsRequestCredential.sessionToken})")
            Future.successful(None)

          case c =>
            val msg = s"Received unexpected StatusCode ($c) for " +
              s"accessKey (${awsRequestCredential.accessKey.value}) " +
              s"and sessionToken (${awsRequestCredential.sessionToken})"
            logger.error(msg)
            Future.failed(STSException(msg))
        }
      }
  }
}

object AuthenticationProviderSTS {
  final case class STSException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
