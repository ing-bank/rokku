package com.ing.wbaa.airlock.proxy.provider

import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.atlas.ModelKafka.{ bucketEntity, fsPathEntity, processEntity, pseudoDirEntity, s3ObjectEntity, serverEntity }
import org.scalatest.{ DiagrammedAssertions, WordSpec }
import spray.json.DefaultJsonProtocol

class LineageProviderAtlasSpec extends WordSpec with DiagrammedAssertions with DefaultJsonProtocol {

  import spray.json._

  val timestamp = System.currentTimeMillis()
  val userName = "testuser"
  val localhost = "127.0.0.1"
  val bucket = "user"
  val pseudoDir = "user/testuser"
  val s3Object = "user/testuser/file1.txt"
  val externalPath = "external_object_in/file1.txt"
  val clientType = "aws_cli"
  val ENTITY_ACTIVE = "ACTIVE"
  val dataType = "application/octet-stream"
  val AWS_S3_OBJECT_TYPE = "aws_s3_object"
  val AWS_S3_BUCKET_TYPE = "aws_s3_bucket"
  val AWS_S3_PSEUDO_DIR_TYPE = "aws_s3_pseudo_dir"
  val HADOOP_FS_PATH = "fs_path"
  val AIRLOCK_CLIENT_TYPE = "airlock_client"
  val AIRLOCK_SERVER_TYPE = "server"

  "Json serverEntities" should {
    "match current schema" in {
      val testServerEntities = serverEntity(localhost, userName, 100)
      val jsonEntities =
        """{"id":{"id":"-100","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"server","version":0},"jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Reference","traitNames":[],"traits":{},"typeName":"server","values":{"description":"Request via Airlock","ip_address":"127.0.0.1","name":"127.0.0.1","owner":"testuser","qualifiedName":"127.0.0.1","server_name":"127.0.0.1"}}"""

      assert(testServerEntities == jsonEntities.parseJson)
    }
  }

  "Json bucketEntities" should {
    "match current schema" in {
      val testBucketEntities = bucketEntity(bucket, userName, 100)
      val jsonEntities =
        """{"id":{"id":"-100","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"aws_s3_bucket","version":0},"jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Reference","traitNames":[],"traits":{},"typeName":"aws_s3_bucket","values":{"description":"Request via Airlock","name":"user","owner":"testuser","qualifiedName":"user"}}"""

      assert(testBucketEntities == jsonEntities.parseJson)
    }
  }

  "Json file Entities" should {
    "match current schema" in {
      val testFileEntities = s3ObjectEntity(s3Object, pseudoDir, 200, userName, dataType, 100).toJson
      val jsonEntities =
        """{"id":{"id":"-100","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"aws_s3_object","version":0},"jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Reference","traitNames":[],"traits":{},"typeName":"aws_s3_object","values":{"dataType":"application/octet-stream","description":"Request via Airlock","name":"user/testuser/file1.txt","owner":"testuser","pseudoDirectory":{"id":"-200","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"aws_s3_pseudo_dir","version":0},"qualifiedName":"user/testuser/file1.txt"}}"""

      assert(testFileEntities == jsonEntities.parseJson)
    }
  }

  "Json fsPathEntities" should {
    "match current schema" in {
      val testFsPathEntities = fsPathEntity(externalPath, userName, "external_object_in/file1.txt", 100).toJson
      val jsonEntities =
        """{"id":{"id":"-100","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"fs_path","version":0},"jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Reference","traitNames":[],"traits":{},"typeName":"fs_path","values":{"description":"Request via Airlock","name":"external_object_in/file1.txt","owner":"testuser","path":"external_object_in/file1.txt","qualifiedName":"external_object_in/file1.txt"}}"""
      assert(testFsPathEntities == jsonEntities.parseJson)
    }
  }

  "Json pseudoDirEntities" should {
    "match current schema" in {
      val pseudoDirEntities = pseudoDirEntity(pseudoDir, bucket, 200, userName, 100).toJson
      val jsonEntities =
        """{"id":{"id":"-100","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"aws_s3_pseudo_dir","version":0},"jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Reference","traitNames":[],"traits":{},"typeName":"aws_s3_pseudo_dir","values":{"bucket":{"id":"-200","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"aws_s3_bucket","version":0},"description":"Request via Airlock","name":"user/testuser","objectPrefix":"user/testuser","owner":"testuser","qualifiedName":"user/testuser"}}"""
      assert(pseudoDirEntities == jsonEntities.parseJson)
    }
  }

  "Json process to Read Entities" should {
    "match current schema" in {
      val readProcess = processEntity("aws-cli_500", userName, Read.rangerName,
        localhost, 100,
        s3Object, AWS_S3_OBJECT_TYPE, 200,
        externalPath, HADOOP_FS_PATH, 300, 400).toJson
      val jsonEntities =
        """ {"id":{"id":"-400","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"airlock_client","version":0},"jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Reference","traitNames":[],"traits":{},"typeName":"airlock_client","values":{"description":"Request via Airlock","inputs":[{"id":"-200","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"aws_s3_object","version":0}],"name":"aws-cli_500","operation":"read","outputs":[{"id":"-300","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"fs_path","version":0}],"owner":"testuser","qualifiedName":"aws-cli_500","run_as":"testuser","server":{"id":"-100","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"server","version":0}}}"""
      assert(readProcess == jsonEntities.parseJson)
    }
  }

  "Json process to PUT Entities" should {
    "match current schema" in {
      val writeProcess = processEntity("aws-cli_500", userName, Write.rangerName,
        localhost, 100,
        externalPath, HADOOP_FS_PATH, 200,
        pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, 300, 400).toJson
      val jsonEntities =
        """{"id":{"id":"-400","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"airlock_client","version":0},"jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Reference","traitNames":[],"traits":{},"typeName":"airlock_client","values":{"description":"Request via Airlock","inputs":[{"id":"-200","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"fs_path","version":0}],"name":"aws-cli_500","operation":"write","outputs":[{"id":"-300","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"aws_s3_pseudo_dir","version":0}],"owner":"testuser","qualifiedName":"aws-cli_500","run_as":"testuser","server":{"id":"-100","jsonClass":"org.apache.atlas.typesystem.json.InstanceSerialization$_Id","state":"ACTIVE","typeName":"server","version":0}}}"""
      assert(writeProcess == jsonEntities.parseJson)
    }
  }

}
