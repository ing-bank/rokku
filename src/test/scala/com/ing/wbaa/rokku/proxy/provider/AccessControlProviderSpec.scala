package com.ing.wbaa.rokku.proxy.provider

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.proxy.config.AccessControlSettings
import com.ing.wbaa.rokku.proxy.provider.AccessControlProviderRanger.RangerException
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class AccessControlProviderSpec extends AnyWordSpec with Diagrams with AccessControlProviderClassForName {

  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  override val authorizerSettings: AccessControlSettings = new AccessControlSettings(testSystem.settings.config) {
    override val appId: String = "nonexistent"
    override val serviceType: String = "nonexistent"
  }

  "Authorization Provider" should {
    "throw a Ranger exception for unknown appId or serviceType" in {
      assertThrows[RangerException] {
        init()
      }
    }
  }
}
