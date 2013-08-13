package lineup

import opus.Europarl2

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

class Europarl3Evaluation extends FunSpec with ShouldMatchers {

	import Europarl3Evaluation._
}

object Europarl3Evaluation {
	val translations = Europarl3.translations

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

	def tr2(i: Int) = {
		val t1 = getCorpus.get(i)
		val p1 = associate(i, 6, 3, true)
		val t2 = getCorpus.get(i + 1)
		val p2 = associate(i + 1, 6, 3, true)
		val tp1 = splitter.toSentences(t1.getSourceSentences.mkString(" "), t1.getTargetSentences.mkString(" "), p1)
		val tp2 = splitter.toSentences(t2.getSourceSentences.mkString(" "), t2.getTargetSentences.mkString(" "), p2)
		
		tp1._1.getTokens.addAll(tp2._1.getTokens); tp1._2.getTokens.addAll(tp2._2.getTokens)
		tp1
	}
}