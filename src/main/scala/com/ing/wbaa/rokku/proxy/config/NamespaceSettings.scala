package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.amazonaws.auth.BasicAWSCredentials
import com.ing.wbaa.rokku.proxy.handler.namespace.NamespaceName
import com.typesafe.config.Config

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

class NamespaceSettings(config: Config) extends Extension {

  private val namespaceCredentialsVarPrefix = config.getString("rokku.namespaces.env.var.credentials.prefix")
  private val namespaceCredentialsFromEnv =
    System.getenv().asScala.filter(_._1.startsWith(namespaceCredentialsVarPrefix)).toSeq
      .filter { case (_, v) =>
        Try {
          val result = v.split(",")
          result.length == 2 && result(0).trim.nonEmpty && result(1).trim.nonEmpty
        } match {
          case Failure(_)               => false
          case Success(credentialsOk)   => credentialsOk
        }
      }
      .sortBy(_._1)
      .map {
        case (k, v) => NamespaceName(k) ->
          new BasicAWSCredentials(v.split(",")(0).trim, v.split(",")(1).trim)
      }
  val isEnabled: Boolean = config.getBoolean("rokku.namespaces.enable")
  val namespaceCredentialsMap: ListMap[NamespaceName, BasicAWSCredentials] =
    ListMap(namespaceCredentialsFromEnv: _*)
}

object NamespaceSettings extends ExtensionId[NamespaceSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): NamespaceSettings = new NamespaceSettings(system.settings.config)
  override def lookup: ExtensionId[NamespaceSettings] = NamespaceSettings
}
