package lineup;

import java.util.*;

public class PossibleTranslations {
    private String sourceWord;
    private List<Candidate> candidates;

    public PossibleTranslations(String sourceWord, List<Candidate> candidates) {
        this.sourceWord = sourceWord;
        this.candidates = candidates;
    }

    public void sort() {
        Collections.sort(getCandidates(), new Comparator<Candidate>() {
            @Override
            public int compare(Candidate c1, Candidate c2) {
                return new Double(c2.getProbability()).compareTo(new Double(c1.getProbability()));
            }
        });
    }

    public void prune(int length) {
        Iterator<Candidate> i = getCandidates().iterator();
        int j = 0;
        while (i.hasNext()) {
            i.next();
            if (j++ >= length) {
                i.remove();
            }
        }
    }

    public String getSourceWord() {
        return sourceWord;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public PossibleTranslations copy() {
        List<Candidate> candidates = new LinkedList<Candidate>();
        PossibleTranslations copy = new PossibleTranslations(getSourceWord(), candidates);

        for (Candidate cand : getCandidates()) {
            candidates.add(new Candidate(cand.getWord(), cand.getProbability()));
        }

        return copy;
    }

    @Override
    public String toString() {
        return "PossibleTranslations(" + sourceWord + " => " + candidates + ")";
    }
}