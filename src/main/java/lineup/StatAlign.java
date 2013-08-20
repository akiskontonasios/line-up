package lineup;

import lineup.splitters.Sentences;
import lineup.util.Relation;
import lineup.util.Tuple;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;

import static lineup.util.Fun.mkString;
import static lineup.util.Fun.take;

/**
 * Produces word alignments based on a statistical model which works using a sentence-aligned corpus.
 *
 * @author Markus Kahl
 */
public class StatAlign<T extends NtoNTranslation> implements Aligner {

    private List<T> corpus;

    private Map<String, Integer> sourceWords;
    private Map<String, Integer> targetWords;
    private final int sourceWordCount;
    private final int targetWordCount;

    private List<String> sourceBlacklist = new LinkedList<String>();
    private List<String> targetBlacklist = new LinkedList<String>();

    private Map<String, List<NtoNTranslation>> targetGivenSourceCache = new ConcurrentHashMap<String, List<NtoNTranslation>>();
    private Map<String, List<NtoNTranslation>> sourceGivenTargetCache = new ConcurrentHashMap<String, List<NtoNTranslation>>();
    private Map<String, Set<String>> srcDeclCache = new HashMap<String, Set<String>>();
    private Map<String, Set<String>> tgtDeclCache = new HashMap<String, Set<String>>();

    private WordParser wordParser;

    ExecutorService exec = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);

                return thread;
            }
        });

    public StatAlign(List<T> corpus, WordParser wordParser) {
        this.corpus = corpus;
        this.wordParser = wordParser;

        computeWordDistribution();

        sourceWordCount = sumValues(getSourceWords());
        targetWordCount = sumValues(getTargetWords());

        initBlacklists();
    }

    public StatAlign(List<T> corpus) {
        this(corpus, WordParser.instance);
    }

    protected void initBlacklists() {
        if (corpus == null || corpus.isEmpty())
            throw new IllegalArgumentException("StatAlign requires a non-empty corpus.");

        String tgtLang = corpus.get(0).getTargetLanguage();

        if ("en".equals(tgtLang)) {
            getTargetBlacklist().add("s"); // s is only a particle indicating genitive
        }
    }

    public Tuple<Sentences, Sentences> getSentences(int startIndex, int length) {
        return getSentences(startIndex, length, 9);
    }

    public Tuple<Sentences, Sentences> getSentences(int startIndex, int length, double maxTranslationDistance) {
        List<PossibleTranslations> pts = associate(startIndex, 6);

        for (int i = 1; i < length; ++i) {
            pts.addAll(associate(startIndex + i, 6));
            try {
                Thread.sleep(250); // concurrency bug (during evaluation) workaround
            } catch (InterruptedException e) {
                System.out.println("[warning] " + e.getMessage());
            }
        }

        StringBuilder de = new StringBuilder();
        StringBuilder en = new StringBuilder();

        for (int i = 0; i < length; ++i) {
            T tr = getCorpus().get(startIndex + i);

            if (i > 0) {
                de.append(" ");
                en.append(" ");
            }
            de.append(mkString(tr.getSourceSentences(), " "));
            en.append(mkString(tr.getTargetSentences(), " "));
        }

        return Sentences.wire(de.toString(), en.toString(), pts, maxTranslationDistance, getWordParser());
    }

    public List<PossibleTranslations> associate(int index) {
        return associate(index, 6);
    }

    public List<PossibleTranslations> associate(NtoNTranslation translation) {
        return associate(translation, 6, 3, true);
    }

    public List<PossibleTranslations> associateRetainingAll(int index) {
        return associate(index, 6, 3, false);
    }

    public List<PossibleTranslations> associate(int index, int limit) {
        return associate(index, limit, 3, true);
    }

    public List<PossibleTranslations> associate(int index, int limit, int prune, boolean retainMostLikely) {
        return associate(getCorpus().get(index), limit, prune, retainMostLikely);
    }

    public List<PossibleTranslations> associate(NtoNTranslation translation, int limit, int prune, boolean retainMostLikely) {
        List<PossibleTranslations> matches = matches(translation, limit);
        Set<Relation> relations = findRelatedWords(translation.getSourceSentences(), translation.getTargetSentences());
        Map<String, Integer> targetWordCounts = new HashMap<String, Integer>();

        addToDistribution(translation.getTargetSentences(), targetWordCounts);

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
                    if (newWord || true) {
                        Candidate cand = new Candidate(
                                rel.getTarget(),
                                sourceProbability(rel.getSource()) * targetProbability(rel.getTarget()));

                        if (rel.getSource().equals(rel.getTarget())) {
                            cand.setProbability(0.99);
                        }
                        pt.getCandidates().add(cand);
                    }
                }
            }

            pt.sort();

            if (!retainMostLikely) {
                if (prune < limit && prune != -1) {
                    pt.prune(prune);
                }
            }
        }

        if (retainMostLikely) {
            pruneMatches(matches, targetWordCounts, false);
            if (prune < limit && prune != -1) {
                for (PossibleTranslations pt : matches) {
                    pt.prune(prune);
                }
            }
        }

        return matches;
    }

    public List<PossibleTranslations> matches(int index) {
        return matches(index, 3);
    }

    public List<PossibleTranslations> matches(int index, int limit) {
        return matches(getCorpus().get(index), limit);
    }

    public List<PossibleTranslations> matches(NtoNTranslation translation, int limit) {
        List<PossibleTranslations> result = new LinkedList<PossibleTranslations>();
        List<PossibleTranslations> forth = possibleTranslations(translation, limit != -1 ? limit : 3);
        List<PossibleTranslations> back = reversePossibleTranslations(translation, limit != -1 ? limit : 3);

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

    public Set<String> sourceDeclensions(String word, boolean includeSource) {
        if (includeSource && srcDeclCache.containsKey(word)) {
            return srcDeclCache.get(word);
        } else {
            Set<String> decls = declensions(word, getSourceWords().keySet(), includeSource);
            if (!includeSource) {
                srcDeclCache.put(word, decls);
            }
            return decls;
        }
    }

    public Set<String> targetDeclensions(String word, boolean includeSource) {
        if (includeSource && tgtDeclCache.containsKey(word)) {
            return tgtDeclCache.get(word);
        } else {
            Set<String> decls = declensions(word, getTargetWords().keySet(), includeSource);
            if (includeSource) {
                tgtDeclCache.put(word, decls);
            }
            return decls;
        }
    }

    public Set<String> declensions(String word, Set<String> words, boolean includeSource) {
        Set<String> result = new HashSet<String>();

        if (word.length() > 3) {
            for (String cand : words) {
                if (cand.length() <= 3)
                    continue;
                if (getWordParser().declension(word, cand)) {
                    result.add(cand);
                }
            }
        }

        if (!includeSource) {
            result.remove(word);
        } else if (result.isEmpty()) {
            result.add(word);
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

        Shingling src = new Shingling(n, mkString(sources, ""), getWordParser());
        Shingling tgt = new Shingling(n, mkString(targets, ""), getWordParser());
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

    public List<PossibleTranslations> pruneMatches(
            List<PossibleTranslations> matches, Map<String, Integer> targetWordCounts) {
        return pruneMatches(matches, targetWordCounts, true);
    }

    public List<PossibleTranslations> pruneMatches(
            List<PossibleTranslations> matches,
            Map<String, Integer> targetWordCounts,
            boolean pure) {

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
            retainMostLikely(word, pts, targetWordCounts, false);
        }

        return pts;
    }

    protected List<PossibleTranslations> retainMostLikely(
            String word,
            List<PossibleTranslations> pts,
            Map<String, Integer> targetWordCounts,
            boolean pure) {

        List<PossibleTranslations> result = pure ? new LinkedList<PossibleTranslations>() : pts;
        PossibleTranslations maxPt = null;
        double minProb = 0;
        int maxCount = 0;
        int wordCount = 0;
        List<Double> probabilities = new LinkedList<Double>();

        if (targetWordCounts.containsKey(word)) {
            wordCount = targetWordCounts.get(word);
        } else {
            System.err.println("\n[warning] no word count for '" + word + "'");
        }

        if (pure) {
            for (PossibleTranslations pt : pts) {
                result.add(pt.copy());
            }
        }

        for (PossibleTranslations pt : result) {
            for (Candidate candidate : pt.getCandidates()) {
                if (candidate.getWord().equals(word)) {
                    probabilities.add(candidate.getProbability());
                }
            }
        }

        Collections.sort(probabilities); // sorted ascending
        Collections.reverse(probabilities); // sort descending (highest probabilties first)
        probabilities = take(wordCount, probabilities);
        minProb = 1000;

        if (probabilities.size() > 0) {
            minProb = probabilities.get(probabilities.size() - 1);
        }

        boolean minNaN = Double.isNaN(minProb);

        for (PossibleTranslations pt : pts) {
            Iterator<Candidate> cands = pt.getCandidates().iterator();
            while (cands.hasNext()) {
                Candidate cand = cands.next();
                if (cand.getWord().equals(word)) {
                    if (cand.getProbability() < minProb || (minNaN && !Double.isNaN(cand.getProbability()))) {
                        cands.remove();
                    } else if (cand.getProbability() >= minProb && maxCount++ >= wordCount) {
                        cands.remove();
                    }
                }
            }
        }

        return result;
    }

    public List<PossibleTranslations> possibleTranslations(NtoNTranslation translation, int limit) {
        return possibleTranslations(translation.getSourceSentences(), translation.getTargetSentences(), limit, false);
    }

    public List<PossibleTranslations> reversePossibleTranslations(NtoNTranslation translation, int limit) {
        return possibleTranslations(translation.getTargetSentences(), translation.getSourceSentences(), limit, true);
    }

    public List<PossibleTranslations> possibleTranslations(
            final List<String> sourceSentences, final List<String> targetSentences,
            final int limit, final boolean reverse) {

        List<PossibleTranslations> translations = new LinkedList<PossibleTranslations>();
        List<Callable<PossibleTranslations>> tasks = new LinkedList<Callable<PossibleTranslations>>();

        for (String source : sourceSentences) {
            Matcher m = getWordParser().getWordPattern().matcher(source);

            while (m.find()) {
                final String word = m.group();
                Callable<PossibleTranslations> task = new Callable<PossibleTranslations>() {
                    public PossibleTranslations call() throws Exception {
                        return possibleTranslations(word, targetSentences, limit, reverse);
                    }
                };
                tasks.add(task);
            }
        }

        int tries = 0;
        while (tries++ < 3) {
            try {
                for (Future<PossibleTranslations> task : exec.invokeAll(tasks)) {
                    translations.add(task.get());
                }
                break;
            } catch (InterruptedException e) {
                System.out.println("[warning] interrupted: " + e.getMessage());
            } catch (ExecutionException e) {
                e.printStackTrace();
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
                Candidate candidate = new Candidate(word, 0);
                for (String decl : (reverse ? targetDeclensions(sourceWord, true) : sourceDeclensions(sourceWord, true))) {
                    double p = !reverse ? translationProbability(decl, word) : reverseTranslationProbability(decl, word);

                    candidate.boostProbability(p);
                }
                candidates.add(candidate);
            }
        }

        Collections.sort(candidates, new Comparator<Candidate>() {
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

        Map<String, List<NtoNTranslation>> cache = targetGivenSource ?
                targetGivenSourceCache : sourceGivenTargetCache;
        List<NtoNTranslation> matches = cache.get(word2);

        if (matches == null) {
            matches = new LinkedList<NtoNTranslation>();
            for (NtoNTranslation tr : getCorpus()) {
                if (getWordParser().findWord(word2, targetGivenSource ? tr.getSourceSentences() : tr.getTargetSentences()))
                    matches.add(tr);
            }
            cache.put(word2, matches);
        }

        for (NtoNTranslation tr : matches) {
            if (getWordParser().findWord(word1, targetGivenSource ? tr.getTargetSentences() : tr.getSourceSentences()))
                ++occurrences;
        }

        return (occurrences / (double) matches.size()) / getCorpus().size();
    }

    protected void computeWordDistribution() {
        sourceWords = new HashMap<String, Integer>();
        targetWords = new HashMap<String, Integer>();

        for (NtoNTranslation tr : getCorpus()) {
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

    public List<T> getCorpus() {
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
