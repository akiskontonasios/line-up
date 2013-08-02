package opus

import java.io.{BufferedReader, FileReader}
import lineup.NtoNTranslation

/**
 * Word-aligned extract from Europarl v2 English to Swedish
 */
object Europarl2 {

	case class Translation(
		en: String,
		sv: String,
		wordMap: Map[Int, Seq[Int]]
	) extends NtoNTranslation { ts =>

		object words {
			lazy val en = ts.en.split("\\s+").toIndexedSeq
			lazy val sv = ts.sv.split("\\s+").toIndexedSeq
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