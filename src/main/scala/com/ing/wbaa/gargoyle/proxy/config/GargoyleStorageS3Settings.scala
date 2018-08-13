package com.ing.wbaa.gargoyle.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config

class GargoyleStorageS3Settings(config: Config) extends Extension {
  private val storageS3Host: String = config.getString("gargoyle.storage.s3.host")
  private val storageS3Port: Int = config.getInt("gargoyle.storage.s3.port")
  val storageS3Authority = Uri.Authority(Uri.Host(storageS3Host), storageS3Port)
}

object GargoyleStorageS3Settings extends ExtensionId[GargoyleStorageS3Settings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleStorageS3Settings = new GargoyleStorageS3Settings(system.settings.config)
  override def lookup(): ExtensionId[GargoyleStorageS3Settings] = GargoyleStorageS3Settings
}
