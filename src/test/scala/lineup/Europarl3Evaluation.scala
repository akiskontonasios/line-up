package lineup

import lineup.splitters._
import opus.Europarl3

import org.scalatest.Tag
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

object Extrinistic3 extends Tag("Extrinistic3")
object Intrinistic3 extends Tag("Intrinistic3")

class Europarl3Evaluation extends FunSpec with ShouldMatchers {

	import Europarl3Evaluation._

	describe("Extrinistic Accuracy") {
    it("should be measured", Extrinistic3) {
      import collection.JavaConversions._

      val dist = new DistAlign(translations)
      val n = translations.size
      var f = 0
      var skip = 0

      println("Extrinistic accuracy of Europarl3: ")

      for (i <- 0 until (n - 1)) {
        val progress = "%.2f".format((i.asInstanceOf[Float] / n) * 100) + "%"
        val acc = if (i == 0) "?" else math.round((f.asInstanceOf[Float] / (i - skip)) * 100) + "%"
        val msg = progress + " done (" + i + " / " + n + "). Current accuracy is " + acc + "."
        print(msg)

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

        (0 to (msg.size)).map(_ => print("\b")).map(_ => print(" ")).map(_ => print("\b"))
      }
      println()

      info("Correctly identified %d of %d (%.2f %%) sentence boundaries.".format(
        f, n, (f.asInstanceOf[Float] / (n - skip)) * 100))
    }
  }
}

object Europarl3Evaluation {
	val translations = Europarl3.translations
}
