package lineup.splitters;

import java.util.*;
import lineup.*;
import lineup.util.*;

public class Word extends Token {

	private int index;
	private String value;
	private List<Match> matches;

	public Word(int index, String value, List<Match> matches) {
		this.index = index;
		this.value = value;
		this.matches = matches;
	}

	public Word(int index, String value) {
		this(index, value, new LinkedList<Match>());
	}

	/**
	 * Word's index within the list of words of its source sentence.
	 */
	public int getIndex() {
		return index;
	}

	public String getValue() {
		return value;
	}

	public List<Match> getMatches() {
		return matches;
	}

	public boolean isCapital() {
		return getValue().length() > 0 && Character.isUpperCase(getValue().charAt(0));
	}

	@Override
	public boolean isWord() {
		return true;
	}

	@Override
	public String toString() {
		return String.format("W(%d, %s)", getIndex(), getValue());
	}

	@Override
	public boolean equals(Object o) {
		return super.equals(o) && ((Word) o).getIndex() == getIndex();
	}

	@Override
	public int hashCode() {
		return super.hashCode() + 59 * getIndex();
	}

	public static class Match extends Tuple<Word, Float> {

		public Match(Word word, Float distance) {
			super(word, distance);
		}

		public Word getWord() {
			return this._1;
		}

		public float getDistance() {
			return this._2;
		}

		@Override
		public String toString() {
			return String.format("Match(%s, %.2f)", getWord().toString(), getDistance());
		}
	}
}
