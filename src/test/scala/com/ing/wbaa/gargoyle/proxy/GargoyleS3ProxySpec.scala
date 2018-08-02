package com.ing.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.{ Authority, Host, Path }
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes, Uri }
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.ing.wbaa.gargoyle.proxy.config.{ GargoyleHttpSettings, GargoyleStorageS3Settings }
import com.ing.wbaa.gargoyle.proxy.data.{ S3Request, User }
import com.ing.wbaa.gargoyle.proxy.handler.RequestHandlerS3
import org.scalatest.{ Assertion, AsyncFlatSpec, DiagrammedAssertions }

import scala.concurrent.Future

class GargoyleS3ProxySpec extends AsyncFlatSpec with DiagrammedAssertions {

  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  private[this] final implicit val materializer: ActorMaterializer = ActorMaterializer()

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  private[this] val gargoyleTestSettings = new GargoyleHttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  // Fixture for starting and stopping a test proxy that tests can interact with.
  def withTestProxy(testCode: Authority => Future[Assertion]): Future[Assertion] = {
    val testProxy = new GargoyleS3Proxy with RequestHandlerS3 {
      override implicit lazy val system: ActorSystem = testSystem
      override val httpSettings: GargoyleHttpSettings = gargoyleTestSettings
      override def isAuthorized(request: S3Request, user: User): Boolean = true
      override val storageS3Settings: GargoyleStorageS3Settings = GargoyleStorageS3Settings(system)

      override def getUser(accessKey: String): Future[Option[User]] = Future(Some(User("userId", "secretKey", Set("group"), "arn")))

      override def isAuthenticated(accessKey: String, token: Option[String]): Future[Boolean] = Future.successful(true)
    }
    testProxy.bind
      .flatMap { binding =>
        val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
        testCode(authority)
      }
      .andThen { case _ => testProxy.shutdown() }
  }

  "A Gargoyle S3 proxy" should "respond to /ping with 'pong'" in withTestProxy { authority =>
    val request = HttpRequest(uri = Uri(scheme = "http", authority = authority, path = Path("/ping")))
    Http().singleRequest(request).flatMap { response =>
      assert(response.status == StatusCodes.OK)
      response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
        .map { body =>
          assert(body == "pong")
        }
    }
  }
}
