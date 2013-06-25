package lineup;

/**
 * Thrown if the format of a corpus being read is invalid.
 *
 * @author Markus Kahl
 */
public class CorpusFormatException extends Exception {
    public CorpusFormatException(String msg) {
        super(msg);
    }
}
