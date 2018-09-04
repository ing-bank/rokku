package com.ing.wbaa.gargoyle.proxy.handler

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import com.ing.wbaa.gargoyle.proxy.data.User
import com.ing.wbaa.gargoyle.proxy.handler.radosgw.RadosGatewayHandler
import org.scalatest.{DiagrammedAssertions, WordSpec}
import org.twonote.rgwadmin4j.{RgwAdmin, RgwAdminBuilder}

import scala.util.Random

class RadosGatewayHandlerItTest extends WordSpec with DiagrammedAssertions with RadosGatewayHandler {

  override protected[this] implicit def system: ActorSystem = ActorSystem("test-system")
  override protected[this] def storageS3Settings: GargoyleStorageS3Settings = GargoyleStorageS3Settings(system)

  private[this] val rgwAdmin: RgwAdmin = new RgwAdminBuilder()
    .accessKey(storageS3Settings.storageS3AdminAccesskey)
    .secretKey(storageS3Settings.storageS3AdminSecretkey)
    .endpoint(s"http://${storageS3Settings.storageS3Authority.host.address()}:${storageS3Settings.storageS3Authority.port}/admin")
    .build

  def testUser: User = User(
    Random.alphanumeric.take(32).mkString,
    None,
    Random.alphanumeric.take(32).mkString,
    Random.alphanumeric.take(32).mkString
  )

  "Rados Gateway Handler" should {
    "handle User Creation" that {
      "for a new user create credentials on ceph" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)
        val retrievedUser = rgwAdmin.getUserInfo(u.userName)
        assert(retrievedUser.isPresent)
      }

      "for an existing user with no credentials create the credentials" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)

        rgwAdmin.removeS3Credential(u.userName, u.accessKey)

        val isUpdated = handleUserCreationRadosGw(u)
        assert(isUpdated)
      }

      "for an existing user with more than 1 credentials returns false" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)

        rgwAdmin.createS3Credential(u.userName)

        val isUpdated = handleUserCreationRadosGw(u)
        assert(!isUpdated)
      }

      "doesn't do anything when user in STS and ceph already match" in {
        val u = testUser
        val isCreated = handleUserCreationRadosGw(u)
        assert(isCreated)

        val isUpdated = handleUserCreationRadosGw(u)
        assert(!isUpdated)
      }
    }
  }
}
