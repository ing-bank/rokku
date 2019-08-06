package com.ing.wbaa.rokku.proxy.persistence

import akka.Done
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.http.scaladsl.model.{HttpRequest, RemoteAddress}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.amazonaws.services.s3.AmazonS3
import com.ing.wbaa.rokku.proxy.RokkuS3Proxy
import com.ing.wbaa.rokku.proxy.config.{HttpSettings, KafkaSettings, StorageS3Settings}
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.{FilterRecursiveListBucketHandler, RequestHandlerS3}
import com.ing.wbaa.rokku.proxy.provider.{AuditLogProvider, MessageProviderKafka, SignatureProviderAws}
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import com.ing.wbaa.testkit.RokkuFixtures
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class HttpRequestRecorderItTest extends AsyncWordSpec with DiagrammedAssertions with RokkuFixtures {
  implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  val rokkuHttpSettings: HttpSettings = new HttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  def withS3SdkToMockProxy(testCode: AmazonS3 => Assertion): Future[Assertion] = {
    val proxy: RokkuS3Proxy = new RokkuS3Proxy with RequestHandlerS3 with SignatureProviderAws
      with FilterRecursiveListBucketHandler with MessageProviderKafka with AuditLogProvider with MemoryUserRequestQueue {
      override implicit lazy val system: ActorSystem = testSystem
      override val httpSettings: HttpSettings = rokkuHttpSettings

      override def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = true

      override def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = true

      override val storageS3Settings: StorageS3Settings = StorageS3Settings(testSystem)
      override val kafkaSettings: KafkaSettings = KafkaSettings(testSystem)

      override def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]] =
        Future(Some(User(UserRawJson("userId", Set("group"), "accesskey", "secretkey"))))

      def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)(implicit id: RequestId): Future[Done] = Future.successful(Done)

      override protected def auditEnabled: Boolean = false

      override val requestPersistenceEnabled: Boolean = true
      override val configuredPersistenceId: String = "localhost-1"
    }
    proxy.startup.map { binding =>
      try testCode(getAmazonS3(
        authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
      ))
      finally proxy.shutdown()
    }
  }

  private val CHECKER_PERSISTENCE_ID = "localhost-1"
  val requestRecorder = testSystem.actorOf(Props(classOf[HttpRequestRecorder]), CHECKER_PERSISTENCE_ID)

  val queries = PersistenceQuery(testSystem)
    .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)


  "S3 Proxy" should {
    s"with Request Recorder" that {
      "persists requests in Cassandra" in withS3SdkToMockProxy { sdk =>

        // just to make fake request
        sdk.createBucket("testbucket")
        Thread.sleep(6000)

        val storedInCassandraF = queries.currentEventsByPersistenceId(CHECKER_PERSISTENCE_ID, 1L, Long.MaxValue)
          .map(_.event)
          .runWith(Sink.seq)
          .mapTo[Seq[ExecutedRequestEvt]]

        val r = Await.result(storedInCassandraF, 5.seconds)

        assert(r.size == 1)
        assert(r.head.userSTS.userName.value == "userId")


      }
    }
  }
}
