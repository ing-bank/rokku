package com.ing.wbaa.rokku.proxy.handler

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.{RequestId, User, UserRawJson}
import com.ing.wbaa.rokku.proxy.handler.radosgw.RadosGatewayHandler
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec
import org.twonote.rgwadmin4j.{RgwAdmin, RgwAdminBuilder}

import scala.util.Random

class RadosGatewayHandlerItTest extends AnyWordSpec with Diagrams with RadosGatewayHandler with S3Client {

  override protected[this] implicit def system: ActorSystem = ActorSystem("test-system")

  override protected[this] def storageS3Settings: StorageS3Settings = StorageS3Settings(system)

  implicit val requestId: RequestId = RequestId("test")

  private[this] val rgwAdmin: RgwAdmin = new RgwAdminBuilder()
    .accessKey(storageS3Settings.storageS3AdminAccesskey)
    .secretKey(storageS3Settings.storageS3AdminSecretkey)
    .endpoint(s"http://${storageS3Settings.storageS3Authority.host.address()}:${storageS3Settings.storageS3Authority.port}/admin")
    .build

  def testUser: User = User(UserRawJson(
    Random.alphanumeric.take(32).mkString,
    Some(Set.empty[String]),
    Random.alphanumeric.take(32).mkString,
    Random.alphanumeric.take(32).mkString,
    None
  ))

  "Rados Gateway Handler" should {
    "handle User Creation" that {
      "for a new user create credentials on ceph" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)
        val retrievedUser = rgwAdmin.getUserInfo(u.userName.value)

        assert(retrievedUser.isPresent)
        assert(retrievedUser.get().getS3Credentials.size() == 1)
        assert(retrievedUser.get().getS3Credentials.get(0).getAccessKey == u.accessKey.value)
        assert(retrievedUser.get().getS3Credentials.get(0).getSecretKey == u.secretKey.value)
      }

      "for an existing user with no credentials create the credentials" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)

        rgwAdmin.removeS3Credential(u.userName.value, u.accessKey.value)

        val isUpdated = handleUserCreationRadosGw(u)
        assert(isUpdated)

        val retrievedUser = rgwAdmin.getUserInfo(u.userName.value)
        assert(retrievedUser.isPresent)
        assert(retrievedUser.get().getS3Credentials.size() == 1)
        assert(retrievedUser.get().getS3Credentials.get(0).getAccessKey == u.accessKey.value)
        assert(retrievedUser.get().getS3Credentials.get(0).getSecretKey == u.secretKey.value)
      }

      "for an existing user with more than 1 credentials returns false" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)

        rgwAdmin.createS3Credential(u.userName.value)

        val isUpdated = handleUserCreationRadosGw(u)
        assert(!isUpdated)
      }

      "resets credentials when user in STS and ceph mismatch credentials" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)

        val u2 = testUser
        val isUpdated = handleUserCreationRadosGw(u.copy(accessKey = u2.accessKey, secretKey = u2.secretKey))
        assert(isUpdated)

        val retrievedUser = rgwAdmin.getUserInfo(u.userName.value)
        assert(retrievedUser.isPresent)
        assert(retrievedUser.get().getS3Credentials.size() == 1)
        assert(retrievedUser.get().getS3Credentials.get(0).getAccessKey == u2.accessKey.value)
        assert(retrievedUser.get().getS3Credentials.get(0).getSecretKey == u2.secretKey.value)
      }

      "doesn't do anything when user in STS and ceph already match" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)

        val isUpdated = handleUserCreationRadosGw(u)
        assert(!isUpdated)
      }
    }

    "list all buckets" in {
      assert(listAllBuckets.contains("demobucket"))
    }
  }
}
