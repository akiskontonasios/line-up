package lineup

import lineup.splitters._
import opus.Europarl3
import opus.Europarl2

import org.scalatest.Tag
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

object Extrinsic3 extends Tag("Extrinsic3")
object Intrinsic3 extends Tag("Intrinsic3")

class Europarl3Evaluation extends FunSpec with ShouldMatchers {

	import Europarl3Evaluation._

	describe("Extrinistic Accuracy") {
    it("should be measured", Extrinsic3) {
      import collection.JavaConversions._

      val stat = new StatAlign(translations)
      val splitter = new GermanEnglishSplitter(stat.getWordParser)
      val n = translations.size
      var f = 0
      var skip = 0

      println("Extrinistic accuracy of Europarl3: ")

      for (i <- 0 until (n - 1)) {
        val progress = "%.2f".format((i.asInstanceOf[Float] / n) * 100) + "%"
        val acc = if (i == 0) "?" else math.round((f.asInstanceOf[Float] / (i - skip)) * 100) + "%"
        val msg = progress + " done (" + i + " / " + n + "). Current accuracy is " + acc + "."
        print(msg)

        val sent = stat.getSentences(i, 2)
        val src = new Sentences(stat.getCorpus.get(i).getSourceSentences.mkString(" "), stat.getWordParser)
        val tgt = new Sentences(stat.getCorpus.get(i).getTargetSentences.mkString(" "), stat.getWordParser)

        if (src.getTokens.exists(_.isWord) && tgt.getTokens.exists(_.isWord)) {
          val aligned = splitter.insertLineBreaks(sent);

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

        (0 to (msg.size)).map(_ => print("\b")).map(_ => print(" ")).map(_ => print("\b"))
      }
      println()

      info("Correctly identified %d of %d (%.2f %%) sentence boundaries.".format(
        f, n, (f.asInstanceOf[Float] / (n - skip)) * 100))
    }
  }

  describe("Intrinistic Accuracy") {
    it("should be measured", Intrinsic3) {
      import collection.JavaConversions._

      val stat = new StatAlign(
        java.util.Arrays.asList(translations.map(new Translation(_)): _*))

      var progress = ""
      println("Intrinistic accuracy of Europarl3: ")

      val ms = wordAlignedTranslations.zipWithIndex.map {
        case (tr, i) =>
          for (c <- progress) print("\b")
          progress = math.round((i.asInstanceOf[Float] / wordAlignedTranslations.size) * 100) + "%"
          print(progress)
          tr -> stat.associate(tr, 6)
      }.map { case (tr: Europarl2.Translation, pts: java.util.List[PossibleTranslations]) =>
        val words = tr.words.en.filterNot(w => stat.getWordParser.getWords(w).isEmpty)
        val possibilties = pts.map(_.getCandidates.map(_.getWord))

        if (words.size != pts.size) {
          println("conflict at " + wordAlignedTranslations.indexWhere(_ == tr))
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

      info("N:          " + n + s" (out of ${wordAlignedTranslations.size})")
      info("Precision:  " + precision)
      info("Recall:     " + recall)
      info("F1 Measure: " + f1Measure)
    }
  }
}

object Europarl3Evaluation {
	val translations = Europarl3.translations
  lazy val wordAlignedTranslations: List[Europarl2.Translation] = {
    import scala.util.parsing.json._

    val src = io.Source.fromFile("src/main/resources/europarl3-extract-word-aligned.json").mkString
    val res = JSON.parseFull(src)

    res.map(_.asInstanceOf[List[_]]).map { list =>
      list.map(_.asInstanceOf[Map[String, _]]).map { obj =>
        Europarl2.Translation(
          obj("de").asInstanceOf[String],
          obj("en").asInstanceOf[String],
          obj("word-alignment").asInstanceOf[Map[String, Seq[Int]]].map {
            case (key, values) =>
              (key.toInt + 1) -> values.asInstanceOf[List[Double]].map(_.toInt + 1)
          },
          Some(WordParser.instance))
      }
    }.getOrElse {
      import JSON._

      println(phrase(root)(new lexical.Scanner(src)))
      assert(false, "Could not read word-aligned corpus.")
      List()
    }
  }
}
