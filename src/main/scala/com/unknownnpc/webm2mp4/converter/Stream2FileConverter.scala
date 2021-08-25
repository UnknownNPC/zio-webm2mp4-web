package com.unknownnpc.webm2mp4.converter

import com.unknownnpc.webm2mp4.data.RequestData
import zio.blocking._
import zio.logging._
import zio.stream.ZSink
import zio.{Has, ZIO, ZLayer}

import java.io.{File, IOException}
import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.util.Try

object Stream2FileConverter {

  type Chunk2FileConverterService = Has[Stream2FileConverter.Service]

  trait Service extends Converter[RequestData, ZIO[Blocking, Throwable, File]]

  val live: ZLayer[Logging with Blocking, Throwable, Chunk2FileConverterService] = ZLayer.fromService { logging => {

    val tmpFolder = Files.createTempDirectory("webm2mp4").toFile.getPath

    def getTempFile(name: String) = Paths.get(s"$tmpFolder/${UUID.randomUUID}_$name").toFile

    (from: RequestData) => {
      val tempFile = getTempFile(from.filename)
      for {
        _ <- logging.info(s"Saving ${from.dataSize} bytes to the next temp file $tempFile")
        _ <- ZIO.fromTry(Try(tempFile.createNewFile()))
        savedBytes <- from.data.run(ZSink.fromFile(tempFile.toPath))
        file <- if (savedBytes == from.dataSize) {
          ZIO.succeed(tempFile)
        } else {
          ZIO.fail(new IOException("Unable to store file"))
        }
      } yield file
    }
  }
  }

  def convert(from: RequestData): ZIO[Chunk2FileConverterService with Blocking, Throwable, File] =
    ZIO.accessM(d => d.get.convert(from))

}
