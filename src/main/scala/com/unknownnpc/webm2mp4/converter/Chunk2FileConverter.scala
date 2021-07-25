package com.unknownnpc.webm2mp4.converter

import zio.blocking._
import zio.logging._
import zio.stream.{ZSink, ZStream}
import zio.{Chunk, Has, IO, ZIO, ZLayer}

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.UUID

object Chunk2FileConverter {

  type Chunk2FileConverterService = Has[Chunk2FileConverter.Service]

  trait Service extends Converter[Chunk[Byte], ZIO[Blocking, Throwable, File]]

  val live: ZLayer[Logging with Blocking, Throwable, Chunk2FileConverterService] = ZLayer.fromService { logging => {

    val tmpFolder = Files.createTempDirectory("webm2mp4").toFile.getPath

    def tempFilePath = s"$tmpFolder/${UUID.randomUUID}"

    (from: Chunk[Byte]) => {
      for {
        _ <- logging.info(s"Saving ${from.length} bytes to the next temp file $tempFilePath")
        _ <- ZStream
          .fromIterable(from)
          .run(ZSink.fromFile(Paths.get(tempFilePath)))
        file <- ZIO.succeed(new File(tempFilePath))
      } yield file
    }
  }
  }

  def convert(from: Chunk[Byte]): ZIO[Chunk2FileConverterService with Blocking, Throwable, File] =
    ZIO.accessM(d => d.get.convert(from))

}
