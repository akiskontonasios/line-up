package lineup

import lineup.splitters._

import opus.Europarl2

import org.scalatest.FunSpec
import org.scalatest.Tag
import org.scalatest.matchers.ShouldMatchers

object Extrinistic2 extends Tag("Extrinistic2")
object Intrinistic2 extends Tag("Intrinistic2")

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

  describe("Extrinistic Accuracy") {
    it("should be measured", Extrinistic2) {
      import collection.JavaConversions._

      val dist = new DistAlign(
        java.util.Arrays.asList(translations.map(new Translation(_)): _*),
        new CustomWordParser)
      val n = translations.size
      var f = 0
      var skip = 0

      println("Extrinistic accuracy of Europarl2: ")

      for (i <- 0 until (n - 1)) {
        val progress = math.round((i.asInstanceOf[Float] / n) * 100) + "%"
        val acc = if (i == 0) "?" else math.round((f.asInstanceOf[Float] / (i - skip)) * 100) + "%"
        print(progress)
        print(" done. Current accuracy is " + acc + ".")

        val sent = dist.getSentences2(i, 2)
        val src = new Sentences(dist.getCorpus.get(i).getSourceSentences.mkString(" "), dist.getWordParser)
        val tgt = new Sentences(dist.getCorpus.get(i).getTargetSentences.mkString(" "), dist.getWordParser)

        if (src.getTokens.exists(_.isWord) && tgt.getTokens.exists(_.isWord)) {
          val aligned = dist.getSplitter().insertLineBreaks(sent);

          // now check if a possible linebreak has been found at the end of the sentence
          val srcEndIndex = aligned._1.indexOf(src.lastWord)
          val srcTail = aligned._1.getTokens.drop(srcEndIndex + 1).takeWhile(_ != LineBreak.instance)
          val srcFound = srcTail.forall(!_.isWord)

          val tgtEndIndex = aligned._2.indexOf(tgt.lastWord)
          val tgtTail = aligned._2.getTokens.drop(tgtEndIndex + 1).takeWhile(_ != LineBreak.instance)
          val tgtFound = tgtTail.forall(!_.isWord)

          if (srcEndIndex == -1 || tgtEndIndex == -1) {
            println()
            println(aligned._1.displayString)
            println(aligned._2.displayString)
            println()
          }

          srcEndIndex should not be (-1)
          tgtEndIndex should not be (-1)

          if (srcFound && tgtFound) {
            f += 1
          }
        } else { // invalid sentence such as 592 in EuroparlV2
          skip += 1
        }

        for (i <- 0 to (progress.size + acc.size + 29)) {
          print("\b")
        }
      }
      println()

      info("Correctly identified %d of %d (%.2f %%) sentence boundaries.".format(
        f, n, (f.asInstanceOf[Float] / (n - skip)) * 100))
    }
  }

	describe("Intrinistic Accuracy") {
    it("should be measured", Intrinistic2) {
  		import collection.JavaConversions._

  		val dist = new DistAlign(
  			java.util.Arrays.asList(translations.map(new Translation(_)): _*),
  			new CustomWordParser)

      var progress = ""
      println("Intrinistic accuracy of Europarl2: ")

  		val ms = translations.zipWithIndex.map {
  			case (tr, i) =>
          for (c <- progress) print("\b")
          progress = math.round((i.asInstanceOf[Float] / translations.size) * 100) + "%"
          print(progress)
          tr -> dist.associate(i, 6)
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
