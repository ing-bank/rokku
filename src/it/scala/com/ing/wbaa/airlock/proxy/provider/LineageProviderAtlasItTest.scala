package com.ing.wbaa.airlock.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, Materializer}
import com.ing.wbaa.airlock.proxy.config.AtlasSettings
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.atlas.RestClient.RestClientException
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.{ExecutionContext, Future}

class LineageProviderAtlasItTest extends AsyncWordSpec with DiagrammedAssertions  {

  implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  def fakeIncomingHttpRequest(method: HttpMethod, path: String) = {
    val uri = Uri(
      scheme = "http",
      authority = Uri.Authority(host = Uri.Host("proxyHost"), port = 8010)).withPath(Uri.Path(path))

    method match {
      case HttpMethods.GET => HttpRequest(method, uri, Nil)
      case HttpMethods.POST | HttpMethods.PUT => HttpRequest(method, uri, Nil, HttpEntity(ContentTypes.`application/json`, "{}"))
      case HttpMethods.DELETE => HttpRequest(method, uri, Nil)
      case _ => HttpRequest(method, uri, Nil)
    }
  }

  val userSTS = User(UserName("fakeUser"), None, AwsAccessKey("a"), AwsSecretKey("k"))

  def withLineageProviderAtlas(atlasTestSettings: AtlasSettings = AtlasSettings(testSystem))(testCode: LineageProviderAtlas => Future[Assertion]) =
    testCode(new LineageProviderAtlas {
      override protected[this] implicit def system: ActorSystem = ActorSystem.create("test-system")

      override protected[this] implicit def executionContext: ExecutionContext = system.dispatcher

      override protected[this] implicit def atlasSettings: AtlasSettings = atlasTestSettings

      override protected[this] implicit def materializer: Materializer = ActorMaterializer()(system)
    })

  "LineageProviderAtlas" should {
    "create Write lineage from HttpRequest" in withLineageProviderAtlas() { apr =>

      val createLineageResult  = apr.createLineageFromRequest(
        fakeIncomingHttpRequest(HttpMethods.PUT, "/fakeBucket/fakeObject"), userSTS)

      createLineageResult.map { result =>
          val postResult = result.asInstanceOf[LineagePostGuidResponse]
            assert( postResult.serverGuid.length > 0 )
            assert( postResult.bucketGuid.length > 0 )
            assert( postResult.fileGuid.length > 0 )
            assert( postResult.processGuid.length > 0 )
        }
    }

    "create Read lineage from HttpRequest" in withLineageProviderAtlas() { apr =>

      val createLineageResult  = apr.createLineageFromRequest(
        fakeIncomingHttpRequest(HttpMethods.GET, "/fakeBucket/fakeObject"), userSTS)

      createLineageResult.map { result =>
        val postResult = result.asInstanceOf[LineagePostGuidResponse]
            assert( postResult.serverGuid.length > 0 )
            assert( postResult.bucketGuid.length > 0 )
            assert( postResult.fileGuid.length > 0 )
            assert( postResult.processGuid.length > 0 )
        }
    }

    "create Delete lineage from HttpRequest" in withLineageProviderAtlas() { apr =>

      val createLineageResult  = apr.createLineageFromRequest(
        fakeIncomingHttpRequest(HttpMethods.DELETE, "/fakeBucket/fakeObject"), userSTS)

      createLineageResult.map { result =>
        val postResult = result.asInstanceOf[LineageGuidResponse]
            assert( postResult.entityGUID.length > 0 )
      }
    }

    "fail on incorrect Settings" in withLineageProviderAtlas(new AtlasSettings(testSystem.settings.config) {
      override val atlasApiHost: String = "fakeHost"
      override val atlasApiPort: Int = 21001
      override def atlasBaseUri: Uri = Uri(
        scheme = "http",
        authority = Uri.Authority(host = Uri.Host(atlasApiHost), port = atlasApiPort)
      )
    }) { apr =>
      recoverToSucceededIf[RestClientException](apr.createLineageFromRequest(fakeIncomingHttpRequest(HttpMethods.PUT, "/fakeBucket/fakeObject"), userSTS))
    }
  }
}
