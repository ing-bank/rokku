package com.ing.wbaa.airlock.proxy.metrics

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter

object MetricsFactory {

  val ALL_REQUEST = "airlock.requests.all"
  val SUCCESS_REQUEST = "airlock.requests.success"
  val FAILURE_REQUEST = "airlock.requests.failure"
  val UNAUTHENTICATED_REQUEST = "airlock.requests.unauthenticated"
  val REQUEST_TIME = "airlock.requests.sum.time"
  val REQUEST_TIME_HIST = "airlock.requests.time.histogram"

  private[this] val metrics = new MetricRegistry()

  JmxReporter.forRegistry(metrics).build.start()

  def registryMetrics(): MetricRegistry = metrics

  def countRequest(name: String): Unit = {
    metrics.counter(ALL_REQUEST).inc()
    metrics.counter(name).inc()
  }

  def markRequestTime(time: Long): Unit = {
    metrics.counter(REQUEST_TIME).inc(time)
    metrics.histogram(REQUEST_TIME_HIST).update(time)
  }
}
