package com.unknownnpc.webm2mp4.web

import com.unknownnpc.webm2mp4.data.RequestData
import com.unknownnpc.webm2mp4.processor.DataProcessor
import com.unknownnpc.webm2mp4.processor.DataProcessor.{ConvertorError, DataProcessorService, InvalidInputFormat, LocalStorageError}
import com.unknownnpc.webm2mp4.storage.FileManager.FileManagerService
import html.{done, error, index}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import io.netty.util.AsciiString
import zhttp.http.HttpData.CompleteData
import zhttp.http.{Header, _}
import zio._
import zio.blocking.Blocking
import zio.logging.{Logging, _}
import zio.stream.ZStream

import java.io.{File, IOException}
import java.nio.file
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

object WebAPI {

  val mp4ContentTypeHeader = Header(HttpHeaderNames.CONTENT_TYPE, AsciiString.cached("video/mp4"))
  val textHtmlContentTypeHeader = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML)

  val app: HttpApp[Blocking with Logging with DataProcessorService with FileManagerService, Throwable] = HttpApp.collectM {
    case Method.GET -> Root =>
      IO.succeed(Response.http(
        content = HttpData.CompleteData(Chunk.fromArray(
          index.render.toString().getBytes(HTTP_CHARSET))
        ),
        headers = List(textHtmlContentTypeHeader),
      ))

    case Method.GET -> Root / "static" / name =>
      val content = HttpData.fromStream {
        ZStream.fromInputStreamEffect(
          ZIO.succeed(
            getClass.getClassLoader.getResourceAsStream(s"web/static/$name")
          )
        )
      }
      val contentTypeHeader = name.split("\\.").lastOption match {
        case Some("js") => Header(HttpHeaderNames.CONTENT_TYPE, AsciiString.cached("application/javascript"))
        case Some("css") => Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_CSS)
        case _ => throw new RuntimeException(s"Unknown static format $name")
      }
      val cacheControlHeader = Header(HttpHeaderNames.CACHE_CONTROL, s"${HttpHeaderValues.MAX_AGE}=86400")
      IO.succeed(
        Response.http(content = content, headers = List(contentTypeHeader, cacheControlHeader)))

    case Method.GET -> Root / "download" / name =>
      val filePathEffect: ZIO[FileManagerService, Nothing, file.Path] = for {
        fileManger <- ZIO.environment[FileManagerService]
        path <- fileManger.get.getPathBy(name)
      } yield path

      filePathEffect.flatMap(path => {
        val httpData = HttpData.fromStream(ZStream.fromFile(path))
        IO.succeed(
          Response.http(content = httpData, headers = List(mp4ContentTypeHeader)))
      })

    case rec@Method.POST -> Root / "upload" =>

      def errorResponse(errorMsg: String) = {
        IO.succeed(Response.http(
          status = Status.INTERNAL_SERVER_ERROR,
          content = HttpData.CompleteData(Chunk.fromArray(
            error.render(errorMsg).toString().getBytes(HTTP_CHARSET))
          ),
          headers = List(textHtmlContentTypeHeader),
        ))
      }

      def processorErrorToResponse(dataProcessorError: DataProcessor.DataProcessorError): UIO[Response.HttpResponse[Any, Nothing]] = {
        dataProcessorError match {
          case LocalStorageError => errorResponse("Internal server error (hdd)")
          case InvalidInputFormat => errorResponse("Invalid input file")
          case ConvertorError => errorResponse("Internal converter error")
        }
      }

      def dataToResponse(file: File) =
        IO.succeed(Response.http(
          content = HttpData.CompleteData(Chunk.fromArray(
            done.render(file.getName).toString().getBytes(HTTP_CHARSET))
          ),
          headers = List(textHtmlContentTypeHeader),
        ))

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
          result.foldM(processorErrorToResponse, dataToResponse)

        case _ => errorResponse("nothing to do here")
      }
  }
}
