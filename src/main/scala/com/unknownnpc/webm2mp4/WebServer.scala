package com.unknownnpc.webm2mp4

import com.unknownnpc.webm2mp4.config.AppConfig
import com.unknownnpc.webm2mp4.config.AppConfig.{Config, ConfigService}
import com.unknownnpc.webm2mp4.converter.Chunk2FileConverter
import com.unknownnpc.webm2mp4.converter.Chunk2FileConverter.Chunk2FileConverterService
import com.unknownnpc.webm2mp4.web.WebAPI
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server, ServerChannelFactory}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.config.getConfig
import zio.console.{Console, putStrLn}
import zio.logging.{LogFormat, LogLevel, Logging, log}
import zio.random.Random

object WebServer extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val server: ZIO[ConfigService with Random
      with EventLoopGroup with ServerChannelFactory with Blocking
      with Logging, Nothing, Unit] = {
      for {
        config <- getConfig[Config]
        _ <- log.info(s"App config $config")
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

    val chuck2FileConverter: ZLayer[Logging with Blocking, Throwable, Chunk2FileConverterService] = Chunk2FileConverter.live
    val chuck2FileConverterWithoutDeps = (Blocking.live ++ loggingEnvWithoutDeps) >>> chuck2FileConverter

    val serverDeps = AppConfig.live ++ loggingEnvWithoutDeps ++ ServerChannelFactory.auto ++ EventLoopGroup.auto(100)
    server
      .provideCustomLayer(serverDeps)
      .exitCode
  }

}
