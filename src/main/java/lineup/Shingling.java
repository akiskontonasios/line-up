package lineup;

import lineup.util.Fun;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Set of unique n-grams for a given input sequence.
 *
 * @author Markus Kahl
 */
public class Shingling {

    private int w;
    private String input;
    private List<Shingles> shingles;
    private WordParser wordParser;

    public Shingling(int w, String input, WordParser wordParser) {
        this.w = w;
        this.input = input;
        this.wordParser = wordParser;
    }

    public void addShingles(List<String> shingles, String word) {
        for (int i = 0; i <= word.length() - w; ++i) {
            shingles.add(word.substring(i, i + w));
        }
        if (word.length() < w) {
            shingles.add(word);
        }
    }

    public List<Shingles> getShingles(String input) {
        List<Shingles> shingles = new LinkedList<Shingles>();
        Matcher m = getWordParser().getWordPattern().matcher(input);

        while (m.find()) {
            shingles.add(new Shingles(m.group()));
        }

        return shingles;
    }

    public List<Shingles> getShingles() {
        return getShingles(getInput());
    }

    @Override
    public String toString() {
        return String.format("%d-Shingling(%s)", w, Fun.cut(input, 30));
    }

    /**
     * Dimension of n-grams to use.
     *
     * @return
     */
    public int getW() {
        return w;
    }

    public String getInput() {
        return input;
    }

    public void setWordParser(WordParser wordParser) {
        this.wordParser = wordParser;
    }

    public WordParser getWordParser() {
        return wordParser;
    }

    public class Shingles extends LinkedList<String> {
        private String word;

        public Shingles(String word) {
            this.word = word;

            addShingles(this, word);
        }

        public boolean containsIgnoreCase(String token) {
            for (String s : this) {
                if (s.equalsIgnoreCase(token))
                    return true;
            }
            return false;
        }

        public String getWord() {
            return word;
        }

        @Override
        public String toString() {
            return String.format("%d-Shingles(%s => [%s])", getW(), getWord(), Fun.mkString(this, ", "));
        }
    }
}
