package com.unknownnpc.webm2mp4.converter

import com.unknownnpc.webm2mp4.config.AppConfig.{Config, ConfigService}
import ws.schild.jave.encode.{AudioAttributes, EncodingAttributes, VideoAttributes}
import ws.schild.jave.{Encoder, MultimediaObject}
import zio.logging._
import zio.{Has, IO, ZIO, ZLayer}

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import scala.util.Try

object Webm2Mp4Converter {

  type Webm2Mp4ConverterService = Has[Webm2Mp4Converter.Service]

  trait Service extends Converter[File, IO[Throwable, File]]

  val live: ZLayer[Logging with ConfigService, Throwable, Webm2Mp4ConverterService] =
    ZLayer.fromServices[Logger[String], Config, Service] { (logger, config) => {

      val outFileDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm")

      def getOutFilePath = (from: File, tempFolderName: String) =>
        s"$tempFolderName/${from.getName}_${outFileDateFormat.format(new Date())}.mp4"

      (from: File) => {
        def tryConverting(tempFolderName: String) = Try {
          val audio = new AudioAttributes
          audio.setCodec(AudioAttributes.DIRECT_STREAM_COPY)

          val video = new VideoAttributes
          video.setCodec("mpeg4")
          video.setBitRate(128000)
          video.setFrameRate(30)

          val attrs = new EncodingAttributes
          attrs.setAudioAttributes(audio)
          attrs.setVideoAttributes(video)

          val encoder = new Encoder
          val to = new File(getOutFilePath(from, tempFolderName))
          encoder.encode(new MultimediaObject(from), to, attrs)

          to
        }

        val start = System.currentTimeMillis()
        for {
          result <- ZIO.fromTry(tryConverting(config.input.tempFolderName))
          _ <- logger.info(s"Convert time: ${System.currentTimeMillis() - start} ms")
        } yield result
      }
    }
    }

  def convert(from: File): ZIO[Webm2Mp4ConverterService, Throwable, File] =
    ZIO.accessM(d => d.get.convert(from))

}
