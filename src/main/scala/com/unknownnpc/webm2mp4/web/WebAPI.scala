package com.unknownnpc.webm2mp4.web

import html.index
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import io.netty.util.AsciiString
import zhttp.http._
import zio._
import zio.blocking.Blocking
import zio.stream.ZStream

import java.nio.file.Paths

object WebAPI {

  val app: HttpApp[Blocking, Throwable] = HttpApp.collect {
    case Method.GET -> Root =>
      Response.http(
        content = HttpData.CompleteData(Chunk.fromArray(
          index.render.toString().getBytes(HTTP_CHARSET))
        ),
        headers = List(
          Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML)
        ),
      )
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
      Response.http(content = content, headers = List(contentTypeHader, cacheControlHeader))
  }
}
