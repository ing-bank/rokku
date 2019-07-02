package com.ing.wbaa.rokku.proxy.persistence.serializers

import java.nio.charset.Charset

import akka.http.scaladsl.model.HttpEntity
import akka.serialization.SerializerWithStringManifest
import com.ing.wbaa.rokku.proxy.data.{User, UserRawJson}
import com.ing.wbaa.rokku.proxy.persistence.ExecutedRequestEvt
import spray.json._

class HttpRequestSerializer extends SerializerWithStringManifest with HttpRequestConversionSupport {
  override def identifier: Int = 197642

  val Utf8 = Charset.forName("UTF-8")
  val HttpRequestManifest = classOf[ExecutedRequestEvt].getName

  override def manifest(o: AnyRef): String = o.getClass.getName

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case re: ExecutedRequestEvt =>
        val simplifiedHttpRequest =
          SimplifiedHttpRequest(
            re.httpRequest.method.value,
            re.httpRequest.uri.toString(),
            convertAkkaHeadersToStrings(re.httpRequest.headers),
            HttpEntity.Empty.withContentType(re.httpRequest.entity.contentType).toString(),
            re.httpRequest.protocol.value
          ).toJson.toString

        val userSTS = re.userSTS
        val rawUserSTS =
          UserRawJson(
            userSTS.userName.value,
            userSTS.userGroups.map(g => g.value),
            userSTS.accessKey.value,
            userSTS.secretKey.value).toJson.toString

        val remoteIP = SimplifiedRemoteAddress(re.clientIPAddress.value).toJson.toString()

        s"$simplifiedHttpRequest|$rawUserSTS|$remoteIP".getBytes(Utf8)

      case e: IllegalArgumentException =>
        throw new IllegalArgumentException(s"Unable to serialize to bytes, class: ${o.getClass} ${e.getMessage}")
    }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    manifest match {
      case HttpRequestManifest =>
        val storedRequest = new String(bytes, Utf8)
        val Array(hr, u, ip) = storedRequest.split("[|]")
        val httpRequest = toAkkaHttpRequest(hr.parseJson.convertTo[SimplifiedHttpRequest])
        val userSTS = User(u.parseJson.convertTo[UserRawJson])
        val simplifiedRemoteAddress = ip.parseJson.convertTo[SimplifiedRemoteAddress]

        ExecutedRequestEvt(httpRequest, userSTS, simplifiedRemoteAddress.toRemoteAddr)

      case _ => throw new IllegalArgumentException(s"Unable to de-serialize from bytes for manifest: $manifest")
    }
}
