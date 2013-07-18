package lineup;

import lineup.util.Relation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static lineup.util.Fun.mkString;

public class Segment {
    private Translation input;

    public Segment(Translation input) {
        this.input = input;
    }

    public Set<Relation> findRelatedWords(int precision) {
        Shingling src = new Shingling(4, mkString(getInput().getSourceSentences(), ""));
        Shingling tgt = new Shingling(4, mkString(getInput().getTargetSentences(), ""));
        Set<Relation> results = new HashSet<Relation>();

        for (Shingling.Shingles ssh : src.getShingles()) {
            for (Shingling.Shingles tsh : tgt.getShingles()) {
                int matches = 0;
                for (String ngram : ssh) {
                    if (tsh.contains(ngram)) {
                        if (++matches >= precision || (ssh.size() == 1 && tsh.size() == 1)) {
                            results.add(new Relation(ssh.getWord(), tsh.getWord()));
                            break;
                        }
                    }
                }
            }
        }

        return results;
    }

    public Set<Relation> findRelatedWords() {
        return findRelatedWords(2);
    }

    public List<Segment> split() {
        return split(this);
    }

    protected List<Segment> split(Segment segment) {
        Set<Relation> relations = findRelatedWords();

        if (relations.size() > 0) {
            return split(this, relations.iterator().next());
        } else {
            System.out.printf("[info] failed to split due to missing relations: %s%n", this);
            return Arrays.asList(this);
        }
    }

    protected List<Segment> split(Segment segment, Relation splitAt) {
        Translation input = segment.getInput();
        List<String> sources = input.getSourceSentences();
        List<String> targets = input.getTargetSentences();
        List<Segment> result = new LinkedList<Segment>();

        if (sources.size() == 1 && targets.size() == 1) {
            List<String> src = splitSentence(sources.get(0), splitAt.getSource());
            List<String> tgt = splitSentence(targets.get(0), splitAt.getTarget());

            if (src.size() == tgt.size()) {
                for (int i = 0; i < src.size(); ++i) {
                    result.add(new Segment(input.copy(
                        Arrays.asList(src.get(i)), Arrays.asList(tgt.get(i)))));
                }
            } else {
                System.out.printf("[info] failed to split at %s: %s%n", splitAt, this);
            }
        } else {
            System.out.printf("[info] failed to split (only 1:1 sentences supported so far): %s%n", this);
        }

        if (result.isEmpty()) {
            result.add(this);
        }

        return result;
    }

    protected List<String> splitSentence(String sentence, String splitWord) {
        List<String> result = new LinkedList<String>();
        int index = sentence.indexOf(splitWord);

        // search left for split position
        for (int i = index; i >= 0; --i) {
            if (canSplitAt(i, sentence)) {
                result.add(sentence.substring(0, i + 1).trim());
                result.add(sentence.substring(i + 1).trim());
                break;
            }
        }

        // search right for split position
        for (int i = index + splitWord.length(); i < sentence.length() && result.isEmpty(); ++i) {
            if (canSplitAt(i, sentence)) {
                if (!result.isEmpty()) {
                    result.remove(1);
                }
                result.add(sentence.substring(0, i + 1).trim());
                result.add(sentence.substring(i + 1).trim());
                break;
            }
        }

        if (result.isEmpty()) {
            result.add(sentence);
        }

        return result;
    }

    protected boolean canSplitAt(int i, String sentence) {
        boolean result = sentence.charAt(i) == ',';

        if (i >= 3 && !result) {
            result |= sentence.substring(i - 1, i + 1).matches("( - )|(- )|( -)");
        }

        return result;
    }

    protected List<Segment> split2() {
        Set<Relation> relations = findRelatedWords();

        if (relations.isEmpty()) {
            System.out.println("[warning] Could not split due to no relations: " + getInput());
            return Arrays.asList(this);
        }

        Relation rel = relations.iterator().next();

        Pattern srcPattern = Pattern.compile("[^|,][^,]*" + rel.getSource() + "[^,.]*(,|.|$)");
        Pattern tgtPattern = Pattern.compile("(|,)[^,]*" + rel.getTarget() + "[^,.]*(,|.|$)");
        Matcher srcMatcher = srcPattern.matcher(getInput().getSourceSentences().get(0));
        Matcher tgtMatcher = tgtPattern.matcher(getInput().getTargetSentences().get(0));

        String srcSent = mkString(getInput().getSourceSentences(), " ");
        String tgtSent = mkString(getInput().getTargetSentences(), " ");
        List<String> srcSplit = splitString(srcSent, srcPattern);
        List<String> tgtSplit = splitString(tgtSent, tgtPattern);

        if (srcSplit.size() > 2 || tgtSplit.size() > 2) {
            System.out.println("[warning] Could not split due to too many parts: " + getInput());
            return Arrays.asList(this);
        }

        if (srcSplit.size() == 0 && tgtSplit.size() == 0) {
            System.out.printf("[warning] Could not split on %s: %s%n", rel, getInput());
            return Arrays.asList(this);
        }

        List<Segment> result = new LinkedList<Segment>();

        if (srcSent.indexOf(srcSplit.get(0)) == 0 && tgtSent.indexOf(tgtSplit.get(0)) == 0) { // related sentence start
            result.add(new Segment(new Translation(
                    getInput().getSourceLanguage(), getInput().getTargetLanguage(), srcSplit.get(0), tgtSplit.get(0))));
        }

        srcMatcher.find();
        tgtMatcher.find();
        result.add(new Segment(new Translation(
                getInput().getSourceLanguage(), getInput().getTargetLanguage(),
                srcMatcher.group(0), tgtMatcher.group(0))));

        if (isSentenceEnd(srcSplit.get(0)) && isSentenceEnd(tgtSplit.get(0))) { // related sentence start
            result.add(new Segment(new Translation(
                    getInput().getSourceLanguage(), getInput().getTargetLanguage(), srcSplit.get(0), tgtSplit.get(0))));
        }

        return null;
    }

    protected List<String> splitString(String str, Pattern pattern) {
        List<String> result = new LinkedList<String>();
        for (String token : pattern.split(str)) {
            if (!"".equals(token.trim())) {
                result.add(token);
            }
        }

        return result;
    }

    protected boolean isLastWord(String sentence, String word) {
        return sentence.matches(".*" + word + "\\.$");
    }

    protected boolean isFirstWord(String sentence, String word) {
        return sentence.matches("^" + word.substring(0, 1).toUpperCase() + word.substring(1) + ".*");
    }

    protected boolean isSentenceStart(String segment) {
        return Character.isUpperCase(segment.charAt(0));
    }

    protected boolean isSentenceEnd(String segment) {
        return segment.endsWith(".");
    }

    protected int findSplitPosition() {
        Set<Relation> relations = findRelatedWords();

        if (relations.isEmpty())
            return -1;



        return 0;
    }

    public Translation getInput() {
        return input;
    }

    @Override
    public String toString() {
        return String.format("Segment(%s)", getInput().toString());
    }
}
