package lineup;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

/**
 * Description goes here.
 *
 * @author Markus Kahl
 */
public class LineupCorpusReader implements CorpusReader {

    private String sourceLanguage;
    private String targetLanguage;

    public List<Translation> readCorpus(String file) throws FileNotFoundException {
        return readCorpus(new java.io.FileReader(file));
    }

    public List<Translation> readCorpus(Reader reader) {
        List<Translation> result = new LinkedList<Translation>();
        BufferedReader in = new BufferedReader(reader);
        String source = null, target = null, line;

        try {
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;

                if (source == null) {
                    source = line;
                } else if (target == null) {
                    target = line;
                }
                if (source != null && target != null) {
                    parseLanguage(source, true);
                    parseLanguage(target, false);

                    Translation trans = new Translation(getSourceLanguage(), getTargetLanguage());
                    trans.getSourceSentences().addAll(parseSentences(source));
                    trans.getTargetSentences().addAll(parseSentences(target));

                    result.add(trans);

                    source = null;
                    target = null;
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read corpus: " + e.getMessage() +
                    " (" + e.getClass().getName() + ")");
        } catch (CorpusFormatException e) {
            System.err.println("Invalid corpus format: " + e.getMessage());
        }

        return result;
    }

    protected void parseLanguage(String line, boolean source) throws CorpusFormatException {
        if (line.matches("[a-z]{2} \\d.*")) {
            String lang = line.substring(0, line.indexOf(" "));
            if (source) {
                if (getSourceLanguage() == null) {
                    sourceLanguage = lang;
                } else if (!lang.equals(getSourceLanguage())) {
                    throw new CorpusFormatException("Unexpected source language (expected "
                            + getSourceLanguage() + ") in: " + line);
                }
            } else if (!source) {
                if (getTargetLanguage() == null) {
                    targetLanguage = lang;
                } else if (!lang.equals(getTargetLanguage())) {
                    throw new CorpusFormatException("Unexpected target language (expected "
                            + getTargetLanguage() + ") in: " + line);
                } else if (getTargetLanguage().equals(getSourceLanguage())) {
                    throw new CorpusFormatException("Source and target language must be different.");
                }
            }
        } else {
            throw new CorpusFormatException("Expected language, got: " + line);
        }
    }

    protected List<String> parseSentences(String line) throws CorpusFormatException {
        String[] tokens = line.split(":|\\|{2}]");
        if (tokens.length >= 2 && tokens[0].matches("[a-z]{2} \\d")) {
            List<String> result = new LinkedList<String>();
            for (int i = 1; i < tokens.length; ++i) {
                result.add(tokens[i].trim());
            }
            return result;
        } else {
            throw new CorpusFormatException("Could not parse sentences: " + line);
        }
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }
}
