package lineup;

public class Candidate {
    private String word;
    private double probability;

    public Candidate(String word, double probability) {
        this.word = word;
        this.probability = probability;
    }

    public String getWord() {
        return word;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }

    public double getProbability() {
        return probability;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Candidate candidate = (Candidate) o;

        if (!word.equals(candidate.word)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return word.hashCode();
    }

    @Override
    public String toString() {
        return "Candidate('" + word + "', " + probability + ")";
    }
}