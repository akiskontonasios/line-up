package lineup;

import java.util.List;

/**
 * An aligner provides a word alignment for a given translation.
 *
 * @author Markus Kahl
 */
public interface Aligner {
    /**
     * Associates each word of the translation's source sentences with a list of candidate words of the target sentences
     * which are likely related.
     *
     * @param translation Translation for which a word alignment should be calculated.
     * @return A list of PossibleTranslation instances, one for each word in the translation's source sentences.
     */
    List<PossibleTranslations> associate(NtoNTranslation translation);

    /**
     * The WordParser this aligner uses to match words (used in #associate).
     *
     * @return A WordParser
     */
    WordParser getWordParser();
}
