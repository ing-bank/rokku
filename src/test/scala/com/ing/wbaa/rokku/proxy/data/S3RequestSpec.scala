package com.ing.wbaa.rokku.proxy.data

import akka.http.scaladsl.model.{ HttpMethods, MediaTypes, RemoteAddress, Uri }
import org.scalatest.diagrams.Diagrams
import org.scalatest.flatspec.AnyFlatSpec

class S3RequestSpec extends AnyFlatSpec with Diagrams {

  val testCred = AwsRequestCredential(AwsAccessKey("ak"), Some(AwsSessionToken("st")))

  "S3Request" should "parse an S3 request from an http Path and Method" in {
    val result = S3Request(testCred, Uri.Path("/demobucket"), HttpMethods.GET, RemoteAddress.Unknown, HeaderIPs(), MediaTypes.`text/plain`, None)
    assert(result == S3Request(testCred, Some("/demobucket"), None, Read("GET")))
  }

  it should "parse an S3 request from an http Path with object and Method" in {
    val result = S3Request(testCred, Uri.Path("/demobucket/demoobject"), HttpMethods.GET, RemoteAddress.Unknown, HeaderIPs(), MediaTypes.`text/plain`, None)
    assert(result == S3Request(testCred, Some("/demobucket/demoobject"), Some("demoobject"), Read("GET")))
  }

  it should "parse an S3 request from an http Path with subfolder and Method" in {
    val result = S3Request(testCred, Uri.Path("/demobucket/subfolder1/"), HttpMethods.GET, RemoteAddress.Unknown, HeaderIPs(), MediaTypes.`text/plain`, None)
    assert(result == S3Request(testCred, Some("/demobucket/subfolder1/"), None, Read("GET")))
  }

  it should "parse none for bucket if path is only root" in {
    val result = S3Request(testCred, Uri.Path("/"), HttpMethods.GET, RemoteAddress.Unknown, HeaderIPs(), MediaTypes.`text/plain`, None)
    assert(result == S3Request(testCred, None, None, Read("GET")))
  }

  it should "parse none for bucket if path is empty" in {
    val result = S3Request(testCred, Uri.Path(""), HttpMethods.GET, RemoteAddress.Unknown, HeaderIPs(), MediaTypes.`text/plain`, None)
    assert(result == S3Request(testCred, None, None, Read("GET")))
  }

  it should "set access to write for anything but GET" in {
    val result = S3Request(testCred, Uri.Path("/demobucket"), HttpMethods.POST, RemoteAddress.Unknown, HeaderIPs(), MediaTypes.`text/plain`, None)
    assert(result == S3Request(testCred, Some("/demobucket"), None, Post("POST")))
  }

}
