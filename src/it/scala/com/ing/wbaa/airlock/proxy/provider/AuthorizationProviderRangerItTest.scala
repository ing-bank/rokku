package com.ing.wbaa.airlock.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.RemoteAddress
import com.ing.wbaa.airlock.proxy.config.RangerSettings
import com.ing.wbaa.airlock.proxy.data._
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.Future

class AuthorizationProviderRangerItTest extends AsyncWordSpec with DiagrammedAssertions {
  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  val s3Request = S3Request(
    AwsRequestCredential(AwsAccessKey("accesskey"), Some(AwsSessionToken("sessiontoken"))),
    Some("/demobucket"),
    None,
    Read
  )

  val user = User(
    UserName("testuser"),
    Some(UserAssumedGroup("testgroup")),
    AwsAccessKey("accesskey"),
    AwsSecretKey("secretkey")
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
    * @param testCode      Code that accepts the created authorization provider
    * @return Assertion
    */
  def withAuthorizationProviderRanger(rangerTestSettings: RangerSettings = RangerSettings(testSystem))(testCode: AuthorizationProviderRanger => Future[Assertion]): Future[Assertion] = {
    testCode(new AuthorizationProviderRanger {
        override def rangerSettings: RangerSettings = rangerTestSettings
    })
  }

  "Authorization Provider Ranger" should {
    "authorize a request" that {
      "successfully authorizes a request on a bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user, clientIPAddress, headerIPs))
      }

      "successfully authorizes a request on an object in a bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = Some("object")), user, clientIPAddress, headerIPs))
      }

      "authorize for requests without bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = None), user, clientIPAddress, headerIPs))
      }

      "doesn't authorize for requests that are not supposed to be (Write)" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(accessType = Write, bucketObjectRoot = Some("object")), user, clientIPAddress, headerIPs))
      }

      "doesn't authorize for unauthorized user and group" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user.copy(
          userName = UserName("unauthorized"), userAssumedGroup = Some(UserAssumedGroup("unauthorized"))), clientIPAddress, headerIPs))
      }

      "does authorize for unauthorized user but authorized group" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user.copy(userName = UserName("unauthorized")), clientIPAddress, headerIPs))
      }

      "does authorize for authorized user but unauthorized group" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user.copy(userAssumedGroup = Some(UserAssumedGroup("unauthorized"))), clientIPAddress, headerIPs))
      }

      "authorize allow-list-buckets with default settings" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = None, bucketObjectRoot = None, accessType = Read), user, clientIPAddress, headerIPs))
      }

      "does authorize allow-list-buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val listBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = None, bucketObjectRoot = None, accessType = Read), user, clientIPAddress, headerIPs))
      }

      "does authorize allow-create-buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val createBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = Write), user, clientIPAddress, headerIPs))
      }

      "does authorize delete buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val createBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = Delete), user, clientIPAddress, headerIPs))
      }

      "doesn't authorize when method is not REST (GET, PUT, DELETE etc.)" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = NoAccess), user, clientIPAddress, headerIPs))
      }

      "doesn't authorize when remoteIpAddress is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user, unauthorizedIPAddress, headerIPs))
      }

      "doesn't authorize when X-Real-IP is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user, clientIPAddress, headerIPs.copy(`X-Real-IP` = Some(unauthorizedIPAddress))))
      }

      "doesn't authorize when any of X-Forwarded-For is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user, clientIPAddress, headerIPs.copy(`X-Forwarded-For` = Some(headerIPs.`X-Forwarded-For`.get :+ unauthorizedIPAddress))))
      }

      "doesn't authorize when Remote-Address is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user, clientIPAddress, headerIPs.copy(`Remote-Address` = Some(unauthorizedIPAddress))))
      }

      "doesn't authorize when any IP is unknown" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user, RemoteAddress.Unknown, headerIPs))
        assert(!apr.isUserAuthorizedForRequest(s3Request, user, clientIPAddress, headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))))
      }

      "doesn't allow listing subdir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some("/demobucket/subdir")), user, clientIPAddress, headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))))
      }

      "does allow read homedir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some("/home/testuser")), user, clientIPAddress, headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))))
      }

      "doesn't allow read in non homedir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some("/home/testuser1")), user, clientIPAddress, headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))))
      }

      "does allow write homedir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(
          s3Request.copy(s3BucketPath = Some("/home/testuser"), bucketObjectRoot = Some("object1"), accessType = Write),
          user, clientIPAddress, headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))))
      }

      "doesn't allow write in non homedir in the bucket" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(
          s3Request.copy(s3BucketPath = Some("/home/testuser1"), bucketObjectRoot = Some("object1"), accessType = Write),
          user, clientIPAddress, headerIPs.copy(`Remote-Address` = Some(RemoteAddress.Unknown))))
      }

    }
  }
}
