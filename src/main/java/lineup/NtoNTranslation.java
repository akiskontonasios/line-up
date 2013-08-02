package lineup;

import java.util.LinkedList;
import java.util.List;

/**
 * An N to N translation maps one or more sentences in one language to one or more
 * sentences in another languages. Those sentences express the same idea.
 *
 * @author Markus Kahl
 */
public interface NtoNTranslation {
    String getSourceLanguage();
    String getTargetLanguage();

    List<String> getSourceSentences();
    List<String> getTargetSentences();
}
