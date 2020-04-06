package com.ing.wbaa.rokku.proxy.cache

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{ HttpMethod, HttpMethods, HttpRequest, Uri }
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class CacheRulesV1Spec extends AnyWordSpec with Diagrams with CacheRulesV1 with RequestParser {

  private implicit val id = RequestId("testRequestId")

  val system: ActorSystem = ActorSystem.create("test-system")
  override val storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
    override val storageS3Authority: Uri.Authority = Uri.Authority(Uri.Host("1.2.3.4"), 1234)
  }

  private val uri = Uri("http", Uri.Authority(Uri.Host("1.2.3.4")), Path(""), None, None)

  private val methods = Seq(HttpMethods.GET, HttpMethods.PUT, HttpMethods.POST, HttpMethods.DELETE, HttpMethods.HEAD)

  "Cache rules v1 set isEligibleToBeCached " should {

    methods.foreach { method =>
      testIsEligibleToBeCached(method, "/home/test", HttpRequest.apply(method = method, uri = uri.copy(path = Path("/home/test"))))
      testIsEligibleToBeCached(method, "/home2/test", HttpRequest.apply(method = method, uri = uri.copy(path = Path("/home2/test"))))
      testIsEligibleToBeCached(method, "/test/abc", HttpRequest.apply(method = method, uri = uri.copy(path = Path("/test/abc"))))
      testIsEligibleToBeCached(method, "/testtest/test", HttpRequest.apply(method = method, uri = uri.copy(path = Path("/testtest/test"))))
    }
  }

  private def testIsEligibleToBeCached(method: HttpMethod, path: String, request: HttpRequest): Unit = {
    method match {
      case HttpMethods.GET if storageS3Settings.eligibleCachePaths.exists(path.startsWith) =>
        s"for method=$method and path=$path to true" in {
          assert(isEligibleToBeCached(request))
        }
      case _ =>
        s"for method=$method and path=$path to false" in {
          assert(!isEligibleToBeCached(request))
        }
    }
  }

  "Cache rules v1 set isEligibleToBeInvalidated" should {

    methods.foreach { method =>
      val request = HttpRequest.apply(method = method, uri)
      method match {
        case HttpMethods.POST | HttpMethods.PUT | HttpMethods.DELETE =>
          s"for method=$method to true" in {
            assert(isEligibleToBeInvalidated(request))
          }
        case _ =>
          s"for method=$method to false" in {
            assert(!isEligibleToBeInvalidated(request))
          }
      }
    }
  }
}
