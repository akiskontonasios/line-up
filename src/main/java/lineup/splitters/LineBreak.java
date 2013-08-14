package lineup.splitters;

public class LineBreak extends Token {

	public static final LineBreak instance = new LineBreak(42);

	private double confidence;

	public LineBreak(double confidence) {
		this.confidence = confidence;
	}

	public double getConfidence() {
		return confidence;
	}

	public String getValue() {
		return "\n";
	}

	@Override
	public boolean isLineBreak() {
		return true;
	}

	@Override
	public String toString() {
		return String.format("NL(%.2f)", getConfidence());
	}
}
