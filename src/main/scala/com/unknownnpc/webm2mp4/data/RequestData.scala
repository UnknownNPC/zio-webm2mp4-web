package com.unknownnpc.webm2mp4.data

import zio.blocking.Blocking
import zio.stream.ZStream

import java.io.IOException

case class RequestData(filename: String, dataSize: Long, data: ZStream[Blocking, IOException, Byte])
