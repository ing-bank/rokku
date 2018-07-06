package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, extractRequestContext, put}
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.providers.StorageProvider
import nl.wbaa.gargoyle.proxy.route.CustomDirectives.validateToken
import nl.wbaa.gargoyle.proxy.response.ResponseHelpers._

import scala.util.{Failure, Success, Try}


case class PutRoute()(implicit provider: StorageProvider) extends LazyLogging {

  def route() =
    validateToken { tokenOk =>
      put {
        extractRequestContext { ctx =>
          Try(provider.translateRequest(ctx.request)) match {
            case Success(s3Response) =>
              stringComplete(s3Response)
            case Failure(ex) => // all exceptions for now
              throw new Exception(ex.getMessage)
          }
        }
      }
    }
}
