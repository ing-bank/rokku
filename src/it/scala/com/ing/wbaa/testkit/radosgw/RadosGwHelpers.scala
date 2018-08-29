package com.ing.wbaa.testkit.radosgw

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import com.ing.wbaa.gargoyle.proxy.data.AwsRequestCredential
import com.typesafe.scalalogging.LazyLogging
import org.twonote.rgwadmin4j.RgwAdminBuilder

trait RadosGwHelpers extends LazyLogging {

  protected[this] implicit def testSystem: ActorSystem

  def generateCredentialsOnCeph(uid: String, awsRequestCredential: AwsRequestCredential, awsSecretKey: String): Unit = {
    import scala.collection.JavaConverters._

    val s3authority = GargoyleStorageS3Settings(testSystem).storageS3Authority

    val rgwAdmin = new RgwAdminBuilder()
      .accessKey("accesskey")
      .secretKey("secretkey")
      .endpoint(s"http://${s3authority.host.address()}:${s3authority.port}/admin")
      .build

    val user = rgwAdmin.createUser(uid,
      Map(
        "display-name" -> uid,
        "access-key" -> awsRequestCredential.accessKey.value,
        "secret-key" -> awsSecretKey
      ).asJava
    )

    logger.info(s"Generated on CEPH: " +
      s"UID=${user.getUserId}, " +
      s"AccessKey=${user.getS3Credentials.get(0).getAccessKey}," +
      s"SecretKey=${user.getS3Credentials.get(0).getSecretKey}," +
      s"DisplayName=${user.getDisplayName}")
  }
}
