package lineup

import opus.Europarl2

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

class Europarl2Evaluation extends FunSpec with ShouldMatchers {

	import Europarl2Evaluation._

	describe("Corpus") {
		it("should load all translations") {
			translations.size should be (972)
		}

		it("should process translations successfully") {
			val t = translations.head
			val (word, tx) = t.relations.en(3)

			t.words.en.head should be ("Madam")
			word should be ("on")
			tx should be (List("Det", "gäller"))
		}
	}

	describe("Results") {
		import collection.JavaConversions._

		val dist = new DistAlign(
			java.util.Arrays.asList(translations.map(new Translation(_)): _*),
			new CustomWordParser)

		val ms = translations.zipWithIndex.map {
			case (tr, i) => tr -> dist.associate(i, 6)
		}.map { case (tr: Europarl2.Translation, pts: java.util.List[PossibleTranslations]) =>
			val words = tr.words.en.filterNot(w => dist.getWordParser.getWords(w).isEmpty)
			val possibilties = pts.map(_.getCandidates.map(_.getWord))

			if (words.size != pts.size) {
				println("conflict at " + translations.indexWhere(_ == tr))
				println("words: " + words)
				println("-------")
				pts.foreach(println)
			}
			words.size should be (pts.size)

			val ms = words.zip(possibilties).zipWithIndex.map { case ((word, predicted), i) =>
				val expected = tr.relations.en.zipWithIndex.find {
					case ((w, translations), j) => word == w && j >= i
				}.map(_._1._2).getOrElse(
					throw new RuntimeException("There should be something in here, you know?"))

				val tp: Float = predicted.intersect(expected).size
				val fp: Float = predicted.filterNot(expected.contains).size
				val fn: Float = expected.filterNot(predicted.contains).size

				val precision = Some(tp + fp).filter(_ != 0).map(tp / _).getOrElse(1f)
				val recall = Some(tp + fn).filter(_ != 0).map(tp / _).getOrElse(1f)

				// info(word + ": ")
				// info("  predicted: " + predicted.mkString(", "))
				// info("  actual:    " + expected.mkString(", "))

				(tp, fp, fn)
			}

			val (tp: Float, fp: Float, fn: Float) = (ms.map(_._1).sum, ms.map(_._2).sum, ms.map(_._3).sum)

			val precision = Some(tp + fp).filter(_ != 0).map(tp / _).getOrElse(1f)
			val recall = Some(tp + fn).filter(_ != 0).map(tp / _).getOrElse(1f)

			(precision, recall)
		}

		val n: Float = ms.size
		val (precision, recall) = (ms.map(_._1).sum / n, ms.map(_._2).sum / n)
		val f1Measure = 2 * ((precision * recall) / (precision + recall))

		info("N:          " + n + s" (out of ${translations.size})")
		info("Precision:  " + precision)
		info("Recall:     " + recall)
		info("F1 Measure: " + f1Measure)
	}
}

/**
 *  Results 
 *  
 *  N:          972.0 (out of 972) 
 *  Precision:  0.61426216 
 *  Recall:     0.34737018 
 *  F1 Measure: 0.44377947
 */
object Europarl2Evaluation {
	val translations = Europarl2.translations

	class CustomWordParser extends WordParser {
		import java.util.regex._

		this setWordPattern Pattern.compile(
				"(\\d+(th|rd|nd|st|s))" + "|" +
				"(i\\.e\\.|e\\.g\\.)" + "|" +
				"\\d+-year(-old?)?" + "|" +
				"((\\d+([\\.\\-_]\\d+)?)(_(%|a\\.m\\.?|p\\.m\\.?))?)" + "|" +
				"(\\p{L}[\\p{L}_\\-0-9]*('s)?)");

		// public boolean declension(String a, String b) {
		override def declension(a: String, b: String): Boolean = {
			(math.abs(a.size - b.size) <= 3 && a.replaceAll("'s$", "") == b.replaceAll("'s$", "")) ||
				super.declension(a, b)
		}
	}
}