package com.ing.wbaa.airlock.proxy.handler.radosgw

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.ing.wbaa.airlock.proxy.config.StorageS3Settings
import com.ing.wbaa.airlock.proxy.data.{ AwsAccessKey, AwsSecretKey, User, UserName }
import com.typesafe.scalalogging.LazyLogging
import org.twonote.rgwadmin4j.{ RgwAdmin, RgwAdminBuilder }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

trait RadosGatewayHandler extends LazyLogging {

  protected[this] implicit def system: ActorSystem

  protected[this] def storageS3Settings: StorageS3Settings

  protected[this] val s3Client: AmazonS3

  private[this] case class CredentialsOnCeph(awsAccessKey: AwsAccessKey, awsSecretKey: AwsSecretKey)

  private[this] case class UserOnCeph(userName: UserName, credentials: List[CredentialsOnCeph])

  private[this] lazy val rgwAdmin: RgwAdmin = new RgwAdminBuilder()
    .accessKey(storageS3Settings.storageS3AdminAccesskey)
    .secretKey(storageS3Settings.storageS3AdminSecretkey)
    .endpoint(s"http://${storageS3Settings.storageS3Authority.host.address()}:${storageS3Settings.storageS3Authority.port}/admin")
    .build

  private[this] def createCredentialsOnCeph(userName: UserName, awsAccessKey: AwsAccessKey, awsSecretKey: AwsSecretKey): Boolean = {

    Try {
      rgwAdmin.createUser(
        userName.value,
        Map(
          "display-name" -> userName.value,
          "access-key" -> awsAccessKey.value,
          "secret-key" -> awsSecretKey.value
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

  private[this] def updateCredentialsOnCeph(userName: UserName, oldAccessKey: AwsAccessKey, newAccessKey: AwsAccessKey, newSecretKey: AwsSecretKey): Boolean = {
    Try {
      rgwAdmin.removeS3Credential(userName.value, oldAccessKey.value)
      rgwAdmin.createS3Credential(userName.value, newAccessKey.value, newSecretKey.value)
    } match {
      case Success(creds) =>
        logger.info(s"Updated on CEPH: " +
          s"UID=${creds.get(0).getUserId}, " +
          s"AccessKey=${creds.get(0).getAccessKey}," +
          s"SecretKey=${creds.get(0).getSecretKey}")
        true

      case Failure(exc) =>
        logger.error("Unexpected exception during user update", exc)
        false
    }
  }

  private[this] def getUserOnCeph(userName: UserName): Option[UserOnCeph] = {

    Try(rgwAdmin.getUserInfo(userName.value)).toOption.flatMap(cuo =>
      if (cuo.isPresent) {
        val cephUser = cuo.get
        Some(UserOnCeph(
          UserName(cephUser.getUserId),
          cephUser.getS3Credentials.asScala.toList.map(c =>
            CredentialsOnCeph(AwsAccessKey(c.getAccessKey), AwsSecretKey(c.getSecretKey))
          )
        ))
      } else None
    )
  }

  /**
   * Checks how to handle the current inconsistent situation, these optional cases apply:
   *
   * 1. The user with accesskey/secretkey pair doesn't exist yet on S3
   * solution: with the User information retrieved from the STS service we can create them
   * 2. The user exists, but his accesskey/secretkey pair changed
   * solution: update accesskey/secretkey
   * 3. Any other reason (e.g. invalid accesskey/secretkey used for this user)
   * left as is
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

      // User on CEPH exists and has single credential
      case Some(List(CredentialsOnCeph(cephAccessKey, cephSecretKey))) =>
        // User on CEPH and STS match, so nothing to be done
        if (cephAccessKey == userSTS.accessKey && cephSecretKey == userSTS.secretKey) {
          logger.debug(s"User from STS exists on CEPH with same credentials already (userSTS: $userSTS)")
          false
        } // Keys for user on CEPH don't match with keys in STS, update keys in CEPH according to those of sts
        else {
          logger.info(s"Keys for the user from STS don't match those on CEPH (userSTS: $userSTS, userCeph: $cephAccessKey/$cephSecretKey)")
          updateCredentialsOnCeph(userSTS.userName, cephAccessKey, userSTS.accessKey, userSTS.secretKey)
        }
    }
  }

  /**
   * List all buckets - no matters who is the owner
   * @return - list of all buckets
   */
  protected[this] def listAllBuckets: Seq[String] = {
    rgwAdmin.listBucket("").asScala
  }
}
