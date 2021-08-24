package com.unknownnpc.webm2mp4.processor

import com.unknownnpc.webm2mp4.converter.Chunk2FileConverter.Chunk2FileConverterService
import com.unknownnpc.webm2mp4.converter.Webm2Mp4Converter.Webm2Mp4ConverterService
import com.unknownnpc.webm2mp4.converter.{Chunk2FileConverter, Webm2Mp4Converter}
import com.unknownnpc.webm2mp4.data.RequestData
import com.unknownnpc.webm2mp4.validator.InputValidator
import com.unknownnpc.webm2mp4.validator.InputValidator.InputValidatorService
import zio.blocking.Blocking
import zio.{Has, URLayer, ZIO, ZLayer}

import java.io.File

object DataProcessor {

  sealed trait DataProcessorError
  object LocalStorageError extends DataProcessorError
  object InvalidInputFormat extends DataProcessorError
  object ConvertorError extends DataProcessorError

  type DataProcessorService = Has[DataProcessor.Service]

  trait Service {
    def process(requestData: RequestData): ZIO[Blocking, DataProcessorError, File]
  }

  //https://github.com/zio/zio/issues/3039
  val live: URLayer[Chunk2FileConverterService with Webm2Mp4ConverterService with InputValidatorService, DataProcessorService] =
    ZLayer.fromServices[Chunk2FileConverter.Service, Webm2Mp4Converter.Service, InputValidator.Service, Service] {
      (chunkToFileConverter, webm2mp4Converter, inputValidator) =>
        (requestData: RequestData) => {
          for {
            bytes2File <- chunkToFileConverter.convert(requestData.data).orElseFail(LocalStorageError)
            _ <- inputValidator.validate(bytes2File).orElseFail(InvalidInputFormat)
            convertedFile <- webm2mp4Converter.convert(bytes2File).orElseFail(ConvertorError)
          } yield convertedFile
        }
    }

  def process(requestData: RequestData): ZIO[DataProcessorService with Blocking, DataProcessorError, File] =
    ZIO.accessM(d => d.get.process(requestData))
}
