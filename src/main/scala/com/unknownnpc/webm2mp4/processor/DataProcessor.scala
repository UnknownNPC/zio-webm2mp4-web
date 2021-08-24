package com.unknownnpc.webm2mp4.processor

import com.unknownnpc.webm2mp4.converter.Chunk2FileConverter.Chunk2FileConverterService
import com.unknownnpc.webm2mp4.converter.Webm2Mp4Converter.Webm2Mp4ConverterService
import com.unknownnpc.webm2mp4.converter.{Chunk2FileConverter, Webm2Mp4Converter}
import com.unknownnpc.webm2mp4.validator.InputValidator
import com.unknownnpc.webm2mp4.validator.InputValidator.InputValidatorService
import zio.blocking.Blocking
import zio.{Chunk, Has, URLayer, ZIO, ZLayer}

import java.io.File

object DataProcessor {

  sealed trait DataProcessorError
  object LocalStorageError extends DataProcessorError
  object InvalidInputFormat extends DataProcessorError
  object ConvertorError extends DataProcessorError

  type DataProcessorService = Has[DataProcessor.Service]

  trait Service {
    def process(data: Chunk[Byte]): ZIO[Blocking, DataProcessorError, File]
  }

  //https://github.com/zio/zio/issues/3039
  val live: URLayer[Chunk2FileConverterService with Webm2Mp4ConverterService with InputValidatorService, DataProcessorService] =
    ZLayer.fromServices[Chunk2FileConverter.Service, Webm2Mp4Converter.Service, InputValidator.Service, Service] {
      (chunkToFileConverter, webm2mp4Converter, inputValidator) =>
        (input: Chunk[Byte]) => {
          for {
            bytes2File <- chunkToFileConverter.convert(input).orElseFail(LocalStorageError)
            _ <- inputValidator.validate(bytes2File).orElseFail(InvalidInputFormat)
            convertedFile <- webm2mp4Converter.convert(bytes2File).orElseFail(ConvertorError)
          } yield convertedFile
        }
    }

  def process(data: Chunk[Byte]): ZIO[DataProcessorService with Blocking, DataProcessorError, File] =
    ZIO.accessM(d => d.get.process(data))
}
