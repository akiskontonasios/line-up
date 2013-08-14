package lineup;

import lineup.util.Relation;
import lineup.splitters.*;

import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.concurrent.*;

import lineup.util.*;
import lineup.util.Terminal.*;

import static lineup.util.Fun.*;
import static lineup.util.Terminal.*;

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
public class DistAlign<T extends NtoNTranslation> {

    private List<T> corpus;

    private Map<String, Integer> sourceWords;
    private Map<String, Integer> targetWords;
    private final int sourceWordCount;
    private final int targetWordCount;

    private List<String> sourceBlacklist = new LinkedList<String>();
    private List<String> targetBlacklist = new LinkedList<String>();

    private Map<String, List<PossibleTranslations>> cache = new HashMap<String, List<PossibleTranslations>>();
    private Map<String, List<Integer>> indexCache = new HashMap<String, List<Integer>>();

    private PrintStream out = new PrintStream(System.out);

    private WordParser wordParser;
    private Splitter splitter;
    private int maxTranslationDistance = 9;

    ExecutorService exec = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);

                return thread;
            }
        });

    public DistAlign(List<T> corpus, WordParser wordParser) {
        this.corpus = corpus;
        this.wordParser = wordParser;
        this.splitter = new GermanEnglishSplitter(getWordParser());

        computeWordDistribution();

        sourceWordCount = sumValues(getSourceWords());
        targetWordCount = sumValues(getTargetWords());
    }

    public DistAlign(List<T> corpus) {
        this(corpus, WordParser.instance);
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

    public Tuple<Sentences, Sentences> getSentences(int startIndex, int length) {
        List<Token> noTokens = new LinkedList<Token>();
        Sentences src = new Sentences(noTokens);
        Sentences tgt = new Sentences(noTokens);

        ptsCache = new LinkedList<PossibleTranslations>();

        for (int i = 0; i < length; ++i) {
            T tr = getCorpus().get(startIndex + i);
            List<PossibleTranslations> pts = associate(startIndex + i, 6);
            Tuple<Sentences, Sentences> sent = Sentences.wire(tr, pts, maxTranslationDistance, getWordParser());

            ptsCache.addAll(pts);

            if (i > 0) {
                src.getTokens().add(new Punctuation(" "));
                tgt.getTokens().add(new Punctuation(" "));
            }
            src.getTokens().addAll(sent._1.getTokens());
            tgt.getTokens().addAll(sent._2.getTokens());
        }

        return tuple(src, tgt);
    }

    public void printRandomAligned() {
        int index = random.nextInt(corpus.size());
        out.println("============ " + index + " ============");
        printAligned(index);
    }

    public void printAligned(int index) {
        printAligned(index, false);
    }

    public void printAligned(int index, boolean highlightRelated) {
        NtoNTranslation translation = getCorpus().get(index);
        List<PossibleTranslations> pts = ptsCache = associate(index, 6);
        Tuple<Sentences, Sentences> sent = Sentences.wire(translation, pts, maxTranslationDistance, getWordParser());

        printAligned(sent, highlightRelated);
    }

    public void printAligned(Tuple<Sentences, Sentences> sent, boolean highlightRelated) {
        try {
            Tuple<Sentences, Sentences> aligned = getSplitter().insertLineBreaks(sent);
            Tuple<List<Token>, List<Token>> tokens = tuple(aligned._1.getTokens(), aligned._2.getTokens());
            LineBreak lineBreak = new LineBreak(42);
            int breaks = aligned._1.lineBreaks();

            java.io.StringWriter line1 = new java.io.StringWriter();
            java.io.StringWriter line2 = new java.io.StringWriter();
            java.io.PrintWriter del = new java.io.PrintWriter(line1);
            java.io.PrintWriter enl = new java.io.PrintWriter(line2);

            for (int i = 0; i <= breaks; ++i) {
                Tuple<List<Token>, List<Token>> src = splitAt(lineBreak, tokens._1);
                Tuple<List<Token>, List<Token>> tgt = splitAt(lineBreak, tokens._2);

                String de = Sentences.getValue(src._1);
                String en = Sentences.getValue(tgt._1);
                int width = Math.max(de.length(), en.length());
                boolean noRelations = true;

                for (Token token : src._1) {
                    if (token.isWord() && !((Word) token).getMatches().isEmpty()) {
                        noRelations = false;
                        break;
                    }
                }

                if (width > 0) {
                    if (highlightRelated) {
                        if (noRelations) {
                            del.print(startPaint(fgDefault));
                            enl.print(startPaint(fgDefault));
                        } else {
                            del.print(startPaint(green));
                            enl.print(startPaint(green));
                        }
                    }
                    del.printf("%" + width + "s", de);
                    enl.printf("%" + width + "s", en);

                    del.print(stopPaint());
                    enl.print(stopPaint());
                }

                String br = "|\u2424|";

                if (i < breaks) {
                    LineBreak deb = aligned._1.lineBreaksAt(i);
                    LineBreak enb = aligned._2.lineBreaksAt(i);

                    if (deb.getConfidence() >= 0.75) {
                        del.print(painted(green, br));
                        enl.print(painted(green, br));
                    } else if (deb.getConfidence() >= 0.5) {
                        del.print(painted(yellow, br));
                        enl.print(painted(yellow, br));
                    } else {
                        del.print(painted(red, br));
                        enl.print(painted(red, br));
                    }
                }

                tokens = tuple(src._2, tgt._2);
            }

            getOut().println(line1.toString());
            getOut().println(line2.toString());
        } catch (AssertionError e) {
            System.err.println("The algortihm failed on this one (" + e.getMessage() + ").");
        }
    }

    public void details() {
        if (ptsCache != null) {
            out.println("============ Relations ============");
            int maxWordLength = 0;
            for (PossibleTranslations pt : ptsCache) {
                if (pt.getSourceWord().length() > maxWordLength) {
                    maxWordLength = pt.getSourceWord().length();
                }
            }

            for (PossibleTranslations pt : ptsCache) {
                out.printf(" %" + maxWordLength + "s => ", pt.getSourceWord());
                boolean first = true;
                for (Candidate cand : pt.getCandidates()) {
                    if (first) {
                        first = false;
                    } else {
                        out.print(", ");
                    }
                    out.printf("(%s, %.2g)", cand.getWord(), cand.getProbability());
                }
                out.println();
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
            int line = Math.max(src.length(), tgt.length());

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

            StringBuilder sb = new StringBuilder(line);
            for (int i = 0; i < line; ++i) {
                sb.append("-");
            }
            out.println(sb.toString());
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
        NtoNTranslation tr = getCorpus().get(index);

        return splitter.split(tr, pts);
    }

    public void printSentence(int index) {
        NtoNTranslation tr = getCorpus().get(index);
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
        Map<String, Integer> targetWordCounts = new HashMap<String, Integer>();

        addToDistribution(getCorpus().get(index).getTargetSentences(), targetWordCounts);

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

    private Map<String, Set<String>> srcDeclCache = new HashMap<String, Set<String>>();
    private Map<String, Set<String>> tgtDeclCache = new HashMap<String, Set<String>>();

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
        int wordCount = targetWordCounts.get(word);
        List<Double> probabilities = new LinkedList<Double>();

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
        minProb = probabilities.get(probabilities.size() - 1);
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
            for (NtoNTranslation tr : getCorpus()) {
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
        List<Future<PossibleTranslations>> tasks = new LinkedList<Future<PossibleTranslations>>();

        for (String source : sourceSentences) {
            Matcher m = getWordParser().getWordPattern().matcher(source);

            while (m.find()) {
                final String word = m.group();
                Callable<PossibleTranslations> task = new Callable<PossibleTranslations>() {
                    public PossibleTranslations call() throws Exception {
                        return possibleTranslations(word, targetSentences, limit, reverse);
                    }
                };
                tasks.add(exec.submit(task));
            }
        }

        for (Future<PossibleTranslations> task : tasks) {
            PossibleTranslations value = null;
            while (value == null) {
                try {
                    value = task.get();
                } catch (InterruptedException e) {
                    System.out.println("[warning] " + e.getMessage());
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            translations.add(value);
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

    private Map<String, List<NtoNTranslation>> targetGivenSourceCache = new ConcurrentHashMap<String, List<NtoNTranslation>>();
    private Map<String, List<NtoNTranslation>> sourceGivenTargetCache = new ConcurrentHashMap<String, List<NtoNTranslation>>();

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

    public void setSplitter(Splitter splitter) {
        this.splitter = splitter;
    }

    public Splitter getSplitter() {
        return splitter;
    }
}
