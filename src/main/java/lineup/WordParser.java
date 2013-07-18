package lineup;

import java.util.*;
import java.util.regex.*;

public class WordParser {
	private Pattern wordPattern = Pattern.compile("(\\p{L}+)|(\\d+)");

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