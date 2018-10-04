package com.ing.wbaa.gargoyle.proxy.provider

import com.ing.wbaa.gargoyle.proxy.provider.atlas.AtlasModelJsonSupport
import com.ing.wbaa.gargoyle.proxy.provider.atlas.Model.{ Bucket, BucketAttributes, Classification, CreateResponse, Entities, FileAttributes, IngestedFile, Ingestion, IngestionAttributes, Server, ServerAttributes, UpdateResponse, guidRef }
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class LineageProviderAtlasSpec extends WordSpec with DiagrammedAssertions with AtlasModelJsonSupport {
  import spray.json._

  val timestamp = System.currentTimeMillis()

  "Json serverEntities" should {
    "match current schema" in {
      val testServerEntities =
        Entities(Seq(Server("Server", "fakeUser", ServerAttributes("fakeHost", "fakeHost", "fakeHost", "fakeHost"), Seq(Classification("staging_node")))))
          .toJson
          .toString()
      val jsonEntities =
        """{"entities":[{"typeName":"Server","createdBy":"fakeUser","attributes":{"qualifiedName":"fakeHost","name":"fakeHost","server_name":"fakeHost","ip_address":"fakeHost"},"classifications":[{"typeName":"staging_node"}]}]}"""

      assert(testServerEntities == jsonEntities)
    }
  }

  "Json bucketEntities" should {
    "match current schema" in {
      val testBucketEntities =
        Entities(Seq(Bucket("Bucket", "fakeUser", BucketAttributes("fakeBucket", "fakeBucket", "fakeBucket"), Seq(Classification("customer_PII")))))
          .toJson
          .toString()
      val jsonEntities =
        """{"entities":[{"typeName":"Bucket","createdBy":"fakeUser","attributes":{"qualifiedName":"fakeBucket","name":"fakeBucket","bucket_name":"fakeBucket"},"classifications":[{"typeName":"customer_PII"}]}]}"""

      assert(testBucketEntities == jsonEntities)
    }
  }

  "Json file PUT Entities" should {
    "match current schema" in {
      val testFileEntities =
        Entities(Seq(IngestedFile(
          "DataFile",
          "fakeUser",
          FileAttributes("fakeObject", "fakeObject", "fakeObject", "application/octet-stream",
            guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "Bucket"),
            guidRef("4af9ec97-b320-4e3b-9363-5763fa63b03b", "Server"),
            List(guidRef("4af9ec97-b320-4e3b-9363-5763fa63b03b", "Server")),
            List(guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "Bucket")),
            Seq(Classification("customer_PII"))))))
          .toJson
          .toString()
      val jsonEntities =
        """{"entities":[{"typeName":"DataFile","createdBy":"fakeUser","attributes":{"format":"application/octet-stream","name":"fakeObject","Server":{"guid":"4af9ec97-b320-4e3b-9363-5763fa63b03b","typeName":"Server"},"file_name":"fakeObject","outputs":[{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"Bucket"}],"classifications":[{"typeName":"customer_PII"}],"inputs":[{"guid":"4af9ec97-b320-4e3b-9363-5763fa63b03b","typeName":"Server"}],"qualifiedName":"fakeObject","bucket":{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"Bucket"}}}]}"""

      assert(testFileEntities == jsonEntities)
    }
  }

  "Json file GET Entities" should {
    "match current schema" in {
      val testFileEntities =
        Entities(Seq(IngestedFile(
          "DataFile",
          "fakeUser",
          FileAttributes("fakeObject", "fakeObject", "fakeObject", "none/none",
            guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "Bucket"),
            guidRef("4af9ec97-b320-4e3b-9363-5763fa63b03b", "Server"),
            List(guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "Bucket")),
            List(guidRef("4af9ec97-b320-4e3b-9363-5763fa63b03b", "Server")),
            Seq(Classification("customer_PII"))))))
          .toJson
          .toString()
      val jsonEntities =
        s"""{"entities":[{"typeName":"DataFile","createdBy":"fakeUser","attributes":{"format":"none/none","name":"fakeObject","Server":{"guid":"4af9ec97-b320-4e3b-9363-5763fa63b03b","typeName":"Server"},"file_name":"fakeObject","outputs":[{"guid":"4af9ec97-b320-4e3b-9363-5763fa63b03b","typeName":"Server"}],"classifications":[{"typeName":"customer_PII"}],"inputs":[{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"Bucket"}],"qualifiedName":"fakeObject","bucket":{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"Bucket"}}}]}"""

      assert(testFileEntities == jsonEntities)
    }
  }

  "Json process to PUT Entities" should {
    "match current schema" in {
      val testBucketEntities =
        Entities(Seq(Ingestion(
          "aws_cli_script",
          "fakeUser",
          IngestionAttributes(s"aws_cli_${timestamp}", s"aws_cli_${timestamp}", "write", "fakeUser",
            guidRef("4af9ec97-b320-4e3b-9363-5763fa63b03b", "Server"),
            List(guidRef("254f0c29-be7f-4dd6-a188-4cced02b0298", "DataFile")),
            List(guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "Bucket"))))))
          .toJson
          .toString()
      val jsonEntities =
        s"""{"entities":[{"typeName":"aws_cli_script","createdBy":"fakeUser","attributes":{"name":"aws_cli_${timestamp}","Server":{"guid":"4af9ec97-b320-4e3b-9363-5763fa63b03b","typeName":"Server"},"outputs":[{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"Bucket"}],"inputs":[{"guid":"254f0c29-be7f-4dd6-a188-4cced02b0298","typeName":"DataFile"}],"operation":"write","qualifiedName":"aws_cli_${timestamp}","run_as":"fakeUser"}}]}"""

      assert(testBucketEntities == jsonEntities)
    }
  }

  "Json process to GET Entities" should {
    "match current schema" in {
      val testBucketEntities =
        Entities(Seq(Ingestion(
          "aws_cli_script",
          "fakeUser",
          IngestionAttributes(s"aws_cli_${timestamp}", s"aws_cli_${timestamp}", "read", "fakeUser",
            guidRef("4af9ec97-b320-4e3b-9363-5763fa63b03b", "Server"),
            List(guidRef("f0a46ae7-481a-4bbf-a202-44bdff598ab5", "Bucket")),
            List(guidRef("254f0c29-be7f-4dd6-a188-4cced02b0298", "DataFile"))))))
          .toJson
          .toString()
      val jsonEntities =
        s"""{"entities":[{"typeName":"aws_cli_script","createdBy":"fakeUser","attributes":{"name":"aws_cli_${timestamp}","Server":{"guid":"4af9ec97-b320-4e3b-9363-5763fa63b03b","typeName":"Server"},"outputs":[{"guid":"254f0c29-be7f-4dd6-a188-4cced02b0298","typeName":"DataFile"}],"inputs":[{"guid":"f0a46ae7-481a-4bbf-a202-44bdff598ab5","typeName":"Bucket"}],"operation":"read","qualifiedName":"aws_cli_${timestamp}","run_as":"fakeUser"}}]}"""

      assert(testBucketEntities == jsonEntities)
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
