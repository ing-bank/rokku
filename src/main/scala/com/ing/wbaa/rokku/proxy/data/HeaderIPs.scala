package com.ing.wbaa.rokku.proxy.data

import akka.http.scaladsl.model.RemoteAddress

case class HeaderIPs(
    `X-Real-IP`: Option[RemoteAddress] = None,
    `X-Forwarded-For`: Option[Seq[RemoteAddress]] = None,
    `Remote-Address`: Option[RemoteAddress] = None) {
  def allIPs: Seq[RemoteAddress] =
    `X-Real-IP`.map(Seq(_)).getOrElse(Nil) ++
      `X-Forwarded-For`.getOrElse(Nil) ++
      `Remote-Address`.map(Seq(_)).getOrElse(Nil)

  def toStringList: List[String] = {
    List(
      `X-Real-IP`.map(ip => s"X-Real-IP=$ip").getOrElse(""),
      `X-Forwarded-For`.map(ip => s"X-Forwarded-For=${ip.mkString(",")}").getOrElse(""),
      `Remote-Address`.map(ip => s"Remote-Address=$ip").getOrElse(""))
      .filter(_.nonEmpty)
  }
}

/**
 *
 * @param clientIp the client IP is taken from akka `Remote-Address`
 * @param headerIps the ip from headers
 */
case class UserIps(clientIp: RemoteAddress, headerIps: HeaderIPs) {

  def getRealIpOrClientIp: String = {
    headerIps.`X-Real-IP`.getOrElse(clientIp).getAddress.get().getHostAddress.toString
  }

  override def toString: String = {
    (Seq(s"ClientIp=$clientIp") ++ headerIps.toStringList).mkString("|")
  }
}
