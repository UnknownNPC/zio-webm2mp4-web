package com.unknownnpc.webm2mp4.converter

import com.unknownnpc.webm2mp4.storage.FileManager
import com.unknownnpc.webm2mp4.storage.FileManager.FileManagerService
import ws.schild.jave.encode.{AudioAttributes, EncodingAttributes, VideoAttributes}
import ws.schild.jave.{Encoder, MultimediaObject}
import zio.logging._
import zio.{Has, IO, ZIO, ZLayer}

import java.io.File
import scala.util.Try

object Webm2Mp4Converter {

  type Webm2Mp4ConverterService = Has[Webm2Mp4Converter.Service]

  trait Service extends Converter[File, IO[Throwable, File]]

  val live: ZLayer[Logging with FileManagerService, Throwable, Webm2Mp4ConverterService] =
    ZLayer.fromServices[Logger[String], FileManager.Service, Service] { (logger, fileManagerService) => {

      (from: File) => {
        def tryConverting(to: File) = Try {
          val audio = new AudioAttributes
          audio.setCodec(AudioAttributes.DIRECT_STREAM_COPY)

          val video = new VideoAttributes
          video.setCodec("mpeg4")
          video.setBitRate(128000)
          video.setFrameRate(30)
          video.setCodec(VideoAttributes.DIRECT_STREAM_COPY);

          val attrs = new EncodingAttributes
          attrs.setAudioAttributes(audio)
          attrs.setVideoAttributes(video)

          val encoder = new Encoder

          encoder.encode(new MultimediaObject(from), to, attrs)

          to
        }

        for {
          start <- ZIO.succeed(System.currentTimeMillis())
          toPath <- fileManagerService.getOutFilePath(from.getName)
          result <- ZIO.fromTry(tryConverting(toPath.toFile))
          _ <- logger.info(s"Convert time: ${System.currentTimeMillis() - start} ms")
        } yield result
      }
    }
    }

  def convert(from: File): ZIO[Webm2Mp4ConverterService, Throwable, File] =
    ZIO.accessM(_.get.convert(from))

}
