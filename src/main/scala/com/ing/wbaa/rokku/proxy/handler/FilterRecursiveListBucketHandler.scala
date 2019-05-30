package com.ing.wbaa.rokku.proxy.handler

import java.net.URLDecoder

import akka.NotUsed
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.alpakka.xml.scaladsl.{ XmlParsing, XmlWriting }
import akka.stream.alpakka.xml.{ EndElement, ParseEvent, StartElement, TextEvent }
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.data.{ Read, RequestId, S3Request, User }

import scala.collection.immutable
import scala.collection.mutable.ListBuffer

/**
 * aws s3 ls s3://bucket --recursive
 * the command above returns all objects in the bucket
 * If there is a ranger policy only for "read" the bucket (non recursively) we need to check all subdirs of the bucket
 * in ranger as well
 */
trait FilterRecursiveListBucketHandler {

  protected[this] def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean

  /**
   * for list objects in bucket we need filter response when --recursive is set
   * --recursive means that there is a delimiter param in request query and the access type is READ
   *
   * @param request
   * @param userSTS
   * @param s3request
   * @param response
   * @return for recursive request it returns filtered response for others the original response
   */
  protected[this] def filterResponse(request: HttpRequest, userSTS: User, s3request: S3Request, response: HttpResponse)(implicit id: RequestId): HttpResponse = {
    val noDelimiterWithReadAndNoObject =
      !request.uri.rawQueryString.getOrElse("").contains("delimiter") && s3request.accessType.isInstanceOf[Read] && s3request.s3Object.isEmpty
    if (noDelimiterWithReadAndNoObject) {
      response.transformEntityDataBytes(filterRecursiveListObjects(userSTS, s3request))
    } else {
      response
    }
  }

  /**
   * Xml as byte of steam is parsed and each value of <Key> tags is checked if the user is authorised for it.
   *
   * @param user
   * @param requestS3
   * @return xml as stream of bytes with only authorised resources
   */
  protected[this] def filterRecursiveListObjects(user: User, requestS3: S3Request)(implicit id: RequestId): Flow[ByteString, ByteString, NotUsed] = {
    def elementResult(allContentsElements: ListBuffer[ParseEvent], isContentsTag: Boolean, element: ParseEvent): immutable.Seq[ParseEvent] = {
      if (isContentsTag) {
        allContentsElements += element
        immutable.Seq.empty
      } else {
        immutable.Seq(element)
      }
    }

    def isPathOkInRangerPolicy(path: String)(implicit id: RequestId): Boolean = {
      val pathToCheck = normalizePath(path)
      val isUserAuthorized = isUserAuthorizedForRequest(requestS3.copy(s3BucketPath = Some(pathToCheck)), user)
      isUserAuthorized
    }

    def normalizePath(path: String): String = {
      val delimiter = "/"
      val decodedPath = URLDecoder.decode(path, "UTF-8")
      val delimiterIndex = decodedPath.lastIndexOf(delimiter)
      val pathToCheckWithoutLastSlash = if (delimiterIndex > 0) delimiter + decodedPath.substring(0, delimiterIndex) else ""
      val s3BucketName = requestS3.s3BucketPath.getOrElse(delimiter)
      val s3pathWithoutLastDelimiter = if (s3BucketName.length > 1 && s3BucketName.endsWith(delimiter)) s3BucketName.substring(0, s3BucketName.length - 1) else s3BucketName
      s3pathWithoutLastDelimiter + pathToCheckWithoutLastSlash
    }

    Flow[ByteString].via(XmlParsing.parser)
      .statefulMapConcat(() => {
        // state
        val keyTagValue = StringBuilder.newBuilder
        val allContentsElements = new ListBuffer[ParseEvent]
        var isContentsTag = false
        var isKeyTag = false

        // aggregation function
        parseEvent =>
          parseEvent match {
            //catch <Contents> to start collecting elements
            case element: StartElement if element.localName == "Contents" =>
              isContentsTag = true
              allContentsElements.clear()
              allContentsElements += element
              immutable.Seq.empty
            //catch end </Contents> to validate the path in ranger
            case element: EndElement if element.localName == "Contents" =>
              isContentsTag = false
              allContentsElements += element
              if (isPathOkInRangerPolicy(keyTagValue.stripMargin)) {
                allContentsElements.toList
              } else {
                immutable.Seq.empty
              }
            // catch <Key> where is the patch name to match in ranger
            case element: StartElement if element.localName == "Key" =>
              keyTagValue.clear()
              isKeyTag = true
              elementResult(allContentsElements, isContentsTag, element)
            //catch end </Key>
            case element: EndElement if element.localName == "Key" =>
              isKeyTag = false
              elementResult(allContentsElements, isContentsTag, element)
            //catch all element text <..>text<\..> but only set the text from <Key>
            case element: TextEvent =>
              if (isKeyTag) keyTagValue.append(element.text)
              elementResult(allContentsElements, isContentsTag, element)
            //just past through the rest of elements
            case element =>
              elementResult(allContentsElements, isContentsTag, element)
          }
      })
      .via(XmlWriting.writer)
  }

}
