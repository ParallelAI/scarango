package com.outr.arango

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}

sealed abstract class Analyzer(val name: String)

object Analyzer {
  case object Identity extends Analyzer("identity")
  case object TextGerman extends Analyzer("text_de")
  case object TextEnglish extends Analyzer("text_en")
  case object TextSpanish extends Analyzer("text_es")
  case object TextFinnish extends Analyzer("text_fi")
  case object TextFrench extends Analyzer("text_fr")
  case object TextItalian extends Analyzer("text_it")
  case object TextDutch extends Analyzer("text_nl")
  case object TextNorwegian extends Analyzer("text_no")
  case object TextPortuguese extends Analyzer("text_pt")
  case object TextRussian extends Analyzer("text_ru")
  case object TextSwedish extends Analyzer("text_sv")
  case object TextChinese extends Analyzer("text_zh")

  private val map = List(Identity, TextGerman, TextEnglish, TextSpanish, TextFinnish, TextFrench, TextItalian, TextDutch, TextNorwegian, TextPortuguese, TextRussian, TextSwedish, TextChinese).map(a => a.name -> a).toMap

  def apply(name: String): Analyzer = map.getOrElse(name, throw new RuntimeException(s"Unable to find analyzer by name: $name"))

  implicit val decoder: Decoder[Analyzer] = new Decoder[Analyzer] {
    override def apply(c: HCursor): Result[Analyzer] = c.value.asString match {
      case Some(s) => Right(Analyzer(s))
      case None => Left(DecodingFailure(s"Expected String to decode Analyzer, but got: ${c.value}", Nil))
    }
  }

  implicit val encoder: Encoder[Analyzer] = new Encoder[Analyzer] {
    override def apply(a: Analyzer): Json = Json.fromString(a.name)
  }
}