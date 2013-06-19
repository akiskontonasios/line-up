package opus

import sys.process._
import xml._
import java.net._
import java.io._
import java.util.zip.GZIPInputStream

case class Translation(de: Seq[String], en: Seq[String]) {
  override def toString =
    "\nde %d: %s\nen %d: %s".format(de.size, de.mkString(" | "), en.size, en.mkString(" | "))
}

object Europarl3 {

  def bodyTag(name: String) = s"<$name[^/]*</$name>".r
  def emptyTag(name: String) = s"<$name[^>]*>".r

  val link = bodyTag("a")
  val href = "\"([^\"]*)\"".r

  val baseUrl = "http://opus.lingfil.uu.se/Europarl3/xml"
  def url(lang: String): String = s"$baseUrl/$lang/"

  def markup(lang: String): String = readURL(url(lang), in => io.Source.fromInputStream(in).mkString)

  def fetchFiles(lang: String): Iterator[String] = link.findAllIn(markup(lang)).
    filter(_.contains(".xml.gz")).flatMap(link => href.findAllIn(link).map(_.replace("\"", "")))

  def readURL[R](url: String, read: InputStream => R): R = {
    val in = new BufferedInputStream(new URL(url).openStream());
    try {
      read(in)
    } finally {
      if (in ne null) {
        try {
          in.close()
        } catch {
          case e: IOException => {}
        }
      }
    }
  }

  lazy val files = fetchFiles("de-en").map(file => url("de-en") + file).toList

  def translations(file: String): Seq[Translation] = {
    val doc = readURL(file, in => io.Source.fromInputStream(new GZIPInputStream(in)).mkString)
    val Attr = ".*\"(.*)\".*\"(.*)\".*\"(.*)\".*".r
    val Targets = "<link.*\"([\\d ]+);([\\d ]+)\".*/>".r

    val Attr(target, deFile, enFile) = emptyTag("linkGrp").findFirstIn(doc).get
    val Seq(deSentences, enSentences) = Seq(deFile, enFile).map(file => sentences(s"$baseUrl/$file").toMap)

    Targets.findAllIn(doc).map { link =>
      val Targets(de, en) = link
      Translation(
        de.split(" ").map(_.toInt).map(i => deSentences(i)),
        en.split(" ").map(_.toInt).map(i => enSentences(i)))
    }.toSeq
  }

  def sentences(url: String): Seq[(Int, String)] = {
    val doc = readURL(url, in =>
      XML.loadString(io.Source.fromInputStream(new GZIPInputStream(in)).getLines.drop(1).mkString))
    doc \\ "s" map (s => s.attribute("id").get.map(_.text.toInt).head ->
      (s \\ "w").map(w => w.text).mkString("", " ", "").replaceAll(" ([\\.,!\\?'])", "$1"))
  }

  def saveCorpus(fileName: String = "europarl3.txt", directory: String = ".", numberOfTexts: Int = -1) = {
    val texts = if (numberOfTexts == -1) files.size else numberOfTexts
    val out = new PrintWriter(new FileWriter(s"$directory/$fileName"))

    try {
      out.println("# number of included episodes: " + numberOfTexts)
      out.println("# source: " + baseUrl + "/")
      val numTranslations = files.take(texts).map(translations).map { translations =>
        translations.foreach(out.println)
        out.println("# next episode \n")
        translations.size
      }.sum

      println(s"Saved corpus including $numberOfTexts episodes with $numTranslations translations.")
    } finally {
      out.close()
    }
  }
}