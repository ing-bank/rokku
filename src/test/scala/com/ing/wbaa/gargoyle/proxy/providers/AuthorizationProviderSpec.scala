package com.ing.wbaa.gargoyle.proxy.providers

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.data._
import com.ing.wbaa.gargoyle.proxy.providers.AuthorizationProviderRanger.RangerException
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class AuthorizationProviderSpec extends WordSpec with DiagrammedAssertions {

  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  "Authorization Provider" should {
    "throw a Ranger exception for unknown appId or serviceType" in {
      val request = S3Request(
        AwsRequestCredential(AwsAccessKey("accesskey"), Some(AwsSessionToken("sessionToken"))),
        Some("demobucket"),
        Read
      )

      val user = User(
        "userId",
        "secretKey",
        Set("group"),
        "arn"
      )

      assertThrows[RangerException] {
        new AuthorizationProviderRanger {
          override val rangerSettings = new GargoyleRangerSettings(testSystem.settings.config) {
            override val appId: String = "nonexistent"
            override val serviceType: String = "nonexistent"
          }
        }.isAuthorized(request, user)
      }
    }
  }
}
