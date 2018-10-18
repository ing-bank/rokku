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
    Some("demobucket"),
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
        assert(apr.isUserAuthorizedForRequest(s3Request, user, clientIPAddress))
      }

      "successfully authorizes a request on an object in a bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = Some("object")), user, clientIPAddress))
      }

      "doesn't authorize for requests without bucket" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(bucket = None), user, clientIPAddress))
      }

      "doesn't authorize for requests that are not supposed to be (Write)" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(accessType = Write), user, clientIPAddress))
      }

      "doesn't authorize for unauthorized user and group" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user.copy(
          userName = UserName("unauthorized"), userAssumedGroup = Some(UserAssumedGroup("unauthorized"))), clientIPAddress))
      }

      "does authorize for unauthorized user but authorized group" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user.copy(userName = UserName("unauthorized")), clientIPAddress))
      }

      "does authorize for authorized user but unauthorized group" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user.copy(userAssumedGroup = Some(UserAssumedGroup("unauthorized"))), clientIPAddress))
      }

      "doesn't authorize allow-list-buckets with default settings" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(bucket = None, bucketObjectRoot = None, accessType = Read), user, clientIPAddress))
      }

      "does authorize allow-list-buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val listBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucket = None, bucketObjectRoot = None, accessType = Read), user, clientIPAddress))
      }

      "does authorize allow-create-buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val createBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = Write), user, clientIPAddress))
      }

      "does authorize delete buckets set to true" in withAuthorizationProviderRanger(new RangerSettings(testSystem.settings.config) {
        override val createBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = Delete), user, clientIPAddress))
      }

      "doesn't authorize when method is not REST (GET, PUT, DELETE etc.)" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = NoAccess), user, clientIPAddress))
      }

      "doesn't authorize when remoteIpAddress is in a DENY policy" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user, RemoteAddress(InetAddress.getByName("1.2.3.4"), Some(1234))))
      }

    }
  }
}
