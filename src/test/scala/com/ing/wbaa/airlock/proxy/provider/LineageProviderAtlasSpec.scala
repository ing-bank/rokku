package com.ing.wbaa.airlock.proxy.provider

import com.ing.wbaa.airlock.proxy.provider.atlas.ModelJsonSupport
import com.ing.wbaa.airlock.proxy.provider.atlas.Model.{ Bucket, BucketAttributes, BucketObject, BucketObjectAttributes, Classification, ClientProcess, ClientProcessAttributes, CreateResponse, Entities, FsPath, FsPathAttributes, Server, ServerAttributes, UpdateResponse, guidRef }
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class LineageProviderAtlasSpec extends WordSpec with DiagrammedAssertions with ModelJsonSupport {
  import spray.json._

  val timestamp = System.currentTimeMillis()

  "Json serverEntities" should {
    "match current schema" in {
      val testServerEntities =
        Entities(Seq(Server("server", "fakeUser", ServerAttributes("fakeHost", "fakeHost", "fakeHost", "fakeHost"), Seq(Classification("staging_node")))))
          .toJson
          .toString()
      val jsonEntities =
        """{"entities":[{"typeName":"server","createdBy":"fakeUser","attributes":{"qualifiedName":"fakeHost","name":"fakeHost","server_name":"fakeHost","ip_address":"fakeHost"},"classifications":[{"typeName":"staging_node"}]}]}"""

      assert(testServerEntities == jsonEntities)
    }
  }

  "Json bucketEntities" should {
    "match current schema" in {
      val testBucketEntities =
        Entities(Seq(Bucket("Bucket", BucketAttributes("fakeBucket", "fakeBucket"), Seq(Classification("customer_PII")))))
          .toJson
          .toString()
      val jsonEntities =
        """{"entities":[{"typeName":"Bucket","attributes":{"qualifiedName":"fakeBucket","name":"fakeBucket"},"classifications":[{"typeName":"customer_PII"}]}]}"""

      assert(testBucketEntities == jsonEntities)
    }
  }

  "Json file PUT Entities" should {
    "match current schema" in {
      val testFileEntities =
        Entities(Seq(BucketObject(
          "aws_s3_object",
          BucketObjectAttributes("fakeObject", "fakeObject", "application/octet-stream",
            guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "aws_s3_pseudo_dir"),
            Seq(Classification("customer_PII"))))))
          .toJson
          .toString()
      val jsonEntities =
        """{"entities":[{"typeName":"aws_s3_object","attributes":{"name":"fakeObject","pseudoDirectory":{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"aws_s3_pseudo_dir"},"classifications":[{"typeName":"customer_PII"}],"qualifiedName":"fakeObject","dataType":"application/octet-stream"}}]}"""

      assert(testFileEntities == jsonEntities)
    }
  }

  "Json file GET Entities" should {
    "match current schema" in {
      val testFileEntities =
        Entities(Seq(BucketObject(
          "aws_s3_object",
          BucketObjectAttributes("fakeObject", "fakeObject", "none/none",
            guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "aws_s3_pseudo_dir"),
            Seq(Classification("customer_PII"))))))
          .toJson
          .toString()
      val jsonEntities =
        s"""{"entities":[{"typeName":"aws_s3_object","attributes":{"name":"fakeObject","pseudoDirectory":{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"aws_s3_pseudo_dir"},"classifications":[{"typeName":"customer_PII"}],"qualifiedName":"fakeObject","dataType":"none/none"}}]}"""

      assert(testFileEntities == jsonEntities)
    }
  }

  "Json process to PUT Entities" should {
    "match current schema" in {
      val testBucketEntities =
        Entities(Seq(ClientProcess(
          "aws_cli_script",
          "fakeUser",
          ClientProcessAttributes(s"aws_cli_${timestamp}", s"aws_cli_${timestamp}", "write", "fakeUser",
            guidRef("4af9ec97-b320-4e3b-9363-5763fa63b03b", "server"),
            List(guidRef("254f0c29-be7f-4dd6-a188-4cced02b0298", "aws_s3_object")),
            List(guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "aws_s3_pseudo_dir"))))))
          .toJson
          .toString()
      val jsonEntities =
        s"""{"entities":[{"typeName":"aws_cli_script","createdBy":"fakeUser","attributes":{"name":"aws_cli_${timestamp}","server":{"guid":"4af9ec97-b320-4e3b-9363-5763fa63b03b","typeName":"server"},"outputs":[{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"aws_s3_pseudo_dir"}],"inputs":[{"guid":"254f0c29-be7f-4dd6-a188-4cced02b0298","typeName":"aws_s3_object"}],"operation":"write","qualifiedName":"aws_cli_${timestamp}","run_as":"fakeUser"}}]}"""

      assert(testBucketEntities == jsonEntities)
    }
  }

  "Json process to GET Entities" should {
    "match current schema" in {
      val testBucketEntities =
        Entities(Seq(ClientProcess(
          "aws_cli_script",
          "fakeUser",
          ClientProcessAttributes(s"aws_cli_${timestamp}", s"aws_cli_${timestamp}", "read", "fakeUser",
            guidRef("4af9ec97-b320-4e3b-9363-5763fa63b03b", "server"),
            List(guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "aws_s3_pseudo_dir")),
            List(guidRef("254f0c29-be7f-4dd6-a188-4cced02b0298", "aws_s3_object"))))))
          .toJson
          .toString()
      val jsonEntities =
        s"""{"entities":[{"typeName":"aws_cli_script","createdBy":"fakeUser","attributes":{"name":"aws_cli_${timestamp}","server":{"guid":"4af9ec97-b320-4e3b-9363-5763fa63b03b","typeName":"server"},"outputs":[{"guid":"254f0c29-be7f-4dd6-a188-4cced02b0298","typeName":"aws_s3_object"}],"inputs":[{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"aws_s3_pseudo_dir"}],"operation":"read","qualifiedName":"aws_cli_${timestamp}","run_as":"fakeUser"}}]}"""

      assert(testBucketEntities == jsonEntities)
    }
  }

  "Json fsPathEntities" should {
    "match current schema" in {
      val testFsPathEntities =
        Entities(Seq(FsPath(
          "fs_path",
          FsPathAttributes("external_object/object1", "external_object/object1", "external_object/object1")
        )))
          .toJson
          .toString()
      val jsonEntities =
        """{"entities":[{"typeName":"fs_path","attributes":{"qualifiedName":"external_object/object1","name":"external_object/object1","path":"external_object/object1"}}]}"""
      assert(testFsPathEntities == jsonEntities)
    }
  }

  "Json createResponse" should {
    "parse a entity create response" that {
      "has a guid" in {
        val jsonString = """{"mutatedEntities":{"CREATE":[{"typeName":"Server","attributes":{"server_name":"someServer","qualifiedName":"someServer","ip_address":"127.0.0.1"},"guid":"47e47394-4555-4288-94f0-1ba1c4d4fab1","status":"ACTIVE"}]},"guidAssignments":{"-497649638924":"47e47394-4555-4288-94f0-1ba1c4d4fab1"}}"""
        val guidString = jsonString.parseJson.convertTo[CreateResponse].guidAssignments.convertTo[Map[String, String]].values.toList.head
        assert(guidString.length == 36)
      }
    }
  }

  "Json updateResponse" should {
    "parse a entity update response" that {
      "has a guid" in {
        val jsonString = """{"guidAssignments":{"-497649638928":"47e47394-4555-4288-94f0-1ba1c4d4fab1"}}"""
        val guidString = jsonString.parseJson.convertTo[UpdateResponse].guidAssignments.convertTo[Map[String, String]].values.toList.head
        assert(guidString.length == 36)
      }
    }
  }

}
