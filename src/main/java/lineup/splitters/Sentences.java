package lineup.splitters;

import lineup.*;
import java.util.*;
import java.util.regex.Matcher;

import static lineup.util.Fun.*;

public class Sentences {
	
	private List<Token> tokens = new ArrayList<Token>();

	/**
	 * Creates a new Sentences object from the given string, splitting it into Punctuation and Words.
	 *
	 * @param possibleTranslations Expected to have one entry for each word.
	 * @param matchingSentences
	 */
	public Sentences(String value, WordParser wordParser,
			List<PossibleTranslations> possibleTranslations,
			Sentences matchingSentences) {

		Matcher m = wordParser.getWordPattern().matcher(value);
		Iterator<PossibleTranslations> pts = possibleTranslations != null ?
				possibleTranslations.iterator() : null;
		int i = 0;
		int wordIndex = 0;

		while (m.find()) {
			if (m.start() > i) { // prefix punctuation
				getTokens().add(new Punctuation(value.substring(i, m.start())));
			}

			getTokens().add(new Word(wordIndex++, m.group()));
			i = m.end();
		}

		if (i < value.length()) {
			getTokens().add(new Punctuation(value.substring(i)));
		}

		if (possibleTranslations != null && matchingSentences != null) {
			i = 0;
			for (Token t : getTokens()) {
				if (t.isWord()) {
					Word word = (Word) t;
					PossibleTranslations p = pts.next();
					List<Word.Match> matches = new LinkedList<Word.Match>();

					for (Candidate cand : p.getCandidates()) {
						int index = 0;
						for (Token token : matchingSentences.getTokens()) {
							if (token.getValue().equals(cand.getWord())) {
								float srcPos = i / (float) getTokens().size();
								float tgtPos = index / (float) matchingSentences.getTokens().size();
								matches.add(new Word.Match(cand, tgtPos - srcPos));
								break;
							}
							index++;
						}
					}

					word.getMatches().addAll(matches);
				}
				i++;
			}
		}
	}

	public Sentences(String value, WordParser wordParser) {
		this(value, wordParser, null, null);
	}

	public Sentences(String value) {
		this(value, WordParser.instance);
	}

	public Sentences(List<Token> tokens) {
		getTokens().addAll(tokens);
	}

	protected Sentences() {
	}

	/**
	 * Start and end inclusive.
	 */
	public Sentences subSentence(int start, int end) {
		if (start < 0) {
			start = getTokens().size() + start + 1;
		}
		if (end < 0) {
			end = getTokens().size() + end + 1;
		}
		return new Sentences(getTokens().subList(start, end));
	}

	public boolean isEmpty() {
		return getTokens().isEmpty();
	}

	/**
	 * Only consider matches within less than maxDistance % (of sentense length) distance.
	 * Everything farther away we cannot reasonably break.
	 */
	public void validateMatches(double maxDistance) {
		for (Token token : getTokens()) {
			if (token.isWord()) {
				Word word = (Word) token;
				Iterator<Word.Match> matches = word.getMatches().iterator();

				while (matches.hasNext()) {
					Word.Match match = matches.next();

					if (Math.abs(match.getDistance()) > maxDistance) {
						matches.remove();
					}
				}
			}
		}
	}

	public void validateMatches() {
		validateMatches(0.25);
	}

	public int indexOf(Word.Match match) {
		int i = 0;
		for (Token token : getTokens()) {
			if (token.isWord()) {
				Word word = (Word) token;

				if (word.getValue().equals(match.getCandidate().getWord())) {
					return i;
				}
			}
			++i;
		}
		
		return -1;
	}

	public int indexOf(Token token) {
		return getTokens().indexOf(token);
	}

	public Word findWord(String word) {
		for (Token token : getTokens()) {
			if (token instanceof Word && token.getValue().equals(word)) {
				return (Word) token;
			}
		}

		return null;
	}

	public Word wordAt(int i) {
		Token token = getTokens().get(i);

		if (token.isWord()) {
			return (Word) token;
		} else {
			return null;
		}
	}

	public Word lastWord() {
		for (int i = getTokens().size() - 1; i >= 0; --i) {
			Word word = wordAt(i);

			if (word != null) {
				return word;
			}
		}

		return null;
	}

	public Word firstWord() {
		for (int i = 0; i < getTokens().size(); ++i) {
			Word word = wordAt(i);

			if (word != null) {
				return word;
			}
		}

		return null;
	}

	public List<Token> getTokens() {
		return tokens;
	}

	public String getText() {
		StringBuilder sb = new StringBuilder();

		for (Token token : getTokens()) {
			sb.append(token.getValue());
		}

		return sb.toString();
	}

	public Token getStart() {
		return getTokens().size() > 0 ? getTokens().get(0) : null;
	}

	public Token getEnd() {
		return getTokens().size() > 0 ? getTokens().get(getTokens().size() - 1) : null;
	}

	public Sentences copy() {
		List<Token> tokens = new ArrayList<Token>(getTokens().size());
		tokens.addAll(getTokens());

		return new Sentences(tokens);
	}

	public Sentences join(Sentences sentences) {
		Sentences result = this.copy();
		result.getTokens().addAll(sentences.getTokens());

		return result;
	}

	public static Sentences join(List<Sentences> sentences, Token separator) {
		Sentences result = new Sentences();
		boolean first = true;

		for (Sentences sent : sentences) {
			if (first) {
				first = false;
			} else {
				result.getTokens().add(separator);
			}
			result.getTokens().addAll(sent.getTokens());
		}

		return result;
	}

	@Override
	public String toString() {
		return String.format("Sentences(%s)", mkString(tokens, " "));
	}
}