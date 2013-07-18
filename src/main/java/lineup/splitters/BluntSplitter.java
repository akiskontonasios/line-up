package lineup.splitters;

import lineup.util.Relation;
import lineup.PossibleTranslations;

import java.util.*;

import static lineup.util.Fun.*;

/**
 * A very blunt, though language independent splitter that simply splits directly after related words
 * and ignores a number of corner cases.
 */
public class BluntSplitter extends Splitter {
	public List<Relation> split(Relation pair, List<PossibleTranslations> relations) {
		return splitFurther(java.util.Arrays.asList(pair), relations);
	}

	public List<Relation> splitFurther(List<Relation> parts, List<PossibleTranslations> relations) {
        List<Relation> splits = new LinkedList<Relation>();

        for (Relation part : parts) {
            List<Relation> split = splitInTwo(part, relations);
            if (split.size() == 1) {
                splits.add(head(split));
            } else {
                splits.addAll(splitFurther(split, relations));
            }
        }

        return splits;
    }

    public List<Relation> splitInTwo(Relation pair, List<PossibleTranslations> relations) {
        ArrayList<String> srcWords = getWordParser().getWords(pair.getSource());
        ArrayList<String> tgtWords = getWordParser().getWords(pair.getTarget());

        for (PossibleTranslations pt : relations) {
            if (pt.getCandidates().size() == 0)
                continue; // no relation discovered
            String srcWord = pt.getSourceWord();
            String tgtWord = pt.getCandidates().get(0).getWord(); // simply take first for now
            int srcIndex = srcWords.indexOf(srcWord);
            int tgtIndex = tgtWords.indexOf(tgtWord);

            if ((srcIndex == 0 && tgtIndex == 0) || (srcIndex == srcWords.size() && tgtIndex == tgtWords.size()))
                continue;

            List<String> srcParts = List(pair.getSource().split("\\b" + srcWord + "\\b"));
            List<String> tgtParts = List(pair.getTarget().split("\\b" + tgtWord + "\\b"));

            if (srcParts.size() < 2 || tgtParts.size() < 2) {
                //System.err.println(pt + " => uncovered corner case (String#split) - @TODO handle it");
                continue;
            }

            Relation part1 = new Relation(
                    head(srcParts) + srcWord,
                    head(tgtParts) + tgtWord);

            Relation part2 = new Relation(
                    mkString(drop(1, srcParts), srcWord),
                    mkString(drop(1, tgtParts), tgtWord));

            return Arrays.asList(part1, part2);
        }

        return Arrays.asList(pair);
    }
}