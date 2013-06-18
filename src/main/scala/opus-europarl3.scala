package opus

import scalaj.http.Http
import sys.process._

object Europarl3 {
  val link = "<a[^/]*</a>".r
  val href = "\"([^\"]*)\"".r

  def url(lang: String) = "http://opus.lingfil.uu.se/Europarl3/xml/%s/" format lang 
  def markup(lang: String) = Http(url(lang)).asString
  def fetchFiles(lang: String) = link.findAllIn(markup(lang)).
    filter(_.contains(".xml.gz")).flatMap(link => href.findAllIn(link).map(_.replace("\"", "")))

  object files {
    lazy val de = fetchFiles("de").toList
    lazy val en = fetchFiles("en").toList

    lazy val de_en = de intersect en
  }

  def download() {
    files.de_en.take(3).foreach { file =>
      Seq("de", "en").foreach(lang =>
        "wget -q %s%s -O %s".format(url(lang), file, file.replace(".xml.gz", s".$lang.xml.gz")).!)
    }
  }
}