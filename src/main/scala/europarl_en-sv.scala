package opus

import java.io.{BufferedReader, FileReader}
import lineup.NtoNTranslation
import lineup.WordParser

/**
 * Word-aligned extract from Europarl v2 English to Swedish
 */
object Europarl2 {

	case class Translation(
		en: String,
		sv: String,
		wordMap: Map[Int, Seq[Int]],
		wordParser: Option[WordParser] = None
	) extends NtoNTranslation { ts =>

		object words {
			import scala.collection.JavaConversions._

			lazy val en = wordParser.map(p => p.getWords(ts.en).toIndexedSeq).getOrElse(
				ts.en.split("\\s+").toIndexedSeq)
			lazy val sv = wordParser.map(p => p.getWords(ts.sv).toIndexedSeq).getOrElse(
				ts.sv.split("\\s+").toIndexedSeq)
		}

		object relations {
			lazy val en: Seq[(String, Seq[String])] = words.en.zipWithIndex.map {
				case (word, index) =>
					word -> wordMap.getOrElse(index + 1, Seq()).map(i => words.sv(i - 1))
			}
		}

		def getSourceLanguage = "en"
		def getTargetLanguage = "sv"
		def getSourceSentences = java.util.Arrays.asList(en)
		def getTargetSentences = java.util.Arrays.asList(sv)
	}

	def translations: Seq[Translation] = {
		sentences("en") zip sentences("sv") zip mappings map {
			case ((en, sv), mappings) => Translation(en, sv, mappings)
		}
	}

	def mappings: Seq[Map[Int, Seq[Int]]] = {
		val lines = linesFromFile("src/main/resources/ep-ensv-alignref.v2009-12-08/dev/dev.ensv.naacl")

		lines.map(_.split("\\s+").map(_.toInt)).groupBy(_(0)).toSeq.sortBy(_._1).map { case (no, ms) =>
			ms.map(_.tail).collect {
				case Array(eni, svi) if eni != 0 && svi != 0 => eni -> svi
			}.groupBy(_._1).map {
				case (key, values) => key -> values.map(_._2).toList
			}
		}
	}

	def sentences(lang: String): Seq[String] =
		linesFromFile(s"src/main/resources/ep-ensv-alignref.v2009-12-08/dev/dev.iso.$lang.naacl").map(line =>
			line.substring(line.indexOf(">") + 1, line.lastIndexOf("<")).trim)

	def linesFromFile(file: String): Seq[String] = io.Source.fromFile(file, "UTF-8").getLines.toSeq
}
