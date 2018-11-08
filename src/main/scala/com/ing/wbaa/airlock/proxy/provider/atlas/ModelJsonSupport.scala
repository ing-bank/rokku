package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.ing.wbaa.airlock.proxy.data.LineageGuidResponse
import spray.json.DefaultJsonProtocol

trait ModelJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import Model._

  //generic
  implicit val readsClassification = jsonFormat1(Classification)
  implicit val readsGuidRef = jsonFormat2(guidRef)
  //server
  implicit val readsServerAttributes = jsonFormat4(ServerAttributes)
  implicit val readsServer = jsonFormat4(Server)
  implicit val readsServerEntities = jsonFormat1(Entities[Server])
  //bucket
  implicit val readsBucketAttributes = jsonFormat2(BucketAttributes)
  implicit val readsBucket = jsonFormat3(Bucket)
  implicit val readsBucketEntities = jsonFormat1(Entities[Bucket])
  //pseudoDir
  implicit val readsPseudoDirAttributes = jsonFormat4(PseudoDirAttributes)
  implicit val readsPseudoDir = jsonFormat2(PseudoDir)
  implicit val readsPseudoDirEntities = jsonFormat1(Entities[PseudoDir])
  // Entity search result
  implicit val idReader = jsonFormat5(EntityId)
  implicit val readsDefinition = jsonFormat7(Definition)
  implicit val resultReader = jsonFormat2(EntitySearchResult)
  implicit val guidResponse = jsonFormat1(LineageGuidResponse)
  // Entity create / update result
  implicit val readsCreateResponse = jsonFormat2(CreateResponse)
  implicit val readsUpdateResponse = jsonFormat1(UpdateResponse)
  // IngestionProcess
  implicit val readsIngestionAttributes = jsonFormat7(ClientProcessAttributes)
  implicit val readsIngestion = jsonFormat3(ClientProcess)
  implicit val readsIngestionEntities = jsonFormat1(Entities[ClientProcess])
  // File
  implicit val readsFileAttributes = jsonFormat5(BucketObjectAttributes)
  implicit val readsFile = jsonFormat2(BucketObject)
  implicit val readsFileEntities = jsonFormat1(Entities[BucketObject])
  // FsPath
  implicit val readsFsPathAttributes = jsonFormat3(FsPathAttributes)
  implicit val readsFsPath = jsonFormat2(FsPath)
  implicit val readsFsPathEntities = jsonFormat1(Entities[FsPath])

}
