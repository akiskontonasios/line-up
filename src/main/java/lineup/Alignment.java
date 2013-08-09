package lineup;

import java.util.List;

public class Alignment {
	public static <T extends NtoNTranslation> DistAlign<T> byWordDistribution(List<T> corpus) {
		if (corpus == null || corpus.isEmpty())
			throw new IllegalArgumentException("DistAlign requires a non-empty corpus.");

		DistAlign<T> dist = new DistAlign<T>(corpus);
		String srcLang = corpus.get(0).getSourceLanguage();
		String tgtLang = corpus.get(0).getTargetLanguage();

		if ("en".equals(tgtLang)) {
			dist.getTargetBlacklist().add("s"); // s is only a particle indicating genitive
		}

		return dist;
	}
}