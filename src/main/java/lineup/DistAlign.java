package lineup;

import lineup.util.Relation;
import lineup.splitters.*;

import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static lineup.util.Fun.*;

/**
 * Produces word alignments based on their distribution.
 *
 * Idea: remove confident matches from sentences; try to infer probability of translations for the rest of the words.
 *       Also consolidate global translation possibilities?
 *
 * Sentences:
 *
 *  de: Das Parlament erhebt sich zu einer Schweigeminute.
 *  en: The House rose and observed a minute's silence.
 *
 * Relations:
 *
 *  Das             =>
 *  Parlament       => House
 *  erhebt          => rose, observed
 *  sich            =>
 *  zu              => The
 *  einer           =>
 *  Schweigeminute  => silence, minute
 *
 * Which leaves:
 *
 *  de: Das [...] sich [...] einer [...]
 *  en: [...] and [...] a [...]
 *
 * Where translationProbability("einer", "and") < translationProbability("einer", "a"),
 * hence we infer that:
 *
 *  einer           => a
 *
 * or sth?
 *
 * @author Markus Kahl
 */
public class DistAlign {

    private List<Translation> corpus;

    private Map<String, Integer> sourceWords;
    private Map<String, Integer> targetWords;
    private final int sourceWordCount;
    private final int targetWordCount;

    private List<String> sourceBlacklist = new LinkedList<String>();
    private List<String> targetBlacklist = new LinkedList<String>();

    private Map<String, List<PossibleTranslations>> cache = new HashMap<String, List<PossibleTranslations>>();
    private Map<String, List<Integer>> indexCache = new HashMap<String, List<Integer>>();

    private PrintStream out = new PrintStream(System.out);

    private WordParser wordParser = WordParser.instance;

    public DistAlign(List<Translation> corpus) {
        this.corpus = corpus;

        computeWordDistribution();

        sourceWordCount = sumValues(getSourceWords());
        targetWordCount = sumValues(getTargetWords());
    }

    private Random random = new Random();

    private List<PossibleTranslations> ptsCache;

    public void showRandom() {
        show(random.nextInt(corpus.size()));
    }

    public void show(int index) {
        printSentence(index);

        out.println("============ " + index + " ============");

        List<PossibleTranslations> pts = ptsCache = associate(index, 6);

        printbr(index, pts);
    }

    public void details() {
        if (ptsCache != null) {
            out.println("============ Relations ============");
            for (PossibleTranslations pt : ptsCache) {
                out.println(pt);
            }
        }
    }

    public void printbr(int index) {
        printbr(index, associate(index, 6));
    }

    public void printbr(int index, List<PossibleTranslations> pts) {
        for (Relation part : split(index, pts)) {
            String src = part.getSource().trim();
            String tgt = part.getTarget().trim();

            for (PossibleTranslations pt : pts) {
                if (pt.getCandidates().size() > 0) {
                    src = src.replaceAll("\\b(" + pt.getSourceWord() + ")\\b", "\033[0;32m$1\033[0m");

                    for (Candidate candidate : pt.getCandidates()) {
                        tgt = tgt.replaceAll("\\b(" + candidate.getWord() + ")\\b", "\033[0;34m$1\033[0m");
                    }
                }
            }

            out.println(src);
            out.println(tgt);
            out.println();
        }
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public PrintStream getOut() {
        return out;
    }

    public List<Relation> split(int index) {
        return split(index, associate(index, 6));
    }

    public List<Relation> split(int index, List<PossibleTranslations> pts) {
        Translation tr = getCorpus().get(index);

        return new BluntSplitter().split(tr, pts);
    }

    public void printSentence(int index) {
        Translation tr = getCorpus().get(index);
        out.println(mkString(tr.getSourceSentences(), " "));
        out.println(mkString(tr.getTargetSentences(), " "));
    }

    public void inspect(int index) {
        printSentence(index);
        out.println("----------------");
        List<PossibleTranslations> ts = possibleTranslations(getCorpus().get(index), 6);

        for (PossibleTranslations pt : ts) {
            List<Candidate> cs = pt.getCandidates();
            String w1, w2, w3, w4, w5, w6;
            double p1, p2, p3, p4, p5, p6;

            w1 = w2 = w3 = w4 = w5 = w6 = "-";
            p1 = p2 = p3 = p4 = p5 = p6 = 0;

            if (cs.size() >= 1) {
                w1 = cs.get(0).getWord();
                p1 = cs.get(0).getProbability();
            }
            if (cs.size() >= 2) {
                w2 = cs.get(1).getWord();
                p2 = cs.get(1).getProbability();
            }
            if (cs.size() >= 3) {
                w3 = cs.get(2).getWord();
                p3 = cs.get(2).getProbability();
            }
            if (cs.size() >= 4) {
                w4 = cs.get(3).getWord();
                p4 = cs.get(3).getProbability();
            }
            if (cs.size() >= 5) {
                w5 = cs.get(4).getWord();
                p5 = cs.get(4).getProbability();
            }
            if (cs.size() >= 6) {
                w6 = cs.get(5).getWord();
                p6 = cs.get(5).getProbability();
            }

            double freq = getSourceWords().get(pt.getSourceWord()) / (double) getSourceWordCount();

            out.printf(
                    "%30s | # %5f | => %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f%n",
                    pt.getSourceWord(), freq, w1, p1, w2, p2, w3, p3, w4, p4, w5, p5, w6, p6);

            // REFINE DAT
            // "\033[0;%dm%s\033[0m"

            boolean refined = refine(pt, 5, index);

            if (cs.size() >= 1) {
                w1 = cs.get(0).getWord();
                p1 = cs.get(0).getProbability();
            }
            if (cs.size() >= 2) {
                w2 = cs.get(1).getWord();
                p2 = cs.get(1).getProbability();
            }
            if (cs.size() >= 3) {
                w3 = cs.get(2).getWord();
                p3 = cs.get(2).getProbability();
            }
            if (cs.size() >= 4) {
                w4 = cs.get(3).getWord();
                p4 = cs.get(3).getProbability();
            }
            if (cs.size() >= 5) {
                w5 = cs.get(4).getWord();
                p5 = cs.get(4).getProbability();
            }
            if (cs.size() >= 6) {
                w6 = cs.get(5).getWord();
                p6 = cs.get(5).getProbability();
            }

            if (refined) {
                out.print("\033[0;35m");
            }
            out.printf(
                    "%30s | # %8d | => %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f%n",
                    "", cache.get(pt.getSourceWord()).size(), w1, p1, w2, p2, w3, p3, w4, p4, w5, p5, w6, p6);
            if (refined) {
                out.print("\033[0m");
            }
        }
    }

    public void versus(int index) {
        printSentence(index);
        out.println("----------------");
        List<PossibleTranslations> ts = possibleTranslations(getCorpus().get(index), 6);

        for (PossibleTranslations pt : ts) {
            List<Candidate> cs = pt.getCandidates();
            List<Object> wp = new LinkedList<Object>();

            for (Candidate c : cs) {
                wp.add(c.getWord());
                wp.add(c.getProbability());
            }

            while (wp.size() < 6) {
                wp.add("-");
                wp.add(0d);
            }

            double freq = getSourceWords().get(pt.getSourceWord()) / (double) getSourceWordCount();

            wp.add(0, freq);
            wp.add(0, pt.getSourceWord());

            out.printf(
                    "%30s | # %5f | => %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f | %13s %.8f%n",
                    wp.toArray(new Object[] { wp.size() }));
        }
    }

    public List<PossibleTranslations> associate(int index) {
        return associate(index, 4);
    }

    public List<PossibleTranslations> associateRetainingAll(int index) {
        return associate(index, 4, 3, false);
    }

    public List<PossibleTranslations> associate(int index, int limit) {
        return associate(index, limit, 3, true);
    }

    public List<PossibleTranslations> associate(int index, int limit, int prune, boolean retainMostLikely) {
        List<PossibleTranslations> matches = matches(index, limit);
        Set<Relation> relations = findRelatedWords(
                getCorpus().get(index).getSourceSentences(), getCorpus().get(index).getTargetSentences());
        for (PossibleTranslations pt : matches) {
            for (Relation rel : relations) {
                if (rel.getSource().equals(pt.getSourceWord())) {
                    boolean newWord = true;
                    for (Candidate c : pt.getCandidates()) {
                        if (getWordParser().relatedWords(c.getWord(), rel.getTarget())) {
                            newWord = false;
                            break;
                        }
                    }
                    if (newWord) {
                        pt.getCandidates().add(new Candidate(
                                rel.getTarget(),
                                sourceProbability(rel.getSource()) * targetProbability(rel.getTarget())));
                    }
                }
            }

            if (!retainMostLikely) {
                if (prune < limit && prune != -1) {
                    pt.prune(prune);
                }
            }
        }

        if (retainMostLikely) {
            pruneMatches(matches, false);
            if (prune < limit && prune != -1) {
                for (PossibleTranslations pt : matches) {
                    pt.prune(prune);
                }
            }
        }

        return matches;
    }

    public List<PossibleTranslations> associate2(int index, int limit) {
        List<PossibleTranslations> matches = matches2(index, limit);
        Set<Relation> relations = findRelatedWords(
                getCorpus().get(index).getSourceSentences(), getCorpus().get(index).getTargetSentences());
        for (PossibleTranslations pt : matches) {
            for (Relation rel : relations) {
                if (rel.getSource().equals(pt.getSourceWord())) {
                    pt.getCandidates().add(new Candidate(
                            rel.getTarget(),
                            sourceProbability(rel.getSource()) * targetProbability(rel.getTarget())));
                }
            }
            pt.sort();
        }

        return matches;
    }

    public List<PossibleTranslations> matches2(int index, int limit) {
        List<PossibleTranslations> result = possibleTranslations(
                getCorpus().get(index).getSourceSentences(), getCorpus().get(index).getTargetSentences(), limit, false);

        for (PossibleTranslations pt : result) {
            for (Candidate cand : pt.getCandidates()) {
                cand.setProbability(cand.getProbability() * reverseTranslationProbability(cand.getWord(), pt.getSourceWord()));
            }
            Iterator<Candidate> candidates = pt.getCandidates().iterator();
            while (candidates.hasNext()) {
                Candidate cand = candidates.next();
                if (cand.getProbability() == 0) {
                    candidates.remove();
                }
            }
            pt.sort();
        }

        return result;
    }

    public List<PossibleTranslations> matches(int index) {
        return matches(index, 3);
    }

    public List<PossibleTranslations> matches(int index, int limit) {
        List<PossibleTranslations> result = new LinkedList<PossibleTranslations>();
        List<PossibleTranslations> forth = possibleTranslations(getCorpus().get(index), limit != -1 ? limit : 3);
        List<PossibleTranslations> back = reversePossibleTranslations(getCorpus().get(index), limit != -1 ? limit : 3);

        for (PossibleTranslations ptForth : forth) {
            List<Candidate> candidates = new LinkedList<Candidate>();
            for (Candidate cForth : ptForth.getCandidates()) {
                cross: for (PossibleTranslations ptBack : back) {
                    if (ptBack.getSourceWord().equals(cForth.getWord())) {
                        for (Candidate cBack : ptBack.getCandidates()) {
                            if (ptForth.getSourceWord().equals(cBack.getWord())) {
                                candidates.add(new Candidate(ptBack.getSourceWord(), cForth.getProbability() * cBack.getProbability()));
                                break cross;
                            }
                        }
                        break;
                    }
                }
            }
            PossibleTranslations matches = new PossibleTranslations(ptForth.getSourceWord(), candidates);

            matches.sort();
            result.add(matches);
        }

        return result;
    }

    public Set<Relation> findRelatedWords(List<String> sources, List<String> targets) {
        return findRelatedWords(sources, targets, 4, 2);
    }

    public Set<Relation> findRelatedWords(List<String> sources, List<String> targets, int n, int precision) {
        if (n == -1)
            n = 3;
        if (precision == -1)
            precision = 2;

        Shingling src = new Shingling(n, mkString(sources, ""));
        Shingling tgt = new Shingling(n, mkString(targets, ""));
        Set<Relation> results = new HashSet<Relation>();

        for (Shingling.Shingles ssh : src.getShingles()) {
            for (Shingling.Shingles tsh : tgt.getShingles()) {
                int matches = 0;
                for (String ngram : ssh) {
                    if (tsh.containsIgnoreCase(ngram)) {
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

    public List<PossibleTranslations> pruneMatches(List<PossibleTranslations> matches) {
        return pruneMatches(matches, true);
    }

    public List<PossibleTranslations> pruneMatches(List<PossibleTranslations> matches, boolean pure) {
        List<PossibleTranslations> pts = pure ? new LinkedList<PossibleTranslations>() : matches;

        if (pure) {
            for (PossibleTranslations match : matches) {
                pts.add(match.copy());
            }
        }

        Set<String> words = new HashSet<String>();

        for (PossibleTranslations pt : pts) {
            for (Candidate candidate : pt.getCandidates()) {
                // Now for every term we encounter delete all but the most likely one!
                words.add(candidate.getWord());
            }
        }

        for (String word : words) {
            retainMostLikely(word, pts, false);
        }

        return pts;
    }

    protected List<PossibleTranslations> retainMostLikely(String word, List<PossibleTranslations> pts, boolean pure) {
        List<PossibleTranslations> result = pure ? new LinkedList<PossibleTranslations>() : pts;
        List<PossibleTranslations> remove = new LinkedList<PossibleTranslations>();
        PossibleTranslations maxPt = null;
        double maxProb = 0;

        if (pure) {
            for (PossibleTranslations pt : pts) {
                result.add(pt.copy());
            }
        }

        for (PossibleTranslations pt : result) {
            for (Candidate candidate : pt.getCandidates()) {
                if (candidate.getWord().equals(word)) {
                    if (candidate.getProbability() > maxProb) {
                        if (maxPt != null) {
                            remove.add(maxPt);
                        }
                        maxProb = candidate.getProbability();
                        maxPt = pt;
                    } else {
                        remove.add(pt);
                    }
                    break;
                }
            }
        }

        for (PossibleTranslations pt : remove) {
            Iterator<Candidate> cands = pt.getCandidates().iterator();
            while (cands.hasNext()) {
                Candidate cand = cands.next();
                if (cand.getWord().equals(word)) {
                    cands.remove();
                    break;
                }
            }
        }

        return result;
    }

    public boolean refine(PossibleTranslations pt, int limit, int corpusSkipIndex) {
        List<PossibleTranslations> related = getCache(pt.getSourceWord(), limit, corpusSkipIndex);
        boolean refined = false;

        for (int i = 0; i < limit && i < pt.getCandidates().size(); ++i) {
            Candidate cand = pt.getCandidates().get(i);

            for (PossibleTranslations tr : related) {
                boolean contains = false;
                for (Candidate c : tr.getCandidates()) {
                    if (getWordParser().relatedWords(c.getWord(), cand.getWord())) {
                        contains = true;
                        break;
                    }
                }
                if (contains) {
                    if (cand.getProbability() >= 0.9) {
                        cand.setProbability(cand.getProbability() + 0.01);
                    } else {
                        double boosted = cand.getProbability() * (10 * (i == 0 ? 10 : 1));
                        if (boosted >= 0.99) {
                            boosted = 0.9;
                        }
                        cand.setProbability(boosted);
                    }
                    refined = true;
                }
            }
        }

        pt.sort();

        return refined;
    }

    public PossibleTranslations refineCopy(PossibleTranslations pt, int limit, int corpusSkipIndex) {
        PossibleTranslations copy = pt.copy();
        refine(copy, limit, corpusSkipIndex);

        return copy;
    }

    public List<PossibleTranslations> getCache(String sourceWord, int limit, int corpusSkipIndex) {
        List<PossibleTranslations> related;
        List<Integer> indices;
        if (cache.containsKey(sourceWord)) {
            related = cache.get(sourceWord);
            indices = indexCache.get(sourceWord);
        } else {
            related = new LinkedList<PossibleTranslations>();
            indices = new LinkedList<Integer>();
            cache.put(sourceWord, related);
            indexCache.put(sourceWord, indices);
        }

        fill: while (related.size() == 0 || (related.size() < 5 && getSourceWordCount() > 6)) {
            int i = -1;
            for (Translation tr : getCorpus()) {
                ++i;
                if (i != corpusSkipIndex && !indices.contains(i) &&
                        getWordParser().findWord(sourceWord, tr.getSourceSentences())) {
                    PossibleTranslations pt = possibleTranslations(sourceWord, tr.getTargetSentences(), limit, false);

                    related.add(pt);
                    indices.add(i);
                    continue fill;
                }
            }
            break fill; // no new matches found
        }

        return related;
    }

    public List<PossibleTranslations> possibleTranslations(Translation translation, int limit) {
        return possibleTranslations(translation.getSourceSentences(), translation.getTargetSentences(), limit, false);
    }

    public List<PossibleTranslations> reversePossibleTranslations(Translation translation, int limit) {
        return possibleTranslations(translation.getTargetSentences(), translation.getSourceSentences(), limit, true);
    }

    public List<PossibleTranslations> possibleTranslations(
            List<String> sourceSentences, List<String> targetSentences, int limit, boolean reverse) {

        List<PossibleTranslations> translations = new LinkedList<PossibleTranslations>();

        for (String source : sourceSentences) {
            Matcher m = getWordParser().getWordPattern().matcher(source);

            while (m.find()) {
                String word = m.group();
                translations.add(possibleTranslations(word, targetSentences, limit, reverse));
            }
        }

        return translations;
    }

    public PossibleTranslations possibleTranslations(String sourceWord, List<String> targetSentences, int limit, boolean reverse) {
        List<Candidate> candidates = new LinkedList<Candidate>();

        for (String sentence : targetSentences) {
            Matcher m = getWordParser().getWordPattern().matcher(sentence);

            while (m.find()) {
                String word = m.group();
                candidates.add(new Candidate(word,
                        !reverse ? translationProbability(sourceWord, word) : reverseTranslationProbability(sourceWord, word)));
            }
        }

        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate c1, Candidate c2) {
                return new Double(c2.getProbability()).compareTo(new Double(c1.getProbability()));
            }
        });

        if (candidates.size() > limit && limit != -1) {
            candidates = candidates.subList(0, limit);
        }

        return new PossibleTranslations(sourceWord, candidates);
    }

    public double translationProbability(String sourceWord, String targetWord) {
        return (sourceProbability(sourceWord) * relationProbability(targetWord, sourceWord)) /
                targetProbability(targetWord);
    }

    public double reverseTranslationProbability(String targetWord, String sourceWord) {
        return (targetProbability(targetWord) * relationProbability(sourceWord, targetWord, false)) /
                sourceProbability(sourceWord);
    }

    public double sourceProbability(String word) {
        return wordProbability(word, getSourceWords(), getSourceWordCount());
    }

    public double targetProbability(String word) {
        return wordProbability(word, getTargetWords(), getTargetWordCount());
    }

    public double wordProbability(String word, Map<String, Integer> dist, int distSize) {
        int n = 0;

        if (dist.containsKey(word)) {
            n = dist.get(word);
        }

        return n / (double) distSize;
    }

    public double relationProbability(String target, String source) {
        return relationProbability(target, source, true);
    }

    public double relationProbability(String word1, String word2, boolean targetGivenSource) {
        int occurrences = 0;

        Map<String, List<Translation>> cache = targetGivenSource ?
                targetGivenSourceCache : sourceGivenTargetCache;
        List<Translation> matches = cache.get(word2);

        if (matches == null) {
            matches = new LinkedList<Translation>();
            for (Translation tr : getCorpus()) {
                if (getWordParser().findWord(word2, targetGivenSource ? tr.getSourceSentences() : tr.getTargetSentences()))
                    matches.add(tr);
            }
            cache.put(word2, matches);
        }

        for (Translation tr : matches) {
            if (getWordParser().findWord(word1, targetGivenSource ? tr.getTargetSentences() : tr.getSourceSentences()))
                ++occurrences;
        }

        return (occurrences / (double) matches.size()) / getCorpus().size();
    }

    private Map<String, List<Translation>> targetGivenSourceCache = new HashMap<String, List<Translation>>();
    private Map<String, List<Translation>> sourceGivenTargetCache = new HashMap<String, List<Translation>>();

    protected void computeWordDistribution() {
        sourceWords = new HashMap<String, Integer>();
        targetWords = new HashMap<String, Integer>();

        for (Translation tr : getCorpus()) {
            addToDistribution(tr.getSourceSentences(), getSourceWords());
            addToDistribution(tr.getTargetSentences(), getTargetWords());
        }
    }

    protected void addToDistribution(List<String> sentences, Map<String, Integer> dist) {
        for (String sentence : sentences) {
            Matcher m = getWordParser().getWordPattern().matcher(sentence);

            while (m.find()) {
                String word = m.group();

                if (dist.containsKey(word)) {
                    dist.put(word, dist.get(word) + 1);
                } else {
                    dist.put(word, 1);
                }
            }
        }
    }

    protected int sumValues(Map<?, Integer> map) {
        int sum = 0;
        for (Integer i : map.values()) {
            sum += i;
        }

        return sum;
    }

    public List<Translation> getCorpus() {
        return corpus;
    }

    public Map<String, Integer> getSourceWords() {
        return sourceWords;
    }

    public Map<String, Integer> getTargetWords() {
        return targetWords;
    }

    public List<String> getSourceBlacklist() {
        return sourceBlacklist;
    }

    public List<String> getTargetBlacklist() {
        return targetBlacklist;
    }

    public int getSourceWordCount() {
        return sourceWordCount;
    }

    public int getTargetWordCount() {
        return targetWordCount;
    }

    public void setWordParser(WordParser wordParser) {
        this.wordParser = wordParser;
    }

    public WordParser getWordParser() {
        return wordParser;
    }
}
