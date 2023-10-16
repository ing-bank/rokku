package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.http.scaladsl.model.Uri
import com.ing.wbaa.rokku.proxy.data.HealthCheck.{ HCMethod, S3ListBucket, Default }
import com.typesafe.config.Config

class StorageS3Settings(config: Config) extends Extension {
  private val storageS3Host: String = config.getString("rokku.storage.s3.host")
  private val storageS3Port: Int = config.getInt("rokku.storage.s3.port")
  val storageS3Schema: String = config.getString("rokku.storage.s3.schema")
  val storageS3Authority: Uri.Authority = Uri.Authority(Uri.Host(storageS3Host), storageS3Port)
  val storageS3AdminAccesskey: String = config.getString("rokku.storage.s3.admin.accesskey")
  val storageS3AdminSecretkey: String = config.getString("rokku.storage.s3.admin.secretkey")
  val awsRegion: String = config.getString("rokku.storage.s3.region")
  val v2SignatureEnabled: Boolean = config.getBoolean("rokku.storage.s3.v2SignatureEnabled")
  val slowdownCodes: Array[Int] = config.getString("rokku.storage.s3.slowdownCodes").split(",").map(o => o.trim.toInt)
  val isRequestUserQueueEnabled: Boolean = config.getBoolean("rokku.storage.s3.request.queue.enable")
  private val hcMethodString = config.getString("rokku.storage.s3.healthCheck.method")
  val hcMethod: HCMethod = hcMethodString match {
    case "s3ListBucket" => S3ListBucket
    case _              => Default
  }
  val hcInterval: Long = config.getLong("rokku.storage.s3.healthCheck.interval")
  val bucketName: String = config.getString("rokku.storage.s3.healthCheck.bucketName")
}

object StorageS3Settings extends ExtensionId[StorageS3Settings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): StorageS3Settings = new StorageS3Settings(system.settings.config)
  override def lookup: ExtensionId[StorageS3Settings] = StorageS3Settings
}
