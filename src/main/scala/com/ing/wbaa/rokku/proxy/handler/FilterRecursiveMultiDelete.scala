package com.ing.wbaa.rokku.proxy.handler

import akka.stream.ActorMaterializer
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.alpakka.xml.{EndElement, StartElement, TextEvent}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

object FilterRecursiveMultiDelete {

  def exctractMultideleteObjectsFlow(
    source: Source[ByteString, Any]
  )(implicit materializer: ActorMaterializer): Future[Seq[String]] = {
    var isKeyTag = false

    source
      .via(XmlParsing.parser)
      .statefulMapConcat(() => {
        val keys = new ListBuffer[String]
        isKeyTag = false

        parseEvent =>
          parseEvent match {
            case e: StartElement if e.localName.startsWith("Delete") =>
              keys.clear()
              immutable.Seq.empty

            case e: StartElement if e.localName == "Key" =>
              isKeyTag = true
              immutable.Seq.empty

            case e: EndElement if e.localName == "Key" =>
              isKeyTag = false
              immutable.Seq.empty

            case e: TextEvent =>
              if (isKeyTag) keys.append(e.text)
              immutable.Seq.empty

            case e: EndElement if e.localName == "Delete" =>
              immutable.Seq(keys).flatten

            case _ =>
              immutable.Seq.empty
          }
      })
      .runWith(Sink.seq)
  }
}
