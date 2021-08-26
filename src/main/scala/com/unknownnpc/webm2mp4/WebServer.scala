package com.unknownnpc.webm2mp4

import com.unknownnpc.webm2mp4.config.AppConfig
import com.unknownnpc.webm2mp4.config.AppConfig.{Config, ConfigService}
import com.unknownnpc.webm2mp4.converter.{Stream2FileConverter, Webm2Mp4Converter}
import com.unknownnpc.webm2mp4.processor.DataProcessor
import com.unknownnpc.webm2mp4.processor.DataProcessor.DataProcessorService
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
import java.nio.file.Files
import scala.util.Try

object WebServer extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val server: ZIO[EventLoopGroup with ServerChannelFactory with Blocking
      with Logging with ConfigService with DataProcessorService, Nothing, Unit] = {
      for {
        config <- getConfig[Config]
        _ <- log.info(s"Crating apps temp folder: ${config.input.tempFolderName}")
        _ <- ZIO.fromTry(Try(Files.createDirectory(new File(s"./${config.input.tempFolderName}").toPath))).orDie
        _ <- log.info(s"Done")
        _ <- log.info(s"Starting application with next config: $config")
        server <- (Server.port(config.web.port) ++
          Server.maxRequestSize(config.input.maxFileSize) ++ Server.app(WebAPI.app)).make
          .use(_ =>
            log.info(s"Server started on port ${config.web.port}")
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

    val stream2FileConverter = Stream2FileConverter.live
    val stream2FileConverterWithoutDeps = (configService ++ blockingService ++ loggingEnvWithoutDeps) >>> stream2FileConverter

    val webm2Mp4ConverterService = Webm2Mp4Converter.live
    val webm2Mp4ConverterServiceWithoutDeps = configService ++ loggingEnvWithoutDeps >>> webm2Mp4ConverterService

    val inputValidatorService = InputValidator.live
    val inputValidatorServiceWithoutDeps = configService >>> inputValidatorService

    val dataProcessorService = (stream2FileConverterWithoutDeps ++ webm2Mp4ConverterServiceWithoutDeps ++ inputValidatorServiceWithoutDeps) >>> DataProcessor.live

    val serverDeps = EventLoopGroup.auto(100) ++ ServerChannelFactory.auto ++ blockingService ++ loggingEnvWithoutDeps ++ configService ++ dataProcessorService
    server
      .provideCustomLayer(serverDeps)
      .exitCode
  }

}
