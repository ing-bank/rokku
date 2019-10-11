package com.ing.wbaa.rokku.proxy.persistence.serializers

import java.nio.charset.Charset

import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, RemoteAddress }
import akka.serialization.SerializerWithStringManifest
import com.ing.wbaa.rokku.proxy.data.{ User, UserRawJson }
import com.ing.wbaa.rokku.proxy.persistence.{ CurrentRequestsState, ExecutedRequestEvt }
import spray.json._

class HttpRequestSerializer extends SerializerWithStringManifest with HttpRequestConversionSupport {
  override def identifier: Int = 197642

  val Utf8 = Charset.forName("UTF-8")
  val HttpRequestManifest = classOf[ExecutedRequestEvt].getName
  val HttpRequestsManifest = classOf[CurrentRequestsState].getName

  def simplifiedHttpRequestString(e: HttpRequest) =
    SimplifiedHttpRequest(
      e.method.value,
      e.uri.toString(),
      convertAkkaHeadersToStrings(e.headers),
      HttpEntity.Empty.withContentType(e.entity.contentType).toString(),
      e.protocol.value
    ).toJson.toString

  def userSTSString(u: User) =
    UserRawJson(
      u.userName.value,
      Option(u.userGroups.map(g => g.value)),
      u.accessKey.value,
      u.secretKey.value,
      Option(u.userRole.value)).toJson.toString

  def remoteIPString(a: RemoteAddress) = SimplifiedRemoteAddress(a.value).toJson.toString()

  def toExecutedRequestEvt(r: String) = {
    val Array(hr, u, ip) = r.split("[|]")
    val httpRequest = toAkkaHttpRequest(hr.parseJson.convertTo[SimplifiedHttpRequest])
    val userSTS = User(u.parseJson.convertTo[UserRawJson])
    val simplifiedRemoteAddress = ip.parseJson.convertTo[SimplifiedRemoteAddress]

    ExecutedRequestEvt(httpRequest, userSTS, simplifiedRemoteAddress.toRemoteAddr)
  }

  override def manifest(o: AnyRef): String = o.getClass.getName

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case r: ExecutedRequestEvt =>
        s"${simplifiedHttpRequestString(r.httpRequest)}|${userSTSString(r.userSTS)}|${remoteIPString(r.clientIPAddress)}".getBytes(Utf8)

      case c: CurrentRequestsState =>
        c.requests.map { re =>
          s"${simplifiedHttpRequestString(re.httpRequest)}|${userSTSString(re.userSTS)}|${remoteIPString(re.clientIPAddress)}"
        }.mkString("|-").getBytes(Utf8)

      case e: IllegalArgumentException =>
        throw new IllegalArgumentException(s"Unable to serialize to bytes, class: ${o.getClass} ${e.getMessage}")
    }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    manifest match {
      case s: String if s == HttpRequestManifest =>
        val storedRequest = new String(bytes, Utf8)
        toExecutedRequestEvt(storedRequest)

      case s: String if s == HttpRequestsManifest =>
        val storedRequests = new String(bytes, Utf8)
        val requestsList: List[String] = storedRequests.split("[|-]{2}").toList
        CurrentRequestsState(requestsList.map(toExecutedRequestEvt))

      case _ => throw new IllegalArgumentException(s"Unable to de-serialize from bytes for manifest: $manifest")
    }
  }
}
