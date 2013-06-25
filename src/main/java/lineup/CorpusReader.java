package lineup;

import java.io.Reader;
import java.util.List;

/**
 * Reads a sentence-aligned corpus.
 *
 * @author Markus Kahl
 */
public interface CorpusReader {
    List<Translation> readCorpus(Reader reader);
}
