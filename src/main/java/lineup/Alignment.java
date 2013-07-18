package lineup;

import java.util.List;

public class Alignment {
	public static DistAlign byWordDistribution(List<Translation> corpus) {
		if (corpus == null || corpus.isEmpty())
			throw new IllegalArgumentException("DistAlign requires a non-empty corpus.");

		DistAlign dist = new DistAlign(corpus);
		String srcLang = corpus.get(0).getSourceLanguage();
		String tgtLang = corpus.get(0).getTargetLanguage();

		if ("en".equals(tgtLang)) {
			dist.getTargetBlacklist().add("s"); // s is only a particle indicating genitive
		}

		return dist;
	}
}