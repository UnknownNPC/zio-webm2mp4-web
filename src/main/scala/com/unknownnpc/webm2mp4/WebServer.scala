package com.unknownnpc.webm2mp4

import com.unknownnpc.webm2mp4.config.AppConfig
import com.unknownnpc.webm2mp4.config.AppConfig.{Config, ConfigService}
import com.unknownnpc.webm2mp4.web.WebAPI
import zhttp.service.{EventLoopGroup, Server, ServerChannelFactory}
import zhttp.service.server.ServerChannelFactory
import zio._
import zio.blocking.Blocking
import zio.config.getConfig
import zio.console.{Console, putStrLn}
import zio.random.Random

object WebServer extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val server: ZIO[ConfigService with Console with Random
      with EventLoopGroup with ServerChannelFactory with Blocking, Nothing, Unit] = {
      for {
        config <- getConfig[Config]
        server <- (Server.port(config.web.port) ++ Server.app(WebAPI.app)).make
          .use(_ =>
            console.putStrLn(s"Server started on port ${config.web.port}")
              *> ZIO.never,
          ).orDie
        _ <- putStrLn(s"App config $config").orDie
      } yield server
    }

    val serverDeps = AppConfig.live ++ Console.live ++ ServerChannelFactory.auto ++ EventLoopGroup.auto(100)
    server
      .provideCustomLayer(serverDeps)
      .exitCode
  }

}
