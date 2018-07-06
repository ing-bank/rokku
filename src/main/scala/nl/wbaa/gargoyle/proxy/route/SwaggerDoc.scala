package nl.wbaa.gargoyle.proxy.route

import com.github.swagger.akka.model.Info
import com.github.swagger.akka.SwaggerHttpService
import com.typesafe.config.ConfigFactory
import io.swagger.models.Scheme

/**
  * Swagger docs endpoint configuration
  */
object SwaggerDoc extends SwaggerHttpService {
  private val config = ConfigFactory.load().getConfig("api.server")
  override val apiClasses: Set[Class[_]] = Set(classOf[GetRoute], classOf[PostRoute], classOf[DeleteRoute], classOf[PutRoute])
  override val host = s"${config.getString("ip")}:${config.getString("port")}" //the url of your api, not swagger's json endpoint
  override val basePath = "/" //the basePath for the API you are exposing
  override val apiDocsPath = "api-docs" //where you want the swagger-json endpoint exposed
  override val info = Info() //provides license and other description details
  //override val schemes = List(Scheme.HTTPS)
}
