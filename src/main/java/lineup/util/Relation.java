package lineup.util;

/**
 * Description goes here.
 *
 * @author Markus Kahl
 */
public class Relation {
    private String source;
    private String target;

    public Relation(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return String.format("Relation(%s -> %s)", getSource(), getTarget());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Relation relation = (Relation) o;

        if (!source.equals(relation.source)) return false;
        if (!target.equals(relation.target)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + target.hashCode();
        return result;
    }
}
