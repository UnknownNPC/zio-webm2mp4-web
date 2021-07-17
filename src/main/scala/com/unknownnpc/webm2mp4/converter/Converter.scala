package com.unknownnpc.webm2mp4.converter

trait Converter[FROM, TO] {

  def convert(from: FROM): TO

}
