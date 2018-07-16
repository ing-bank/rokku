package nl.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.providers.{AuthenticationProvider, AuthorizationProvider}

import scala.concurrent.Future

class S3Proxy(port: Int)(implicit system: ActorSystem = ActorSystem.create("gargoyle-s3proxy")) extends LazyLogging
  with AuthenticationProvider
  with AuthorizationProvider
  with RequestHandler {

  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  /* ------------------------ PREVIOUS SOLUTION ---------------------------------
  private var bind: Http.ServerBinding = _

  def start(): Http.ServerBinding = {
    implicit val mat = ActorMaterializer()
    val http = Http(system)

    val allRoutes =
      // concat new routes here
        GetRoute().route() ~
            PostRoute().route() ~
            DeleteRoute().route()
            PutRoute().route()

    // interface 0.0.0.0 needed in case of docker

    // no debug
    //bind = Await.result(http.bindAndHandle(allRoutes, "0.0.0.0", port), Duration.Inf)

    //debug all requests
    bind = Await.result(http.bindAndHandle(DebuggingDirectives.logRequest(("debug", Logging.InfoLevel))(allRoutes), "0.0.0.0", port), Duration.Inf)
    bind
  }
  // -------------------------------------------------------------------------*/

  val serverSource = Http().bind(interface = "localhost", port = port)

  val requestHandler: HttpRequest => Future[HttpResponse] = htr =>
    isAuthenticated("accesskey", Some("token")).flatMap {
        case None => Future(HttpResponse(StatusCodes.ProxyAuthenticationRequired))
        case Some(secret) =>
          if(validateUserRequest(htr, secret))
            isAuthorized("accessMode", "path", "username")
          else Future(HttpResponse(StatusCodes.BadRequest))
      }.flatMap {
        case false => Future(HttpResponse(StatusCodes.Unauthorized))
        case true =>
          println(s"OLD: $htr")
          val newHtr = htr.copy(uri = htr.uri.withAuthority("localhost", 8010))
          println(s"NEW: $newHtr")

          Http().singleRequest(translateRequest(newHtr))
      }


  val bindingFuture: Future[Http.ServerBinding] = {
    println("Server has started")
    serverSource.to(Sink.foreach { connection =>
      println("Accepted new connection from " + connection.remoteAddress)

      connection handleWithAsyncHandler requestHandler
      // this is equivalent to
      // connection handleWith { Flow[HttpRequest] map requestHandler }
    }).run()
  }
}
