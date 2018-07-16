package nl.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.providers.{ AuthenticationProvider, AuthorizationProvider }

import scala.concurrent.{ ExecutionContextExecutor, Future }

object S3Proxy extends App
  with LazyLogging
  with AuthenticationProvider
  with AuthorizationProvider
  with RequestHandler {

  // TODO: centralise configuration
  private val configProxy = ConfigFactory.load().getConfig("proxy.server")
  val proxyInterface = configProxy.getString("interface")
  val proxyPort = configProxy.getInt("port")
  private val configS3 = ConfigFactory.load().getConfig("s3.server")
  val s3Host = configS3.getString("host")
  val s3Port = configS3.getInt("port")

  implicit val system: ActorSystem = ActorSystem.create("gargoyle-s3proxy")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val serverSource = Http().bind(interface = proxyInterface, port = proxyPort)

  // TODO: review whether it's better to use high level Akka API (routes)
  val requestProcessor: HttpRequest => Future[HttpResponse] = htr =>
    isAuthenticated("accesskey", Some("token")).flatMap {
      case None => Future(HttpResponse(StatusCodes.ProxyAuthenticationRequired))
      case Some(secret) =>
        if (validateUserRequest(htr, secret))
          isAuthorized("accessMode", "path", "username")
        else Future(HttpResponse(StatusCodes.BadRequest))
    }.flatMap {
      case false => Future(HttpResponse(StatusCodes.Unauthorized))
      case true =>
        println(s"OLD: $htr")
        val newHtr = htr.copy(uri = htr.uri.withAuthority(s3Host, s3Port))
        println(s"NEW: $newHtr")

        Http().singleRequest(translateRequest(newHtr))
    }

  val bindingFuture: Future[Http.ServerBinding] = {
    println("Server has started")
    serverSource.to(Sink.foreach { connection =>
      println("Accepted new connection from " + connection.remoteAddress)

      connection handleWithAsyncHandler requestProcessor
    }).run()
  }
}
