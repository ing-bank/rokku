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

  override def toString: String = {
    List(`X-Real-IP`.map(ip => s"X-Real-IP=${ip}").getOrElse(""),
      `X-Forwarded-For`.map(ip => s"X-Forwarded-For=${ip.mkString(",")}").getOrElse(""),
      `Remote-Address`.map(ip => s"Remote-Address=${ip}").getOrElse("")).filter(_.nonEmpty).mkString("|")
  }
}
