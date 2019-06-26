package com.ing.wbaa.rokku.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import com.ing.wbaa.rokku.proxy.config.KafkaSettings
import com.ing.wbaa.rokku.proxy.data.{AwsAccessKey, AwsSecretKey, RequestId, User, UserGroup, UserName}
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{Assertion, DiagrammedAssertions, WordSpecLike}

import scala.concurrent.ExecutionContext

class LineageProviderAtlasItTest extends WordSpecLike with DiagrammedAssertions with EmbeddedKafka {

  implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  implicit val requestId: RequestId = RequestId("test")

  def fakeIncomingHttpRequest(method: HttpMethod, path: String): HttpRequest = {
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

  val remoteClientIP = RemoteAddress(InetAddress.getByName("127.0.0.1"))

  val userSTS = User(UserName("fakeUser"), Set.empty[UserGroup], AwsAccessKey("a"), AwsSecretKey("k"))

  private val testKafkaPort = 9093

  def withLineageProviderAtlas()(testCode: LineageProviderAtlas => Assertion) =
    testCode(new LineageProviderAtlas {
      override protected[this] implicit def system: ActorSystem = ActorSystem.create("test-system")

      override protected[this] implicit val executionContext: ExecutionContext = system.dispatcher

      override protected[this] implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

      override val kafkaSettings: KafkaSettings = new KafkaSettings(testSystem.settings.config) {
        override val bootstrapServers: String = s"localhost:$testKafkaPort"
      }
    })

  val createEventsTopic = "ATLAS_HOOK"

  "LineageProviderAtlas" should {
    "create Write lineage from HttpRequest" in withLineageProviderAtlas() { apr =>
      implicit val config = EmbeddedKafkaConfig(kafkaPort = testKafkaPort)
      withRunningKafka {
        Thread.sleep(2000)
        createCustomTopic(createEventsTopic)
        apr.createLineageFromRequest(
          fakeIncomingHttpRequest(HttpMethods.PUT, "/fakeBucket/fakeObject"), userSTS, remoteClientIP)
        val message = consumeFirstStringMessageFrom(createEventsTopic)
        assert(message.contains("external_object_in/fakeObject"))
      }
    }

    "create Write lineage from HttpRequest with metadata" in withLineageProviderAtlas() { apr =>
      implicit val config = EmbeddedKafkaConfig(kafkaPort = testKafkaPort)
      withRunningKafka {
        Thread.sleep(2000)
        apr.createLineageFromRequest(
          fakeIncomingHttpRequest(HttpMethods.PUT, "/fakeBucket/fakeTags").withHeaders(RawHeader("rokku-metadata", "k1=v1")), userSTS, remoteClientIP)
        val message = consumeFirstStringMessageFrom(createEventsTopic)
        assert(message.contains("\"aws_tag\",\"values\":{\"key\":\"k1\",\"value\":\"v1\"}"))
      }
    }

    "create Read lineage from HttpRequest" in withLineageProviderAtlas() { apr =>
      implicit val config = EmbeddedKafkaConfig(kafkaPort = testKafkaPort)
      withRunningKafka {
        Thread.sleep(2000)
        apr.createLineageFromRequest(
          fakeIncomingHttpRequest(HttpMethods.GET, "/fakeBucket/fakeObject"), userSTS, remoteClientIP)
        val message = consumeFirstStringMessageFrom(createEventsTopic)
        assert(message.contains("external_object_out/fakeObject"))
      }
    }

    "create Delete lineage from HttpRequest" in withLineageProviderAtlas() { apr =>
      implicit val config = EmbeddedKafkaConfig(kafkaPort = testKafkaPort)
      withRunningKafka {
        Thread.sleep(2000)
        apr.createLineageFromRequest(
          fakeIncomingHttpRequest(HttpMethods.DELETE, "/fakeBucket/fakeObject"), userSTS, remoteClientIP)
        val message = consumeFirstStringMessageFrom(createEventsTopic)
        assert(message.contains("fakeObject"))
      }
    }
  }
}
