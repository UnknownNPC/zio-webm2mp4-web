package com.unknownnpc.webm2mp4.storage

import com.unknownnpc.webm2mp4.config.AppConfig.ConfigService
import zio.{Has, UIO, URLayer, ZIO, ZLayer}

import java.nio.file.{Path, Paths}
import java.text.SimpleDateFormat
import java.util.{Date, UUID}

object FileManager {

  type FileManagerService = Has[FileManager.Service]

  trait Service {
    def getTempPathBy(name: String): UIO[Path]
    def getOutFilePath(name: String): UIO[Path]
    def getPathBy(name: String): UIO[Path]
  }

  val live: URLayer[ConfigService, FileManagerService] = ZLayer.fromService { configEnv =>
    new Service {

      val outFileDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm")

      override def getTempPathBy(name: String): UIO[Path] =
        ZIO.succeed(Paths.get(s"./${configEnv.input.tempFolderName}/${UUID.randomUUID}_$name"))

      override def getOutFilePath(name: String): UIO[Path] =
        ZIO.succeed(Paths.get(s"./${configEnv.input.tempFolderName}/${name}_${outFileDateFormat.format(new Date())}.mp4"))

      override def getPathBy(name: String): UIO[Path] =
        ZIO.succeed(Paths.get(s"./${configEnv.input.tempFolderName}/$name"))
    }
  }

  def getTempFileBy(name: String): ZIO[FileManagerService, Nothing, Path] =
    ZIO.accessM(d => d.get.getTempPathBy(name))

  def getOutFilePath(name: String): ZIO[FileManagerService, Nothing, Path] =
    ZIO.accessM(d => d.get.getOutFilePath(name))

  def getPathBy(name: String): ZIO[FileManagerService, Nothing, Path] =
    ZIO.accessM(d => d.get.getPathBy(name))

}
