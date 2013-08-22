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
    private CognateModel cognateModel = new CognateModel(4, 0.10);

    ExecutorService exec = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);

                return thread;
            }
        });

    /**
     * Creates a new StatAlign instance whose statistical model is based on the given corpus.
     *
     * @param corpus Corpus to build model on.
     * @param wordParser WordParser to extract words from sentences.
     */
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

    /**
     * Joins adjacent translations into one pair of Sentences instances.
     * This is not specific to this model, but put here for evaluation purposes.
     *
     * @param startIndex Index of first translation to use.
     * @param length Number of adjacent translations to join.
     * @param maxTranslationDistance Maximum token distance before discarding suggested translations due to wrapping infeasability.
     */
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

    public List<PossibleTranslations> associate(NtoNTranslation translation) {
        return associate(translation, 6, 3, true);
    }

    /**
     * Computes a word alignment for a given translation. Each word of the source sentence is associated with a number
     * of candidates that are possible translations in the target sentence. The alignment is based both on the basic
     * statistical model and a congnate model.
     *
     * @param translation The translation for which a word alignment is required.
     * @param limit Limits the number of generated possible translations in the basic step to the n most likely ones.
     * @param prune Prune the resulting number of candidates to the n most likely ones.
     * @param retainMostLikely From every target word retain only the one most likely candidate.
     *
     * @return For each word in the source sentences of the translation a list of possible translations.
     */
    public List<PossibleTranslations> associate(NtoNTranslation translation, int limit, int prune, boolean retainMostLikely) {
        List<PossibleTranslations> matches = matches(translation, limit);
        Set<Relation> relations = findRelatedWords(translation.getSourceSentences(), translation.getTargetSentences());
        Map<String, Integer> targetWordCounts = new HashMap<String, Integer>();

        addToDistribution(translation.getTargetSentences(), targetWordCounts);

        for (PossibleTranslations pt : matches) {
            for (Relation rel : relations) {
                if (rel.getSource().equals(pt.getSourceWord())) {
                    // add cognate matches
                    Candidate cand = new Candidate(
                            rel.getTarget(),
                            sourceProbability(rel.getSource()) * targetProbability(rel.getTarget()));

                    if (rel.getSource().equals(rel.getTarget())) {
                        cand.setProbability(0.99);
                    }
                    pt.getCandidates().add(cand);
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

    public List<PossibleTranslations> associate(int index) {
        return associate(index, 6);
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

    /**
     * Computes basic word alignment for a given translation.
     *
     * It does so by generating possible translations in both directions, i.e. from source to target and vice versa.
     * The probabilities of the returned candidates are then the product of the source word being the translation of a
     * candidate and the other way around.
     *
     * @param translation Translation to get word alignment for.
     * @param limit Limits the number of generated possible translations in the basic step to the n most likely ones.
     *
     * @return A list of PossibleTranslations instances containing one instance for each word in the translation's source sentences.
     */
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

    public List<PossibleTranslations> matches(int index) {
        return matches(index, 3);
    }

    public List<PossibleTranslations> matches(int index, int limit) {
        return matches(getCorpus().get(index), limit);
    }

    /**
     * Computes the set of possible declensions for a given source word.
     *
     * @param word The word to compute declensions for.
     * @param includeSource If true include the input word in the result.
     */
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

    /**
     * Computes the set of possible declensions for a given target word.
     *
     * @param word The word to compute declensions for.
     * @param includeSource If true include the input word in the result.
     */
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

    /**
     * Computes the set of possible declensions for a given word from a specific corpus.
     *
     * @param word The word to compute declensions for.
     * @param words The set of words within the source corpus containing possible declensions.
     * @param includeSource If true include the input word in the result.
     */
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

    /**
     * Finds related words using on a simple cognate model based on n-grams.
     *
     * @param sources Source sentences.
     * @param targets Target sentences.
     * @param n The dimension of n-grams to be used for the cognate model.
     * @param minResemblance The minimum resemblance required to consider two words related.
     *
     * @return A set of Relation instances each of which represents a pair of related words between the source
     *         and the target sentences.
     */
    public Set<Relation> findRelatedWords(List<String> sources, List<String> targets, int n, double minResemblance) {
        Shingling src = new Shingling(n, mkString(sources, ""), getWordParser());
        Shingling tgt = new Shingling(n, mkString(targets, ""), getWordParser());
        Set<Relation> results = new HashSet<Relation>();

        for (Shingling.Shingles ssh : src.getShingles()) {
            for (Shingling.Shingles tsh : tgt.getShingles()) {
                if (ssh.resemblance(tsh) >= minResemblance) {
                    results.add(new Relation(ssh.getWord(), tsh.getWord()));
                }
            }
        }

        return results;
    }

    public Set<Relation> findRelatedWords(List<String> sources, List<String> targets) {
        return findRelatedWords(sources, targets, getCognateModel().getW(), getCognateModel().getResemblance());
    }

    /**
     * Prune the candidates of a list of PossibleTranslations instances so that for each word in the original
     * target sentence at most 1 candidate which is the most likely remains.
     *
     * @param matches Raw word alignment to prune.
     * @param targetWordCounts Counts for every word in the original target sentence.
     * @param pure If pure a new list with depp copied PossibleTranslations instances will be returned.
     *             Otherwise the input list's instance will be modified in-place.
     * @return Pruned word alignment.
     */
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

    public List<PossibleTranslations> pruneMatches(
            List<PossibleTranslations> matches, Map<String, Integer> targetWordCounts) {
        return pruneMatches(matches, targetWordCounts, true);
    }

    /**
     * Given a list of PossibleTranslations instances remove all but the n most likely occurences of a given word
     * where n is the number of occurences of said word in the original sentence.
     *
     * @param word The (target) word whose unlikely occurences are to be removed.
     * @param pts The PossibleTranslations list to apply the pruning to.
     * @param targetWordCounts A map holding for each source word the number of occurences in the original sentence.
     * @param pure If true a new list with deep copies will be returned.
     *             Otherwise the input instances will be changed in-place.
     *
     * @return List of PossibleTranslation instances where overall the word only the most likely candidates
     *         are retained.
     */
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

    /**
     * For a number of source sentences compute possible translations from a number of target sentences for each word.
     *
     * @param sourceSentences Source sentences to get possible translations for.
     * @param targetSentences Target sentences to get possible translation from.
     * @param limit Limits the number of generated possible translations in the basic step to the n most likely ones.
     * @param reverse Swaps the semantics of source and target sentences.
     *
     * @return PossibleTranslations instances for each word in the source sentences in the order in which they occur.
     */
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

    public List<PossibleTranslations> possibleTranslations(NtoNTranslation translation, int limit) {
        return possibleTranslations(translation.getSourceSentences(), translation.getTargetSentences(), limit, false);
    }

    public List<PossibleTranslations> reversePossibleTranslations(NtoNTranslation translation, int limit) {
        return possibleTranslations(translation.getTargetSentences(), translation.getSourceSentences(), limit, true);
    }

    /**
     * For a source word get possible translations in a target sentence.
     *
     * @param sourceWord Word for which to find possible translations.
     * @param targetSentences Target sentences in which to search for possible translations.
     * @param limit Limits the number of generated possible translations in the basic step to the n most likely ones.
     * @param reverse Reverses semantics of source and target. If true sourceWord is actually a target word
     *                and the targetSentences are actually source sentences.
     * @return A PossibleTranslations instance containing translation candidates for the source word in the target sentences.
     */
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

    /**
     * Probability that a source word is translated by a target word.
     */
    public double translationProbability(String sourceWord, String targetWord) {
        return (sourceProbability(sourceWord) * relationProbability(targetWord, sourceWord)) /
                targetProbability(targetWord);
    }

    /**
     * Probability that a target word is translated by a source word.
     */
    public double reverseTranslationProbability(String targetWord, String sourceWord) {
        return (targetProbability(targetWord) * relationProbability(sourceWord, targetWord, false)) /
                sourceProbability(sourceWord);
    }

    /**
     * Source language model, i.e. the probability of a source word occuring.
     */
    public double sourceProbability(String word) {
        return wordProbability(word, getSourceWords(), getSourceWordCount());
    }

    /**
     * Target language model, i.e. the probability of a target word occuring.
     */
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

    /**
     * Translation model.
     */
    public double relationProbability(String target, String source) {
        return relationProbability(target, source, true);
    }

    /**
     * Translation model stating the probability that a source word appears in every translation
     * containing a certain target word.
     *
     * @param word1 Target word.
     * @param word2 Source word.
     * @param targetGivenSource If true (default) the first word is a target word and the second one a source word.
     *                          If false it is the other way around.
     */
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

    /**
     * The default cognate model uses 4-shinglings and requires a resemblance of only 0.10.
     */
    public CognateModel getCognateModel() {
        return cognateModel;
    }

    static class CognateModel {
        private int w;
        private double resemblance;

        public CognateModel(int w, double resemblance) {
            this.w = w;
            this.resemblance = resemblance;
        }

        public int getW() {
            return w;
        }

        public void setW(int w) {
            this.w = w;
        }

        public double getResemblance() {
            return resemblance;
        }

        public void setResemblance(double resemblance) {
            this.resemblance = resemblance;
        }
    }
}
