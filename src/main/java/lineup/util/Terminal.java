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
	public static final Color yellow = new Color(33);
	public static final Color blue = new Color(34);
	public static final Color magenta = new Color(35);
	public static final Color cyan = new Color(36);
	public static final Color white = new Color(97);
	public static final Color fgDefault = new Color(39);

	public static final Color bgDefault = new Color(49);
	public static final Color bgBlack = new Color(40);
	public static final Color bgRed = new Color(41);
	public static final Color bgGreen = new Color(42);
	public static final Color bgYellow = new Color(43);
	public static final Color bgBlue = new Color(44);
	public static final Color bgMagenta = new Color(45);
	public static final Color bgCyan = new Color(46);
	public static final Color bgWhite = new Color(107);
	public static final Color bgDarkGray = new Color(100);

	public static String painted(Color color, String msg) {
		return String.format("\033[0;%dm%s\033[0m", color.getValue(), msg);
	}

	public static String painted(Color foreground, Color background, String msg) {
		return String.format("\033[0;%d;%dm%s\033[0m", foreground.getValue(), background.getValue(), msg);
	}

	public static String startPaint(Color color) {
		return String.format("\033[0;%dm", color.getValue());
	}

	public static String startPaint(Color foreground, Color background) {
		return String.format("\033[0;%d;%dm", foreground.getValue(), background.getValue());
	}

	public static String stopPaint() {
		return "\033[0m";
	}
}
