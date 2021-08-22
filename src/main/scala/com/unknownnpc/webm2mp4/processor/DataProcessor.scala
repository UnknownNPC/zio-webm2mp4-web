package com.unknownnpc.webm2mp4.processor

import com.unknownnpc.webm2mp4.converter.Chunk2FileConverter.Chunk2FileConverterService
import com.unknownnpc.webm2mp4.converter.Webm2Mp4Converter.Webm2Mp4ConverterService
import com.unknownnpc.webm2mp4.validator.InputValidator.InputValidatorService
import zio.{Chunk, Has, IO, URLayer, ZLayer}

import java.io.File

object DataProcessor {

  sealed trait DataProcessorError
  object InvalidInputFormat extends DataProcessorError
  object ConvertorError extends DataProcessorError

  type DataProcessorService = Has[DataProcessor.Service]

  trait Service {
    def process(data: Chunk[Byte]): IO[DataProcessorError, File]
  }

  val live: URLayer[Chunk2FileConverterService with
    Webm2Mp4ConverterService with InputValidatorService, DataProcessorService] = ZLayer.fromServices {
    (a, b, c) => new Service {
      override def process(data: Chunk[Byte]): IO[DataProcessorError, File] = {

      }
    }
  }

}
