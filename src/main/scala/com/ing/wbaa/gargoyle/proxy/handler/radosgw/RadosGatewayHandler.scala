package com.ing.wbaa.gargoyle.proxy.handler.radosgw

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import com.ing.wbaa.gargoyle.proxy.data.User
import com.typesafe.scalalogging.LazyLogging
import org.twonote.rgwadmin4j.{ RgwAdmin, RgwAdminBuilder }

import scala.util.{ Failure, Success, Try }

trait RadosGatewayHandler extends LazyLogging {

  protected[this] implicit def system: ActorSystem

  protected[this] def storageS3Settings: GargoyleStorageS3Settings

  private[this] case class CredentialsOnCeph(accessKey: String, secretKey: String)
  private[this] case class UserOnCeph(uid: String, credentials: List[CredentialsOnCeph])

  private[this] lazy val rgwAdmin: RgwAdmin = new RgwAdminBuilder()
    .accessKey(storageS3Settings.storageS3AdminAccesskey)
    .secretKey(storageS3Settings.storageS3AdminSecretkey)
    .endpoint(s"http://${storageS3Settings.storageS3Authority.host.address()}:${storageS3Settings.storageS3Authority.port}/admin")
    .build

  private[this] def createCredentialsOnCeph(uid: String, accessKey: String, secretKey: String): Boolean = {
    import scala.collection.JavaConverters._

    Try {
      rgwAdmin.createUser(
        uid,
        Map(
          "display-name" -> uid,
          "access-key" -> accessKey,
          "secret-key" -> secretKey
        ).asJava
      )
    } match {
      case Success(user) =>
        logger.info(s"Created on CEPH: " +
          s"UID=${user.getUserId}, " +
          s"AccessKey=${user.getS3Credentials.get(0).getAccessKey}," +
          s"SecretKey=${user.getS3Credentials.get(0).getSecretKey}," +
          s"DisplayName=${user.getDisplayName}")
        true

      case Failure(exc) =>
        logger.error("Unexpected exception during user creation", exc)
        false
    }
  }

  private[this] def getUserOnCeph(uid: String): Option[UserOnCeph] = {
    import scala.collection.JavaConverters._

    Try(rgwAdmin.getUserInfo(uid)).toOption.flatMap(cuo =>
      if (cuo.isPresent) {
        val cephUser = cuo.get
        Some(UserOnCeph(
          cephUser.getUserId,
          cephUser.getS3Credentials.asScala.toList.map(c => CredentialsOnCeph(c.getAccessKey, c.getSecretKey))
        ))
      } else None
    )
  }

  /**
   * Checks how to handle the current inconsistent situation, these optional cases apply:
   *
   * 1. The user with accesskey/secretkey pair doesn't exist yet on S3
   *    solution: with the User information retrieved from the STS service we can create them
   * 2. The user exists, but his accesskey/secretkey pair changed
   *    solution: update accesskey/secretkey
   * 3. Any other reason (e.g. invalid accesskey/secretkey used for this user)
   *    left as is
   *
   * @param userSTS User as retrieved from STS
   * @return True if a change was done on RadosGw
   */
  protected[this] def handleUserCreationRadosGw(userSTS: User): Boolean = {
    getUserOnCeph(userSTS.userName).map(_.credentials) match {

      // User doesn't yet exist on CEPH, create it
      case None =>
        logger.info(s"User from STS doesn't exist yet on CEPH, create it (userSTS: $userSTS)")
        createCredentialsOnCeph(userSTS.userName, userSTS.accessKey, userSTS.secretKey)

      // User on CEPH exists but has no credentials
      case Some(creds) if creds.isEmpty =>
        logger.info(s"User from STS exists on CEPH, but has no credentials. Create them for userSTS: $userSTS")
        createCredentialsOnCeph(userSTS.userName, userSTS.accessKey, userSTS.secretKey)

      // User on CEPH exists but has multiple credentials
      case Some(creds) if creds.size > 1 =>
        logger.error(s"User from STS exists on CEPH, but has multiple credentials (userSTS: $userSTS)")
        false

      // User on CEPH and STS match, so nothing to be done
      case Some(creds) if creds == List(CredentialsOnCeph(userSTS.accessKey, userSTS.secretKey)) =>
        logger.info(s"User from STS exists on CEPH with same credentials already (userSTS: $userSTS)")
        false
    }
  }
}
