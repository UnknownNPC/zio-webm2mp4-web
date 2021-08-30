package com.unknownnpc.webm2mp4

import com.unknownnpc.webm2mp4.config.AppConfig
import com.unknownnpc.webm2mp4.config.AppConfig.{Config, ConfigService}
import com.unknownnpc.webm2mp4.converter.{Stream2FileConverter, Webm2Mp4Converter}
import com.unknownnpc.webm2mp4.processor.DataProcessor
import com.unknownnpc.webm2mp4.processor.DataProcessor.DataProcessorService
import com.unknownnpc.webm2mp4.storage.FileManager
import com.unknownnpc.webm2mp4.storage.FileManager.FileManagerService
import com.unknownnpc.webm2mp4.validator.InputValidator
import com.unknownnpc.webm2mp4.web.WebAPI
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server, ServerChannelFactory}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.config.getConfig
import zio.console.Console
import zio.logging.{LogFormat, LogLevel, Logging, log}

import java.io.File
import java.nio.file.{FileAlreadyExistsException, Files}
import scala.util.{Properties, Success, Try}

object WebServer extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val server: ZIO[EventLoopGroup with ServerChannelFactory with Blocking
      with Logging with ConfigService with DataProcessorService with FileManagerService, Nothing, Unit] = {

      def createDir(folderName: String) = {
        val path = new File(s"./${folderName}").toPath
        Try(Files.createDirectory(path)).recoverWith {
          case _: FileAlreadyExistsException => Success(path)
        }
      }

      for {
        config <- getConfig[Config]
        port <- ZIO.succeed(Properties.envOrElse("PORT", config.web.port.toString).toInt)
        _ <- log.info(s"Crating apps temp folder: ${config.input.tempFolderName}")
        _ <- ZIO.fromTry(createDir(config.input.tempFolderName)).orDie
        _ <- log.info(s"Done")
        _ <- log.info(s"Starting application with next config: $config")
        server <- (Server.port(port) ++
          Server.maxRequestSize(config.input.maxFileSize) ++ Server.app(WebAPI.app)).make
          .use(_ =>
            log.info(s"Server started on port $port")
              *> ZIO.never,
          ).orDie
      } yield server
    }

    val loggingEnv: ZLayer[Console with Clock, Nothing, Logging] =
      Logging.console(
        logLevel = LogLevel.Info,
        format = LogFormat.ColoredLogFormat()
      ) >>> Logging.withRootLoggerName("web-server")
    val loggingEnvWithoutDeps = Console.live ++ Clock.live >>> loggingEnv

    val configService = AppConfig.live
    val blockingService = Blocking.live

    val fileManagerService = FileManager.live
    val fileManagerWithoutDeps = configService >>> fileManagerService

    val stream2FileConverter = Stream2FileConverter.live
    val stream2FileConverterWithoutDeps = (fileManagerWithoutDeps ++ blockingService ++ loggingEnvWithoutDeps) >>> stream2FileConverter

    val webm2Mp4ConverterService = Webm2Mp4Converter.live
    val webm2Mp4ConverterServiceWithoutDeps = fileManagerWithoutDeps ++ loggingEnvWithoutDeps >>> webm2Mp4ConverterService

    val inputValidatorService = InputValidator.live
    val inputValidatorServiceWithoutDeps = configService >>> inputValidatorService

    val dataProcessorService = (stream2FileConverterWithoutDeps ++ webm2Mp4ConverterServiceWithoutDeps ++ inputValidatorServiceWithoutDeps) >>> DataProcessor.live

    val serverDeps = EventLoopGroup.auto(100) ++ ServerChannelFactory.auto ++ blockingService ++
      loggingEnvWithoutDeps ++ configService ++ dataProcessorService ++ fileManagerWithoutDeps
    server
      .provideCustomLayer(serverDeps)
      .exitCode
  }

}
