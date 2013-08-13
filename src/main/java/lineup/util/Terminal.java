package lineup.util;

public class Terminal {
	public static abstract class Type {
		int value;

		public Type(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	public static class Color extends Type {

		public Color(int value) {
			super(value);
		}
	}

	public static final Color black = new Color(30);
	public static final Color red = new Color(31);
	public static final Color green = new Color(32);
	public static final Color brown = new Color(33);
	public static final Color blue = new Color(34);
	public static final Color magenta = new Color(35);
	public static final Color cyan = new Color(36);
	public static final Color white = new Color(37);

	public static String painted(Color color, String msg) {
		return String.format("\033[0;%dm%s\033[0m", color.getValue(), msg);
	}
}
