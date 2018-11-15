package com.ing.wbaa.airlock.proxy.data

import akka.http.scaladsl.model.RemoteAddress

case class HeaderIPs(
    `X-Real-IP`: Option[RemoteAddress] = None,
    `X-Forwarded-For`: Option[Seq[RemoteAddress]] = None,
    `Remote-Address`: Option[RemoteAddress] = None) {
  def allIPs: Seq[RemoteAddress] =
    `X-Real-IP`.map(Seq(_)).getOrElse(Nil) ++
      `X-Forwarded-For`.getOrElse(Nil) ++
      `Remote-Address`.map(Seq(_)).getOrElse(Nil)

}
