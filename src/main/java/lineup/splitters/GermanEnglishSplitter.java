package lineup.splitters;

import lineup.util.*;
import lineup.*;

import java.util.*;

import static lineup.util.Fun.*;

/**
 * Splitter specifically for translations from German to English.
 */
public class GermanEnglishSplitter extends Splitter {

	public static final Word NO_WORD = new Word(-1, "");

	private double maxTranslationDistance = 9;

	/**
	 * Creates a new GermanEnglishSplitter.
	 *
	 * @param wordParser The WordParser to use to extract words from sentences.
	 */
	public GermanEnglishSplitter(WordParser wordParser) {
		super(wordParser);
	}

	public GermanEnglishSplitter() {
		super();
	}

	/**
	 * The basic idea for this algorithm is that there will usually be clusters of known translations interleaved
	 * with words who don't have any translation candidates. Those clusters then indicate words that form phrases with
	 * a shared thought.
	 * Although this is specific to the current word alignment model which has a low recall rate.
	 *
	 * In a first stage those coarse clusters will be used to insert breaks.
	 * The second stage then reduces the cluster size to 1 to try break between single words
	 * in case the first stage didn't yield enough line breaks.
	 *
	 * More coarse line breaks are marked as more confident. Accordingly the resulting wrapping will be of a
	 * better quality when only using those line breaks but may yield fewer possible wrapping positions.
	 */
	public Tuple<Sentences, Sentences> insertLineBreaks(Tuple<Sentences, Sentences> translation) {

		List<Tuple<Sentences, Sentences>> segments = processClusters(translation);
		Sentences de = translation._1.copy();
		Sentences en = translation._2.copy();
		int deStart, enStart, deEnd = 0, enEnd = 0;
		for (Tuple<Sentences, Sentences> seg : segments) {
			deStart = deEnd;
			enStart = enEnd;
			deEnd = deStart + seg._1.getTokens().size();
			enEnd = enStart + seg._2.getTokens().size();

			if (deStart != 0 || enStart != 0) {
				de.getTokens().add(deStart, new LineBreak(0.75)); // inter-cluster line breaks
				en.getTokens().add(enStart, new LineBreak(0.75));
				deEnd++; enEnd++;
			}

			List<Tuple<Sentences, Sentences>> subsegs = processClusters(seg, 1);
			if (subsegs.size() > 1) {
				int deOffset = 1, enOffset = 1;
				Iterator<Tuple<Sentences, Sentences>> ss = subsegs.iterator();
				while (ss.hasNext()) {
					Tuple<Sentences, Sentences> subseg = ss.next();
					if (!ss.hasNext()) {
						break; // skip last
					}
					deOffset += subseg._1.getTokens().size();
					enOffset += subseg._2.getTokens().size();
					if (deOffset > 0) {
						de.getTokens().add(deStart + deOffset++, new LineBreak(0.5)); // intra-cluster line breaks
						en.getTokens().add(enStart + enOffset++, new LineBreak(0.5));
						++deEnd;
						++enEnd;
					}
				}
			}
		}

		assert(de.lineBreaks() == en.lineBreaks());

		uniteGenitive(en);

		return tuple(de, en);
	}

	/**
	 * If we ignore the genitive in English this may lead to token sequences such as [W(Parliament), NL(0.75), P('), W(s)].
	 * This method will fix that to [W(Parliament), P('), W(s), NL(0.75)].
	 */
	protected void uniteGenitive(Sentences sent) {
		int i = 0;
		while (i < sent.getTokens().size()) {
			ListIterator<Token> tokens = sent.getTokens().subList(i, sent.getTokens().size()).listIterator();
			while (tokens.hasNext()) {
				Token token = tokens.next();
				++i;

				if (token.isLineBreak() && i + 2 <= sent.getTokens().size()) {
					List<Token> next = sent.getTokens().subList(i - 1, i + 2);
					if (next.size() == 3 && next.get(1).getValue().equals("'") && next.get(2).getValue().equals("s")) {
						next.add(next.remove(0));
						i += 2;
						break;
					}
				}
			}
		}
	}

	public Tuple<Sentences, Sentences> toSentences(String src, String tgt, List<PossibleTranslations> pts) {
		return Sentences.wire(src, tgt, pts, maxTranslationDistance, getWordParser());
	}

	/**
	 * Requires the first Sentences object to already be initialized with validated word matches.
	 * Inserts linebreaks based on clusters.
	 */
	public List<Tuple<Sentences, Sentences>> processClusters(Tuple<Sentences, Sentences> translation, int maxClusterSize) {
		List<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> segments = cluster(translation, true, maxClusterSize);
		List<Tuple<Sentences, Sentences>> result = new LinkedList<Tuple<Sentences, Sentences>>();
		int deIndex = 0;
		int enIndex = 0;

		for (Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>> seg : segments) {
			Tuple<Integer, Integer> de = seg._1;
			Tuple<Integer, Integer> en = seg._2;

			String des = mkString(translation._1.getTokens().subList(de._1, de._2 + 1), "");
			String ens = mkString(translation._2.getTokens().subList(en._1, en._2 + 1), "");

			Tuple<Sentences, Sentences> gap = tuple( // gap between last and pair
				translation._1.subSentence(deIndex, de._1),
				translation._2.subSentence(enIndex, en._1));

			if (!gap._1.isEmpty() || !gap._2.isEmpty()) {
				result.add(gap);
			}

			Tuple<Sentences, Sentences> pair = tuple(
				translation._1.subSentence(de._1, deIndex = de._2 + 1),
				translation._2.subSentence(en._1, enIndex = en._2 + 1));

			result.add(pair);
		}

		Tuple<Sentences, Sentences> gap = tuple( // gap between last and end
				translation._1.subSentence(deIndex, -1),
				translation._2.subSentence(enIndex, -1));

		if (!gap._1.isEmpty() || !gap._2.isEmpty()) {
			result.add(gap);
		}

		return result;
	}

	public List<Tuple<Sentences, Sentences>> processClusters(Tuple<Sentences, Sentences> translation) {
		return processClusters(translation, -1);
	}

	/**
	 * For a translation computes aligned clusters of a given size. That is adjacent source words
	 * that are associated with candidates in the target sentences.
	 *
	 * @param translation Translation for which to get clusters.
	 * @param mergeIntersections Merge intersecting clusters. This keeps passages such as "European [...] Union" together.
	 * @param maxClusterSize Maximum number of adjacent source words to form a cluster.
	 */
	public List<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> cluster(
			Tuple<Sentences, Sentences> translation, boolean mergeIntersections,
			int maxClusterSize) {

		List<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> segments =
				new LinkedList<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>>();
		List<Word> cluster = new LinkedList<Word>();
		Word previous = null;

		if (maxClusterSize < 0) {
			maxClusterSize = Integer.MAX_VALUE;
		}

		for (Token token : translation._1.getTokens()) {
			if (token.isWord()) {
				Word word = (Word) token;
				boolean previousMatches = previous == null || !previous.getMatches().isEmpty();

				if (!word.getMatches().isEmpty()) { // add to current cluster
					cluster.add(word);
				}

				// add breaks for current cluster and start new one
				if (word.getMatches().isEmpty() || cluster.size() >= maxClusterSize) {
					if (!cluster.isEmpty()) {
						addSegment(segments, cluster, translation);
					}
					previous = null;
					cluster.clear();
				}

				previous = word;
			}
		}

		if (!cluster.isEmpty()) { // add possible trailing cluster
			addSegment(segments, cluster, translation);
		}

		while (mergeIntersections) { // merge until all intersections are found
			int size = segments.size();
			segments = mergeIntersectingSegments(segments);

			if (size == segments.size())
				break;
		}

		return segments;
	}

	public List<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> cluster(
			Tuple<Sentences, Sentences> translation) {

		return cluster(translation, true);
	}

	public List<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> cluster(
			Tuple<Sentences, Sentences> translation, boolean mergeIntersections) {

		return cluster(translation, mergeIntersections, -1);
	}

	public List<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> mergeIntersectingSegments(
			List<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> segments) {

		LinkedList<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> result =
			new LinkedList<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>>();

		for (Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>> segment : segments) {
			if (!result.isEmpty()) {
				Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>> previous = result.getLast();
				if (previous._1._2 >= segment._1._1 || previous._2._2 >= segment._2._1) { // intersecting bounds
					result.removeLast();
					result.add(tuple(
						tuple(Math.min(previous._1._1, segment._1._1), Math.max(previous._1._2, segment._1._2)),
						tuple(Math.min(previous._2._1, segment._2._1), Math.max(previous._2._2, segment._2._2))));
				} else {
					result.add(segment);
				}
			} else {
				result.add(segment);
			}
		}

		return result;
	}

	protected void addSegment(
			List<Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>> segments,
			List<Word> cluster, Tuple<Sentences, Sentences> translation) {

		List<Word.Match> matches = new LinkedList<Word.Match>();
		for (Word cw : cluster) {
			matches.addAll(cw.getMatches());
		}

		Tuple<Integer, Integer> deBounds = tuple(minWordIndex(cluster, translation._1), maxWordIndex(cluster, translation._1));
		Tuple<Integer, Integer> enBounds = tuple(minMatchIndex(matches, translation._2), maxMatchIndex(matches, translation._2));

		Tuple<Word, Integer> dePrev = previousWord(deBounds._1, translation._1);
		if (dePrev != null && isGermanArticle(dePrev._1)) {
			deBounds = tuple(dePrev._2, deBounds._2);
		}

		Tuple<Word, Integer> enPrev = previousWord(enBounds._1, translation._2);
		if (enPrev != null && isEnglishArticle(enPrev._1)) {
			enBounds = tuple(enPrev._2, enBounds._2);
		}

		Word deLast = wordAt(deBounds._2, translation._1);
		if (deLast != null) {
			if (isGermanArticle(deLast) && translation._1.getTokens().size() > deBounds._2 + 2) {
				deBounds = tuple(deBounds._1, deBounds._2 + 2);
			}
		}

		Word enLast = wordAt(enBounds._2, translation._2);
		if (enLast != null) {
			if (isEnglishArticle(enLast) && translation._2.getTokens().size() > enBounds._2 + 2) {
				enBounds = tuple(enBounds._1, enBounds._2 + 2);
			}
		}

		segments.add(tuple(deBounds, enBounds));
	}

	public Tuple<Word, Integer> previousWord(Word word, Sentences sent) {
		Word previous = null;
		int pi = -1;
		int i = 0;

		for (Token token : sent.getTokens()) {
			if (token.isWord()) {
				Word w = (Word) token;

				if (w.equals(word)) {
					if (previous != null) {
						return tuple(previous, pi);
					} else {
						return null;
					}
				} else {
					previous = w;
					pi = i;
				}
			}
			++i;
		}

		return null;
	}

	public Tuple<Word, Integer> previousWord(int index, Sentences sent) {
		Word previous = null;
		int pi = -1;
		int i = 0;

		for (Token token : sent.getTokens()) {
			if (token.isWord()) {
				Word w = (Word) token;

				if (i == index) {
					if (previous != null) {
						return tuple(previous, pi);
					} else {
						return null;
					}
				} else {
					previous = w;
					pi = i;
				}
			}
			++i;
		}

		return null;
	}

	public boolean isGermanArticle(Word word) {
		String w = word.getValue().toLowerCase();

		boolean definite = w.startsWith("d") && w.length() == 3;
		boolean indefinite = w.startsWith("ein") && w.length() <= 5;
		boolean these = w.startsWith("diese") && w.length() <= 6;
		boolean contr = "im".equals(w) || "vom".equals(w) || "zum".equals(w) || "ins".equals(w) || "aufs".equals(w);

		return definite || indefinite || these || contr;
	}

	public boolean isEnglishArticle(Word word) {
		return englishArticles.contains(word.getValue().toLowerCase());
	}

	private final List<String> englishArticles = Arrays.asList(
		"a", "an","the", "these", "those");

	public int minIndex(Word word, Sentences en) {
		for (int i = 0; i < en.getTokens().size(); ++i) {
			if (en.getTokens().get(i).equals(word)) {
				return i;
			}
		}

		return -1;
	}

	public int maxIndex(Word word, Sentences en) {
		for (int i = en.getTokens().size() - 1; i >= 0; --i) {
			if (en.getTokens().get(i).equals(word)) {
				return i;
			}
		}

		return -1;
	}

	public int minWordIndex(List<Word> words, Sentences de) {
		int i = 0;

		for (Token token : de.getTokens()) {
			for (Word word : words) {
				if (word.equals(token)) {
					return i;
				}
			}
			++i;
		}

		return -1;
	}

	public int minMatchIndex(List<Word.Match> candidates, Sentences en) {
		int result = -1;

		for (Word.Match match : candidates) {
			int i = minIndex(match.getWord(), en);

			if (i != -1 && (i < result || result == -1)) {
				result = i;
			}
		}

		return result;
	}

	public int maxWordIndex(List<Word> words, Sentences de) {
		int result = -1;
		int i = 0;

		for (Token token : de.getTokens()) {
			for (Word word : words) {
				if (word.equals(token) && i > result) {
					result = i;
				}
			}
			++i;
		}

		return result;
	}

	public int maxMatchIndex(List<Word.Match> candidates, Sentences en) {
		int result = -1;

		for (Word.Match match : candidates) {
			int i = maxIndex(match.getWord(), en);

			if (i > result) {
				result = i;
			}
		}

		return result;
	}

	public Word wordAt(int index, Sentences sent) {
		Token token = sent.getTokens().get(index);

		if (token.isWord()) {
			return (Word) token;
		} else {
			return NO_WORD;
		}
	}

	public List<Relation> split(Relation pair, List<PossibleTranslations> relations) {
		Tuple<Sentences, Sentences> translation = toSentences(pair.getSource(), pair.getTarget(), relations);

		return split(translation);
	}

	public List<Relation> split(NtoNTranslation tr, List<PossibleTranslations> pts) {
		return split(new Relation(
				mkString(tr.getSourceSentences(), " "), mkString(tr.getTargetSentences(), " ")), pts);
	}

	public List<Relation> split(Tuple<Sentences, Sentences> translation) {

		List<Tuple<Sentences, Sentences>> segments = processClusters(translation);
		List<Relation> result = new LinkedList<Relation>();

		for (Tuple<Sentences, Sentences> seg : segments) {
			result.add(new Relation(seg._1.getText(), seg._2.getText()));
		}

		int i = 0;
		for (Tuple<Sentences, Sentences> seg : segments) {
			List<Tuple<Sentences, Sentences>> subsegs = processClusters(seg, 1);
			List<Relation> subresult = new LinkedList<Relation>();
			if (subsegs.size() > 1) {
				for (Tuple<Sentences, Sentences> subseg : subsegs) {
					subresult.add(new Relation(subseg._1.getText(), subseg._2.getText()));
				}
				result.remove(i);
				result.addAll(i, subresult);
			}
			++i;
		}

		return result;
	}

	public void setMaxTranslationDistance(double wordDistance) {
		this.maxTranslationDistance = wordDistance;
	}

	/**
	 * The maximum translation distance defines up until what token distance the Splitter
	 * will still consider translation candidates. If a candidate is too far away it will
	 * make too large of a part of the sentence "unbreakable".
	 *
	 * Not considering a candidate does not acknowledge that the candidate is wrong,
	 * but it concedes that the Splitter is not able to come up with a reasonable wrapping position
	 * based on that.
	 *
	 * Example:
	 *   "er hat ihm den Zugang zur Einrichtung der Behörde gegeben"
	 *   ->
	 *   "he gave him access to the facilities of the agency"
	 *
	 *   PossibleTranslations(gave, List(hat, gegeben))
	 *
	 * Considering 'gegeben' would require the whole sentence to stay intact which would make any wraps impossible.
	 * So it is discarded to enable some (although not entirely correct) wrapping.
	 */
	public double getMaxTranslationDistance() {
		return maxTranslationDistance;
	}
}
