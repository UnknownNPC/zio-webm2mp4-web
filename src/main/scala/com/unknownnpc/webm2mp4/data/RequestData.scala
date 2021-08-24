package com.unknownnpc.webm2mp4.data

import zio.Chunk

case class RequestData(filename: String, data: Chunk[Byte])
