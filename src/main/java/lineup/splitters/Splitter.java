package lineup.splitters;

import lineup.util.Relation;
import lineup.PossibleTranslations;
import lineup.NtoNTranslation;
import lineup.WordParser;

import java.util.List;

import lineup.util.*;

import static lineup.util.Fun.*;

/**
 * Splits a Translation into corresponding parts.
 */
public abstract class Splitter {

	private WordParser wordParser;

	public Splitter(WordParser wordParser) {
		this.wordParser = wordParser;
	}

	public Splitter() {
		this(WordParser.instance);
	}

	/**
	 * Inserts corresponding line breaks into a pair of Sentences instances which represent a single translation.
	 * Line breaks are to be inserted in such a way that related passages in the translation are not separated.
	 * The number of line breaks in both Sentences instances must always be the same.
	 *
	 * @param translation Translation represented as pair of Sentences instances which have to be wired.
	 *
	 * @see Sentences#wire(String, String, List<PossibleTranslations>, double, WordParser)
	 */
	public abstract Tuple<Sentences, Sentences> insertLineBreaks(Tuple<Sentences, Sentences> translation);

	/* The following operations are optional, i.e. not necessary but just there for convenience' sake. */

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
