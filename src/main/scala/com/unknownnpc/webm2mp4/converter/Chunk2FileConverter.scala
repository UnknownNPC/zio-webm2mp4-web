package com.unknownnpc.webm2mp4.converter

import zio.blocking._
import zio.logging._
import zio.stream.{ZSink, ZStream}
import zio.{Chunk, Has, IO, ZIO, ZLayer}

import java.io.{File, IOException}
import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.util.Try

object Chunk2FileConverter {

  type Chunk2FileConverterService = Has[Chunk2FileConverter.Service]

  trait Service extends Converter[Chunk[Byte], ZIO[Blocking, Throwable, File]]

  val live: ZLayer[Logging with Blocking, Throwable, Chunk2FileConverterService] = ZLayer.fromService { logging => {

    val tmpFolder = Files.createTempDirectory("webm2mp4").toFile.getPath

    def getTempFile = Paths.get(s"$tmpFolder/${UUID.randomUUID}.webm").toFile

    (from: Chunk[Byte]) => {
      val tempFile = getTempFile
      for {
        _ <- logging.info(s"Saving ${from.length} bytes to the next temp file $tempFile")
        _ <- ZIO.fromTry(Try{
          tempFile.createNewFile()
        })
        runResult <- ZStream
          .fromIterable(from)
          .run(ZSink.fromFile(tempFile.toPath))
        file <- if (runResult == from.length) {
          ZIO.succeed(tempFile)
        } else {
          ZIO.fail(new IOException("Unable to store file"))
        }
      } yield file
    }
  }
  }

  def convert(from: Chunk[Byte]): ZIO[Chunk2FileConverterService with Blocking, Throwable, File] =
    ZIO.accessM(d => d.get.convert(from))

}
