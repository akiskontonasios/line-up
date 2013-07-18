package lineup;

import java.util.*;

import static lineup.util.Fun.mkString;

/**
 * Description goes here.
 *
 * @author Markus Kahl
 */
public class SimpleAlign {

    private final String punctuation = "[\\.,;\\?!'\"]";

    public List<Translation> align(Translation sentence) {
        String[] srcWords = mkString(sentence.getSourceSentences(), " ").replaceAll(punctuation, "").split(" ");
        String[] tgtWords = mkString(sentence.getTargetSentences(), " ").replaceAll(punctuation, "").split(" ");

        Map<String, HashMap<Integer, LinkedList<String>>> relations = new HashMap<String, HashMap<Integer, LinkedList<String>>>();
        for (final String word : srcWords) {
            HashMap<Integer, LinkedList<String>> diffs = new HashMap<Integer, LinkedList<String>>();
            relations.put(word, diffs);

            for (String target : tgtWords) {
                int diff = diff(word, target);

                LinkedList<String> values = diffs.get(diff);
                if (values == null) {
                    values = new LinkedList<String>();
                    diffs.put(diff, values);
                }

                values.add(target);
            }
        }

        for (String word : relations.keySet()) {
            Map<Integer, LinkedList<String>> diffs = relations.get(word);
            List<Integer> scores = new LinkedList<Integer>();

            scores.addAll(diffs.keySet());
            java.util.Collections.sort(scores);

            System.out.print(word + " ~= ");
            for (Integer diff : scores) {
                List<String> related = diffs.get(diff);
                System.out.print(diff + "(");
                if (related.size() >= 3) {
                    System.out.print(mkString(related.subList(0, 2), ", "));
                } else {
                    System.out.print(mkString(related, ", "));
                }
                System.out.print(") ");
            }
            System.out.println();
        }

        return null;
    }

    protected int diff(String source, String target) {
        int diff = Math.abs(source.length() - target.length());
        LinkedList<Character> srcChars = listCharacters(source);
        LinkedList<Character> tgtChars = listCharacters(target);

        main: for (Character s : srcChars) {
            Iterator<Character> i = tgtChars.iterator();
            if (!i.hasNext()) {
                break;
            }
            while (i.hasNext()) {
                Character t = i.next();
                if (s == t) {
                    --diff;
                    i.remove();
                    continue main;
                }
            }
        }

        // System.out.println("diff(" + source + ", " + target + ") = " + diff);

        return diff;
    }

    protected LinkedList<Character> listCharacters(String str) {
        LinkedList<Character> chars = new LinkedList<Character>();
        for (int i = 0; i < str.length(); ++i) {
            chars.add(str.charAt(i));
        }

        return chars;
    }
}
