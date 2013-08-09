package lineup.splitters;

import lineup.util.Relation;
import lineup.PossibleTranslations;
import lineup.NtoNTranslation;
import lineup.WordParser;

import java.util.List;

import static lineup.util.Fun.*;

/**
 * Splits a Translation into (hopefully) corresponding parts.
 */
public abstract class Splitter {

	private WordParser wordParser;

	public Splitter(WordParser wordParser) {
		this.wordParser = wordParser;
	}

	public Splitter() {
		this(WordParser.instance);
	}

	public abstract List<Relation> split(Relation pair, List<PossibleTranslations> relations);

	public List<Relation> split(NtoNTranslation tr, List<PossibleTranslations> relations) {
		return split(
				new Relation(
					mkString(tr.getSourceSentences(), " "),
					mkString(tr.getTargetSentences(), " ")),
				relations);
	}

	public WordParser getWordParser() {
		return wordParser;
	}
}