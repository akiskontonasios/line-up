package lineup;

import lineup.util.Fun;

import java.util.LinkedList;
import java.util.List;

/**
 * Set of unique n-grams for a given input sequence.
 *
 * @author Markus Kahl
 */
public class Shingling {

    public static final String punctuation = "[\\.,;\\?!'\"\\-]";

    private int w;
    private String input;
    private List<Shingles> shingles;

    public Shingling(int w, String input) {
        this.w = w;
        this.input = input;
    }

    public void addShingles(List<String> shingles, String word) {
        for (int i = 0; i <= word.length() - w; ++i) {
            shingles.add(word.substring(i, i + w));
        }
        if (word.length() < w) {
            shingles.add(word);
        }
    }

    public List<Shingles> getShingles(String input, String splitRegex) {
        List<Shingles> shingles = new LinkedList<Shingles>();

        if (splitRegex != null) {
            for (String token : input.split(splitRegex)) {
                if (!token.trim().isEmpty()) {
                    shingles.add(new Shingles(token));
                }
            }
        } else {
            shingles.add(new Shingles(input));
        }

        return shingles;
    }

    public List<Shingles> getShingles(String input) {
        return getShingles(input, "(" + Shingling.punctuation + "| )");
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
