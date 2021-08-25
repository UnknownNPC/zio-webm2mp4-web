package com.unknownnpc.webm2mp4.web

import com.unknownnpc.webm2mp4.data.RequestData
import com.unknownnpc.webm2mp4.processor.DataProcessor
import com.unknownnpc.webm2mp4.processor.DataProcessor.{ConvertorError, DataProcessorService, InvalidInputFormat, LocalStorageError}
import html.index
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import io.netty.util.AsciiString
import zhttp.http.HttpData.CompleteData
import zhttp.http._
import zio._
import zio.blocking.Blocking
import zio.logging.{Logging, _}
import zio.stream.ZStream

import java.io.{File, IOException}
import java.nio.file.Paths
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

object WebAPI {

  val mp4ContentTypeHeader = Header(HttpHeaderNames.CONTENT_TYPE, AsciiString.cached("video/mp4"))

  val app: HttpApp[Blocking with Logging with DataProcessorService, Throwable] = HttpApp.collectM {
    case Method.GET -> Root =>
      IO.succeed(Response.http(
        content = HttpData.CompleteData(Chunk.fromArray(
          index.render.toString().getBytes(HTTP_CHARSET))
        ),
        headers = List(
          Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML)
        ),
      ))
    case Method.GET -> Root / "static" / name =>
      val content = HttpData.fromStream {
        ZStream.fromFile(
          Paths.get(
            getClass.getClassLoader.getResource(s"web/static/$name").toURI
          )
        )
      }
      val contentTypeHader = name.split("\\.").lastOption match {
        case Some("js") => Header(HttpHeaderNames.CONTENT_TYPE, AsciiString.cached("application/javascript"))
        case Some("css") => Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_CSS)
        case _ => throw new RuntimeException(s"Unknown static format $name")
      }
      val cacheControlHeader = Header(HttpHeaderNames.CACHE_CONTROL, s"${HttpHeaderValues.MAX_AGE}=86400")
      IO.succeed(
        Response.http(content = content, headers = List(contentTypeHader, cacheControlHeader)))

    case rec@Method.POST -> Root / "upload" =>

      def string2HttpData(str: String) = HttpData.CompleteData(Chunk.fromArray(str.getBytes))

      def errorToResponse(error: DataProcessor.DataProcessorError): UIO[Response.HttpResponse[Any, Nothing]] = {
        error match {
          case LocalStorageError => IO.succeed(Response.http(status = Status.INTERNAL_SERVER_ERROR,
            content = string2HttpData("Internal server error (hdd)")))
          case InvalidInputFormat => IO.succeed(Response.http(status = Status.INTERNAL_SERVER_ERROR,
            content = string2HttpData("Invalid input file")))
          case ConvertorError => IO.succeed(Response.http(status = Status.INTERNAL_SERVER_ERROR,
            content = string2HttpData("Internal converter error")))
        }
      }

      def dataToResponse(file: File): UIO[Response.HttpResponse[Blocking, Throwable]] = {
        IO.succeed(Response.http(
          content = HttpData.fromStream(ZStream.fromFile(file.toPath)), headers = List(mp4ContentTypeHeader)))
      }

      rec.data.content match {
        case CompleteData(data) =>
          val multipartDataSource = new ByteArrayDataSource(data.toArray, "multipart/form-data")
          val multipart = new MimeMultipart(multipartDataSource)
          val part = multipart.getBodyPart(0)

          val dataStream: ZStream[Blocking, IOException, Byte] = ZStream.fromInputStreamEffect(ZIO.succeed(part.getInputStream))
          val requestData = RequestData(part.getFileName, part.getSize, dataStream)

          val result: ZIO[Blocking with Logging with DataProcessorService, DataProcessor.DataProcessorError, File] = for {
            dataProcessor <- ZIO.environment[DataProcessorService]
            _ <- log.info(s"Retrieved request with file: ${data.length} bytes")
            response <- dataProcessor.get.process(requestData)
          } yield response
          result.foldM(errorToResponse, dataToResponse)

        case _ => IO.succeed(Response.text("nothing to do here"))
      }
  }
}
