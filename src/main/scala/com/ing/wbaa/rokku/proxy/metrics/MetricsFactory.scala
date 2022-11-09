package com.ing.wbaa.rokku.proxy.metrics

import akka.http.scaladsl.model.{ HttpMethod, HttpMethods }
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter

object MetricsFactory {

  val ALL_REQUEST = "requests.status.all.total"
  val SUCCESS_REQUEST = "requests.status.success.total"
  val FAILURE_REQUEST = "requests.status.failure.total"
  val UNAUTHENTICATED_REQUEST = "requests.status.unauthenticated.total"
  val REQUEST_TIME = "requests.nanoseconds.total"
  val REQUEST_TIME_HIST = "requests.time.histogram"
  val HTTP_METHOD = "{httpMethod}"
  val HTTP_DIRECTION = "{InOut}"
  val REQUEST_CONTEXT_LENGTH = s"requests.method.$HTTP_METHOD.$HTTP_DIRECTION.context.length.bytes"
  val REQUEST_CONTEXT_LENGTH_SUM = s"requests.method.$HTTP_METHOD.$HTTP_DIRECTION.context.length.bytes.total"
  val REQUEST_QUEUE_OCCUPIED = "request.queue.occupied"
  val REQUEST_USER = "{user}"
  val REQUEST_QUEUE_OCCUPIED_BY_USER = s"request.queue.occupied.by.$REQUEST_USER"
  val ERROR_REPORTED_TOTAL = "errors.reported.total"
  val OBJECTS_UPLOAD_OPERATIONS_TOTAL = s"requests.method.$HTTP_METHOD.operations.total"
  val KAFKA_SENT_NOTIFICATION_TOTAL = "requests.kafka.notification.sent.total"
  val KAFKA_SENT_NOTIFICATION_ERROR_TOTAL = "requests.kafka.notification.sent.errors.total"
  val REQUEST_STORAGE_TIME = "request.storage.nanoseconds.total"
  val REQUEST_STORAGE_TIME_HIST = "request.storage.time.histogram"
  val REQUEST_STORAGE_TOTAL = "request.storage.total"

  private[this] val metrics = new MetricRegistry()

  JmxReporter.forRegistry(metrics).inDomain("rokku").build.start()

  def registryMetrics(): MetricRegistry = metrics

  def countRequest(name: String, count: Long = 1, countAll: Boolean = true): Unit = {
    metrics.counter(name).inc(count)
    if (countAll) metrics.counter(ALL_REQUEST).inc()
  }

  def markRequestTime(time: Long): Unit = {
    metrics.counter(REQUEST_TIME).inc(time)
    metrics.histogram(REQUEST_TIME_HIST).update(time)
  }

  def incrementRequestQueue(name: String): Unit = {
    metrics.counter(name).inc()
    metrics.counter(REQUEST_QUEUE_OCCUPIED).inc()
  }

  def decrementRequestQueue(name: String): Unit = {
    metrics.counter(name).dec()
    metrics.counter(REQUEST_QUEUE_OCCUPIED).dec()
  }

  def countLogErrors(name: String): Unit = {
    metrics.counter(name).inc()
  }

  def incrementObjectsUploaded(requestMethodName: HttpMethod): Unit = {
    requestMethodName match {
      case HttpMethods.PUT | HttpMethods.POST =>
        metrics.counter(OBJECTS_UPLOAD_OPERATIONS_TOTAL.replace(MetricsFactory.HTTP_METHOD, requestMethodName.value)).inc()
      case _ =>
    }
  }

  def incrementKafkaNotificationsSent(requestMethodName: HttpMethod): Unit = {
    requestMethodName match {
      case HttpMethods.PUT | HttpMethods.POST => metrics.counter(KAFKA_SENT_NOTIFICATION_TOTAL).inc()
      case _                                  =>
    }
  }

  def incrementKafkaSendErrors(): Unit = {
    metrics.counter(KAFKA_SENT_NOTIFICATION_ERROR_TOTAL).inc()
  }

  def markRequestStorageTime(time: Long): Unit = {
    metrics.counter(REQUEST_STORAGE_TIME).inc(time)
    metrics.histogram(REQUEST_STORAGE_TIME_HIST).update(time)
    metrics.counter(REQUEST_STORAGE_TOTAL).inc()
  }
}
