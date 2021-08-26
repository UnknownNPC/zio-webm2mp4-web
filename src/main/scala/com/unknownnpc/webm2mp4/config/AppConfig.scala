package com.unknownnpc.webm2mp4.config

import zio._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe.TypesafeConfig

object AppConfig {

  type ConfigService = Has[Config]

  case class Config(web: Web, input: Input)
  case class Web(url: String, port: Int)
  case class Input(maxFileSize: Int, tempFolderName: String)

  private val configDescription = DeriveConfigDescriptor.descriptor[Config]

  val live: ULayer[ConfigService] =
    TypesafeConfig.fromDefaultLoader(configDescription).orDie

}
