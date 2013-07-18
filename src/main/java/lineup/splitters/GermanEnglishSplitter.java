package lineup.splitters;

import lineup.util.Relation;
import lineup.PossibleTranslations;

import java.util.List;

public class GermanEnglishSplitter extends Splitter {
	public List<Relation> split(Relation pair, List<PossibleTranslations> relations) {
		return java.util.Arrays.asList(pair); // no op
	}
}