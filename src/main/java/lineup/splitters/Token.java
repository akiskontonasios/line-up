package lineup.splitters;

public abstract class Token {
	/**
	 * Token represents a word within the sentence.
	 */
	public boolean isWord() {
		return false;
	}
	/**
	 * Token represents punctuation.
	 */
	public boolean isPunctuation() {
		return false;
	}
	/**
	 * Token represents a possible line break position.
	 */
	public boolean isLineBreak() {
		return false;
	}

	@Override
	public boolean equals(Object o) {
		return o == this || (o.getClass() == this.getClass() &&
			this.getValue().equals(((Token) o).getValue()));
	}

	@Override
	public int hashCode() {
		int result = -1;

		if (isWord()) {
			result += 11;
		} else if (isPunctuation()) {
			result += 17;
		} else if (isLineBreak()) {
			result = 0;
		}

		result *= getValue().hashCode();

		return result;
	}

	public abstract String getValue();
}