package lineup;

import java.util.LinkedList;
import java.util.List;

/**
 * A translation maps one or more sentences in one language to one or more
 * sentences in another languages. Those sentences express the same idea.
 *
 * @author Markus Kahl
 */
public class Translation {

    private String sourceLanguage;
    private String targetLanguage;

    private List<String> sourceSentences = new LinkedList<String>();
    private List<String> targetSentences = new LinkedList<String>();

    public Translation(String sourceLanguage, String targetLanguage) {
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
    }

    public Translation(String sourceLanguage, String targetLanguage, String sourceSentence, String targetSentence) {
        this(sourceLanguage, targetLanguage);

        getSourceSentences().add(sourceSentence);
        getTargetSentences().add(targetSentence);
    }

    public Translation copy(List<String> sources, List<String> targets) {
        Translation copy = new Translation(getSourceLanguage(), getTargetLanguage());

        copy.getSourceSentences().addAll(sources);
        copy.getTargetSentences().addAll(targets);

        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Translation(");
        String src = getSourceSentences().get(0);
        String tgt = getTargetSentences().get(0);
        final int maxLength = 20;

        sb.append(getSourceLanguage());
        sb.append(": ");
        if (src.length() > maxLength) {
            sb.append(src.substring(0, maxLength - 1));
            sb.append("...");
        } else {
            sb.append(src);
            if (getSourceSentences().size() > 1 && src.length() <= maxLength) {
                sb.append(" ...");
            }
        }

        sb.append(" | ");
        sb.append(getTargetLanguage());
        sb.append(": ");
        if (tgt.length() > maxLength) {
            sb.append(tgt.substring(0, maxLength - 1));
            sb.append("...");
        } else {
            sb.append(tgt);
            if (getTargetSentences().size() > 1 && tgt.length() <= maxLength) {
                sb.append(" ...");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public List<String> getSourceSentences() {
        return sourceSentences;
    }

    public List<String> getTargetSentences() {
        return targetSentences;
    }
}
