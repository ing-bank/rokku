package com.ing.wbaa.rokku.proxy.provider

import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.provider.atlas.ModelKafka._
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec
import spray.json.DefaultJsonProtocol

class LineageProviderAtlasSpec extends AnyWordSpec with Diagrams with DefaultJsonProtocol {

  import spray.json._

  val timestamp = System.currentTimeMillis()
  val userName = "testuser"
  val localhost = "127.0.0.1"
  val bucket = "user"
  val pseudoDir = "user/testuser/"
  val s3Object = Some("s3://user/testuser/file1.txt")
  val externalPath = "external_object_in/file1.txt"
  val clientType = "aws_cli"
  val ENTITY_ACTIVE = "ACTIVE"
  val dataType = "application/octet-stream"
  val AWS_S3_OBJECT_TYPE = "aws_s3_object"
  val AWS_S3_BUCKET_TYPE = "aws_s3_bucket"
  val AWS_S3_PSEUDO_DIR_TYPE = "aws_s3_pseudo_dir"
  val HADOOP_FS_PATH = "fs_path"
  val ROKKU_CLIENT_TYPE = "rokku_client"
  val ROKKU_SERVER_TYPE = "server"
  val created = System.currentTimeMillis()
  val user = User(UserName("okUser"), Set(UserGroup("okGroup")), AwsAccessKey("accesskey"), AwsSecretKey("secretkey"), UserAssumeRole(""))

  "Json serverEntities" should {
    "match current schema" in {
      val testServerEntities = serverEntity(localhost, userName, 100, created).get
      val jsonEntities =
        """{"attributes":{"description":"Request via Rokku","ip_address":"127.0.0.1","name":"127.0.0.1","owner":"testuser","qualifiedName":"127.0.0.1","server_name":"127.0.0.1"},"classifications":[],"guid":"-100","typeName":"server"}"""
      assert(testServerEntities == jsonEntities.parseJson)
    }
  }

  "Json create bucketEntities" should {
    "match current schema" in {
      val testBucketEntities = createBucketEntity(bucket, userName, 100, List.empty[String], created).get
      val jsonEntities =
        """{"attributes":{"createtime":"""" + created + """","description":"Request via Rokku","name":"user","owner":"testuser","qualifiedName":"s3://user"},"classifications":[],"guid":"-100","typeName":"aws_s3_bucket"}"""

      assert(testBucketEntities == jsonEntities.parseJson)
    }
  }

  "Json update bucketEntities" should {
    "match current schema" in {
      val testBucketEntities = updateBucketEntity(bucket, 100).get
      val jsonEntities =
        """{"attributes":{"name":"user","qualifiedName":"s3://user"},"classifications":[],"guid":"-100","typeName":"aws_s3_bucket"}"""

      assert(testBucketEntities == jsonEntities.parseJson)
    }
  }

  "Json file Entities" should {
    "match current schema" in {
      val testFileEntities = s3ObjectEntity(s3Object, pseudoDir, 200, userName, dataType, 100, None, List.empty[String], created).get.toJson
      val jsonEntities =
        """{"attributes":{"awsTags":[],"createTime":"""" + created + """","dataType":"application/octet-stream","description":"Request via Rokku","name":"file1.txt","owner":"testuser","pseudoDirectory":{"guid":"-200","state":"ACTIVE","typeName":"aws_s3_pseudo_dir","version":0},"qualifiedName":"s3://user/testuser/file1.txt"},"classifications":[],"guid":"-100","typeName":"aws_s3_object"}"""

      assert(testFileEntities == jsonEntities.parseJson)
    }

    "match current schema with awsTags" in {
      val testFileEntities = s3ObjectEntity(s3Object, pseudoDir, 200, userName, dataType, 100, Some(Map("key1" -> "value1")), List.empty[String], created).get.toJson
      val jsonEntities =
        """{"attributes":{"awsTags":[{"attributes":{"key":"key1","value":"value1"},"typeName":"aws_tag"}],"createTime":"""" + created + """","dataType":"application/octet-stream","description":"Request via Rokku","name":"file1.txt","owner":"testuser","pseudoDirectory":{"guid":"-200","state":"ACTIVE","typeName":"aws_s3_pseudo_dir","version":0},"qualifiedName":"s3://user/testuser/file1.txt"},"classifications":[],"guid":"-100","typeName":"aws_s3_object"}"""

      assert(testFileEntities == jsonEntities.parseJson)
    }
  }

  "Json fsPathEntities" should {
    "match current schema" in {
      val testFsPathEntities = fsPathEntity(externalPath, userName, "external_object_in/file1.txt", 100, created).toJson
      val jsonEntities =
        """{"attributes":{"createTime":"""" + created + """","description":"Request via Rokku","modifiedTime":"""" + created + """","name":"external_object_in/file1.txt","owner":"testuser","path":"external_object_in/file1.txt","qualifiedName":"external_object_in/file1.txt"},"classifications":[],"guid":"-100","typeName":"fs_path"}"""
      assert(testFsPathEntities == jsonEntities.parseJson)
    }
  }

  "Json pseudoDirEntities" should {
    "match current schema" in {
      val pseudoDirEntities = pseudoDirEntity(pseudoDir, bucket, 200, userName, 100, List.empty[String], created).get.toJson
      val jsonEntities =
        """{"attributes":{"bucket":{"guid":"-200","state":"ACTIVE","typeName":"aws_s3_bucket","version":0},"createTime":"""" + created + """","description":"Request via Rokku","name":"/testuser/","objectPrefix":"s3://user/testuser/","owner":"testuser","qualifiedName":"s3://user/testuser/"},"classifications":[],"guid":"-100","typeName":"aws_s3_pseudo_dir"}"""
      assert(pseudoDirEntities == jsonEntities.parseJson)
    }
  }

  "Json process to Read Entities" should {
    "match current schema" in {
      val readProcess = processEntity("aws-cli_500", userName, Read().rangerName,
        localhost, 100,
        s3Object.get, AWS_S3_OBJECT_TYPE, 200,
        externalPath, HADOOP_FS_PATH, 300, 400, created).get.toJson
      val jsonEntities =
        """{"attributes":{"description":"Request via Rokku","inputs":[{"guid":"-200","state":"ACTIVE","typeName":"aws_s3_object","version":0}],"name":"aws-cli_500","operation":"read","outputs":[{"guid":"-300","state":"ACTIVE","typeName":"fs_path","version":0}],"owner":"testuser","qualifiedName":"aws-cli_500","run_as":"testuser","server":{"guid":"-100","state":"ACTIVE","typeName":"server","version":0}},"classifications":[],"guid":"-400","typeName":"rokku_client"}"""
      assert(readProcess == jsonEntities.parseJson)
    }
  }

  "Json process to PUT Entities" should {
    "match current schema" in {
      val writeProcess = processEntity("aws-cli_500", userName, Write().rangerName,
        localhost, 100,
        externalPath, HADOOP_FS_PATH, 200,
        s3Object.get, AWS_S3_OBJECT_TYPE, 300, 400, created).toJson
      val jsonEntities =
        """{"attributes":{"description":"Request via Rokku","inputs":[{"guid":"-200","state":"ACTIVE","typeName":"fs_path","version":0}],"name":"aws-cli_500","operation":"write","outputs":[{"guid":"-300","state":"ACTIVE","typeName":"aws_s3_object","version":0}],"owner":"testuser","qualifiedName":"aws-cli_500","run_as":"testuser","server":{"guid":"-100","state":"ACTIVE","typeName":"server","version":0}},"classifications":[],"guid":"-400","typeName":"rokku_client"}"""
      assert(writeProcess == jsonEntities.parseJson)
    }
  }

  "Json prepareEntityDeleteMessage" should {
    "match current schema" in {
      val deleteMessage = prepareEntityDeleteMessage(user, "deleteName", "deleteType")
      val jsonEntities = """{"entities":[{"typeName":"deleteType","uniqueAttributes":{"qualifiedName":"deleteName"}}],"type":"ENTITY_DELETE_V2","user":"okUser"}"""
      assert(deleteMessage == jsonEntities.parseJson)
    }
  }

}
