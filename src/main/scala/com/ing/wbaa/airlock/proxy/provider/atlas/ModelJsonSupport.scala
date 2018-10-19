package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
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
  implicit val readsBucketAttributes = jsonFormat3(BucketAttributes)
  implicit val readsBucket = jsonFormat4(Bucket)
  implicit val readsBucketEntities = jsonFormat1(Entities[Bucket])
  // Entity search result
  implicit val idReader = jsonFormat5(EntityId)
  implicit val readsDefinition = jsonFormat7(Definition)
  implicit val resultReader = jsonFormat2(EntitySearchResult)
  // Entity create / update result
  implicit val readsCreateResponse = jsonFormat2(CreateResponse)
  implicit val readsUpdateResponse = jsonFormat1(UpdateResponse)
  // IngestionProcess
  implicit val readsIngestionAttributes = jsonFormat7(IngestionAttributes)
  implicit val readsIngestion = jsonFormat3(Ingestion)
  implicit val readsIngestionEntities = jsonFormat1(Entities[Ingestion])
  // File
  implicit val readsFileAttributes = jsonFormat8(FileAttributes)
  implicit val readsFile = jsonFormat3(IngestedFile)
  implicit val readsFileEntities = jsonFormat1(Entities[IngestedFile])

}
