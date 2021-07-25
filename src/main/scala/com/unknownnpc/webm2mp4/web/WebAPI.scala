package com.unknownnpc.webm2mp4.web

import html.index
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import io.netty.util.AsciiString
import zhttp.http.HttpData.CompleteData
import zhttp.http._
import zio._
import zio.blocking.Blocking
import zio.logging.{Logging, _}
import zio.stream.ZStream

import java.nio.file.Paths

object WebAPI {

  val app: HttpApp[Blocking with Logging, Throwable] = HttpApp.collectM {
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
      rec.data.content match {
        case CompleteData(data) =>
          for {
            _ <- log.info(s"Retrieved request with file: ${data.length} bytes")
            response <- ZIO.succeed(Response.text("Done"))
          } yield response

        case _ => IO.succeed(Response.text("nothing to do here"))
      }
  }
}
