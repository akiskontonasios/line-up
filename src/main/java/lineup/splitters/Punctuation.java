package lineup.splitters;

public class Punctuation extends Token {

	private String value;

	public Punctuation(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	@Override
	public boolean isPunctuation() {
		return true;
	}

	@Override
	public String toString() {
		return String.format("P(%s)", getValue());
	}
}