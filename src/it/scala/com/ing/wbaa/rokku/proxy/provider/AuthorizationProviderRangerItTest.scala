package com.ing.wbaa.rokku.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.RemoteAddress
import com.ing.wbaa.rokku.proxy.config.RangerSettings
import com.ing.wbaa.rokku.proxy.data.{AwsAccessKey, AwsRequestCredential, AwsSecretKey, AwsSessionToken, Delete, HeaderIPs, NoAccess, Read, RequestId, S3Request, User, UserAssumeRole, UserGroup, UserName, Write}
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.Future

class AuthorizationProviderRangerItTest extends AsyncWordSpec with DiagrammedAssertions {
  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  implicit val requestId: RequestId = RequestId("test")

  val s3Request = S3Request(
    AwsRequestCredential(AwsAccessKey("accesskey"), Some(AwsSessionToken("sessiontoken"))),
    Some("/demobucket"),
    None,
    Read()
  )

  val user = User(
    UserName("testuser"),
    Set(UserGroup("testgroup")),
    AwsAccessKey("accesskey"),
    AwsSecretKey("secretkey"),
    UserAssumeRole("")
  )

  val clientIPAddress = RemoteAddress(InetAddress.getByName("1.7.8.9"), Some(1234))
  val unauthorizedIPAddress = RemoteAddress(InetAddress.getByName("1.2.3.4"), Some(1234))
  val headerIPs = HeaderIPs(
    `X-Real-IP` = Some(RemoteAddress(InetAddress.getByName("1.1.3.4"), Some(1234))),
    `X-Forwarded-For` = Some(Seq(
      RemoteAddress(InetAddress.getByName("1.1.1.1"), None),
      RemoteAddress(InetAddress.getByName("1.1.1.2"), None),
      RemoteAddress(InetAddress.getByName("1.1.1.3"), None))),
    `Remote-Address` = Some(RemoteAddress(InetAddress.getByName("1.1.4.4"), None))
  )

  /**
    * Fixture for setting up a Ranger provider object
    *
    * @param testCode Code that accepts the created authorization provider
    * @return Assertion
    */
  def withAuthorizationProviderRanger(rangerTestSettings: RangerSettings =
                                      RangerSettings(testSystem))
                                     (testCode: AuthorizationProviderRanger => Future[Assertion]): Future[Assertion] = {
    testCode(new AuthorizationProviderRanger {
      override def rangerSettings: RangerSettings = rangerTestSettings
    })
  }

  "Authorization Provider Ranger" should {
    "authorize a request" that {
      "successfully authorizes a request on a bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = clientIPAddress,
          headerIPs = headerIPs), user))
      }

      "successfully authorizes a request on an object in a bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3Object = Some("object"),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "authorize for requests without bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = None,
          clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "doesn't authorize for requests that are not supposed to be (Write)" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(accessType = Write(), s3Object = Some("object"),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "doesn't authorize for unauthorized user and group" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = clientIPAddress,
          headerIPs = headerIPs), user.copy(
          userName = UserName("unauthorized"), userGroups = Set(UserGroup("unauthorized")))))
      }

      "does authorize for unauthorized user but authorized group" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = clientIPAddress,
          headerIPs = headerIPs), user.copy(userName = UserName("unauthorized"))))
      }

      "does authorize for authorized user but unauthorized group" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = clientIPAddress,
          headerIPs = headerIPs), user.copy(userGroups = Set(UserGroup("unauthorized")))))
      }

      "authorize allow-list-buckets with default settings" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = None, s3Object = None,
          accessType = Read(), clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "does authorize allow-list-buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val listBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = None, s3Object = None,
          accessType = Read(), clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "does authorize allow-create-buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val createBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3Object = None, accessType = Write(),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "does authorize delete buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val createBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3Object = None, accessType = Delete(),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "doesn't authorize when method is not REST (GET, PUT, DELETE etc.)" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(s3Object = None, accessType = NoAccess,
          clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "doesn't authorize when remoteIpAddress is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = unauthorizedIPAddress,
          headerIPs = headerIPs), user))
      }

      "doesn't authorize when X-Real-IP is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = clientIPAddress,
          headerIPs = headerIPs.copy(`X-Real-IP` = Some(unauthorizedIPAddress))), user))
      }

      "doesn't authorize when any of X-Forwarded-For is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = clientIPAddress,
          headerIPs = headerIPs.copy(`X-Forwarded-For` = Some(headerIPs.`X-Forwarded-For`.get :+ unauthorizedIPAddress))), user))
      }

      "doesn't authorize when Remote-Address is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = clientIPAddress,
          headerIPs = headerIPs.copy(`Remote-Address` = Some(unauthorizedIPAddress))), user))
      }

      "doesn't authorize when any IP is unknown" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = RemoteAddress.Unknown, headerIPs = headerIPs), user))
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(clientIPAddress = clientIPAddress,
          headerIPs = headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))), user))
      }

      "doesn't allow listing subdir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some("/demobucket/subdir"),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))), user))
      }

      "does allow read homedir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some("/home/testuser"),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))), user))
      }

      "doesn't allow read in non homedir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some("/home/testuser1"),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))), user))
      }

      "does allow write homedir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(
          s3Request.copy(s3BucketPath = Some("/home/testuser"), s3Object = Some("object1"),
            accessType = Write(), clientIPAddress = clientIPAddress,
            headerIPs = headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))), user))
      }

      "doesn't allow write in non homedir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(
          s3Request.copy(s3BucketPath = Some("/home/testuser1"), s3Object = Some("object1"),
            accessType = Write(), clientIPAddress = clientIPAddress,
            headerIPs = headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))), user))
      }

      "does allow read all user dir" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some("/home"),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs), user))
      }

      "does allow read shared bucket with assumedRole" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some("/shared"),
          clientIPAddress = clientIPAddress, headerIPs = headerIPs), user.copy(userName = UserName(""), userGroups = Set(), userRole = UserAssumeRole("test"))))
      }
    }
  }
}
