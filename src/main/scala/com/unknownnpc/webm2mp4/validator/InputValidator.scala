package com.unknownnpc.webm2mp4.validator

import com.unknownnpc.webm2mp4.config.AppConfig.{Config, ConfigService}
import zio.{Has, IO, URLayer, ZIO, ZLayer}

import java.io.File

object InputValidator {

  type InputValidatorService = Has[InputValidator.Service]

  sealed trait ValidatorResult
  object FileNotFound extends ValidatorResult
  object InvalidInputFileFormat extends ValidatorResult
  object InvalidFileSize extends ValidatorResult

  class Service(config: Config) {
     def validate(input: File): IO[ValidatorResult, Unit] = {
      for {
        _ <- if (input.exists()) ZIO.succeed(()) else ZIO.fail(FileNotFound)
        _ <- if (input.getName.indexOf(".webm") < 0 ) ZIO.fail(InvalidInputFileFormat) else ZIO.succeed(())
        _ <- if (input.length() > config.input.maxFileSize) ZIO.fail(InvalidFileSize) else ZIO.succeed(())
      } yield ()
    }
  }

  val live: URLayer[ConfigService, InputValidatorService] =
    ZLayer.fromService(configEnv => new Service(configEnv))

  def validate(input: File): ZIO[InputValidatorService, ValidatorResult, Unit] =
    ZIO.accessM(d => d.get.validate(input))

}
