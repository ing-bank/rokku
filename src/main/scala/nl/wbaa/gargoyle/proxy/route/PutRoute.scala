package nl.wbaa.gargoyle.proxy.route

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{ complete, extractRequestContext, put }
import akka.stream.Materializer
import akka.stream.alpakka.s3.impl.ListBucketVersion2
import akka.stream.alpakka.s3.scaladsl.S3Client
import com.amazonaws.regions.AwsRegionProvider
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.route.CustomDirectives.checkPermission
import akka.stream.alpakka.s3.{ MemoryBufferType, S3Settings }
import com.amazonaws.auth.AWSStaticCredentialsProvider
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.providers.{ Secret, StorageProvider }

import scala.concurrent.ExecutionContext.Implicits.global

case class PutRoute()(implicit provider: StorageProvider, system: ActorSystem, mat: Materializer) extends LazyLogging with RequestHandler {

  /**
   * put route is using alpakka streams to put object in S3 (multipartUpload)
   *
   * @return
   */
  def route() =
    checkPermission { secretKey =>
      put {

        // alpakka settings
        val awsCredentialsProvider = new AWSStaticCredentialsProvider(
          provider.credentials
        )
        val regionProvider = new AwsRegionProvider {
          def getRegion: String = "us-east-1"
        }
        val settings =
          new S3Settings(MemoryBufferType, None, awsCredentialsProvider, regionProvider, true, Some(provider.endpoint), ListBucketVersion2)
        val s3Client = new S3Client(settings)

        //response
        extractRequestContext { ctx =>
          if (validateUserRequest(ctx.request, Secret(secretKey))) {

            val path = ctx.request.uri.path.toString().split("/").toList
            val sink = s3Client.multipartUpload(path(1), path(2), ContentTypes.`application/octet-stream`)
            val resp = ctx.request.entity.withoutSizeLimit().dataBytes.runWith(sink).map(resp => resp.toString)

            complete(resp)
          } else {
            complete(StatusCodes.Unauthorized)
          }
        }
      }
    }
}
