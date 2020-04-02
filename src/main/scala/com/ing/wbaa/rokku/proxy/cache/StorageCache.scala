package com.ing.wbaa.rokku.proxy.cache

import akka.http.scaladsl.model.HttpRequest
import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.data.RequestId

trait StorageCache {

  def getKey(request: HttpRequest)(implicit id: RequestId): String
  def getObject(key: String)(implicit id: RequestId): Option[ByteString]
  def putObject(key: String, value: ByteString)(implicit id: RequestId): Unit
  def removeObject(key: String)(implicit id: RequestId): Unit

}
