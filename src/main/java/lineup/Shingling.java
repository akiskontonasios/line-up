package lineup;

import lineup.util.Fun;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * List of unique n-grams for every word in a associated input sequence.
 *
 * @author Markus Kahl
 */
public class Shingling {

    private int w;
    private String input;
    private List<Shingles> shingles;
    private WordParser wordParser;

    /**
     * Creates a new Shingling.
     *
     * @param w N-gram dimension (i.e. N)
     * @param input Text
     * @param wordParser WordParser used to extract words from the input sequence.
     */
    public Shingling(int w, String input, WordParser wordParser) {
        this.w = w;
        this.input = input;
        this.wordParser = wordParser;
    }

    /**
     * Adds to a list all n-grams (size w of this Shingling) of a given word.
     *
     * @param shingles Shingles to add to.
     * @param word Word whose n-grams to add.
     */
    public void addShingles(List<String> shingles, String word) {
        for (int i = 0; i <= word.length() - w; ++i) {
            shingles.add(word.substring(i, i + w));
        }
        if (word.length() < w) {
            shingles.add(word);
        }
    }

    /**
     * Computes shingles for a given input.
     *
     * @param input Sentence to shingles for.
     * @return A list of shingles, one for each word in the input and in the order the words occur within which.
     */
    public List<Shingles> getShingles(String input) {
        List<Shingles> shingles = new LinkedList<Shingles>();
        Matcher m = getWordParser().getWordPattern().matcher(input);

        while (m.find()) {
            shingles.add(new Shingles(m.group()));
        }

        return shingles;
    }

    /**
     * Computes shingles for this Shingling.
     *
     * @see #getShingles(String)
     */
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

    /**
     * List of n-grams of a word.
     */
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

        /**
         * This shingles' resemblance with another one.
         * See Jaccard coefficient.
         */
        public double resemblance(Shingles shingles) {
            return intersection(shingles).size() / (double) union(shingles).size();
        }

        protected Shingles intersection(Shingles shingles) {
            Shingles ret = new Shingles(getWord());
            ret.retainAll(shingles);

            return ret;
        }

        protected Shingles union(Shingles shingles) {
            Shingles ret = new Shingles(getWord());

            ret.addAll(Shingles.this);
            for (String str : shingles) {
                if (!ret.contains(str)) {
                    ret.add(str);
                }
            }

            return ret;
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
