package com.unknownnpc.webm2mp4.validator

import com.unknownnpc.webm2mp4.config.AppConfig.ConfigService
import zio.{Has, IO, URLayer, ZIO, ZLayer}

import java.io.File

object InputValidator {

  type InputValidatorService = Has[InputValidator.Service]

  sealed trait ValidatorResult
  object FileNotFound extends ValidatorResult
  object InvalidInputFileFormat extends ValidatorResult
  object InvalidFileSize extends ValidatorResult

  trait Service {
    def validate(input: File): IO[ValidatorResult, Unit]
  }

  val live: URLayer[ConfigService, InputValidatorService] =
    ZLayer.fromService(configEnv => (input: File) => {
      for {
        _ <- if (input.exists()) ZIO.succeed(()) else ZIO.fail(FileNotFound)
        _ <- if (input.getName.indexOf(".webm") < 0) ZIO.fail(InvalidInputFileFormat) else ZIO.succeed(())
        _ <- if (input.length() > configEnv.input.maxFileSize) ZIO.fail(InvalidFileSize) else ZIO.succeed(())
      } yield ()
    })

  def validate(input: File): ZIO[InputValidatorService, ValidatorResult, Unit] =
    ZIO.accessM(d => d.get.validate(input))

}
