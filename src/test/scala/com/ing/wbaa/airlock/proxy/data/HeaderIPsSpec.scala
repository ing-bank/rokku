package com.ing.wbaa.airlock.proxy.data

import java.net.InetAddress

import akka.http.scaladsl.model.RemoteAddress
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class HeaderIPsSpec extends WordSpec with DiagrammedAssertions {

  private[this] val address1 = RemoteAddress(InetAddress.getByName("1.1.1.1"), None)
  private[this] val address2 = RemoteAddress(InetAddress.getByName("1.1.1.2"), None)
  private[this] val address3 = RemoteAddress(InetAddress.getByName("1.1.1.3"), None)
  private[this] val address4 = RemoteAddress(InetAddress.getByName("1.1.1.4"), None)

  val headerIPs = HeaderIPs(
    `X-Real-IP` = Some(address1),
    `X-Forwarded-For` = Some(Seq(address2, address3)),
    `Remote-Address` = Some(address4)
  )

  "HeaderIPs" should {
    "return all IPs" that {
      "are in X-Real-IP" in {
        assert(headerIPs.allIPs.contains(address1))
      }
      "are in X-Forwarded-For" in {
        assert(headerIPs.allIPs.contains(address2))
        assert(headerIPs.allIPs.contains(address3))
      }
      "are in Remote-Address" in {
        assert(headerIPs.allIPs.contains(address4))
      }
    }
  }
}
