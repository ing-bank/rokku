package com.ing.wbaa.rokku.proxy.provider

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.proxy.config.AuthorizerSettings
import com.ing.wbaa.rokku.proxy.provider.AuthorizationProviderRanger.RangerException
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class AuthorizationProviderSpec extends AnyWordSpec with Diagrams with AuthorizationProviderRanger {

  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  override val authorizerSettings: AuthorizerSettings = new AuthorizerSettings(testSystem.settings.config) {
    override val appId: String = "nonexistent"
    override val serviceType: String = "nonexistent"
  }

  "Authorization Provider" should {
    "throw a Ranger exception for unknown appId or serviceType" in {
      assertThrows[RangerException] {
        rangerPluginForceInit
      }
    }
  }
}
