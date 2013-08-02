package lineup;

import java.util.*;
import java.util.regex.*;

public class WordParser {
	private Pattern wordPattern = Pattern.compile("(\\p{L}[\\p{L}_-]*)|(\\d+)");

	public static final WordParser instance = new WordParser();

	public ArrayList<String> getWords(String sentence) {
        ArrayList<String> words = new ArrayList<String>(Math.min(sentence.length() / 5, 5));
        Matcher m = getWordPattern().matcher(sentence);

        while (m.find()) {
            String word = m.group();
            words.add(word);
        }

        return words;
    }

    public boolean findWord(String word, List<String> sentences) {
        for (String sentence : sentences) {
            Matcher m = containsWordPattern(word).matcher(sentence);

            if (m.find()) {
                return true;
            }
        }
        return false;
    }

	public boolean relatedWords(String a, String b) {
        return a.equalsIgnoreCase(b);
    }

    /**
     * Checks if the given two words are declensions of each other.
     * Based on German.
     */
    public boolean declension(String a, String b) {
        int lengthDelta = Math.abs(a.length() - b.length());

        if (lengthDelta > 3)
            return false;

        int i;
        for (i = 0; i < a.length() && i < b.length(); ++i) {
            char ca = a.charAt(i);
            char cb = b.charAt(i);

            if (ca == cb || declension(ca, cb)) {
                // ä's can turn into a's and stuff in German declensions
            } else {
                return false;
            }
        }

        if (lengthDelta > 0) {
            String ext = i >= a.length() ? b : a;
            char first = ext.charAt(i);

            switch (first) {
                case 'e': break;
                case 'n': break;
                case 's': break;
                default: return false;
            }
        }

        return true;
    }

    public boolean declension(char a, char b) {
        return  ((a == 'ä' && b == 'a') || (a == 'a' && b == 'ä')) ||
                ((a == 'ü' && b == 'u') || (a == 'u' && b == 'ü')) ||
                ((a == 'ö' && b == 'o') || (a == 'o' && b == 'ö'));
    }

    public Pattern containsWordPattern(String word) {
        return Pattern.compile("(?i)\\b" + word + "\\b");
    }

	public void setWordPattern(Pattern pattern) {
		this.wordPattern = pattern;
	}

	public Pattern getWordPattern() {
		return wordPattern;
	}
}