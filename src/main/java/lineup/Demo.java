package lineup;

import lineup.splitters.*;
import lineup.util.Relation;
import lineup.util.Tuple;

import java.io.*;
import java.util.*;

import static lineup.util.Fun.*;
import static lineup.util.Terminal.*;
import static lineup.util.Terminal.painted;
import static lineup.util.Terminal.red;

/**
 * Demo of functionality so far.
 *
 * @author Markus Kahl
 */
public class Demo {

    private List<Translation> corpus;
    private DistAlign<Translation> dist;
    private BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private Collection<Command> commands = new LinkedList<Command>();
    private PrintStream out;

    private Random random = new Random();
    private List<PossibleTranslations> ptsCache;

    public Demo() {
        this(Alignment.byWordDistribution(loadCorpus()));
    }

    public Demo(DistAlign<Translation> aligner) {
        corpus = aligner.getCorpus();
        dist = aligner;

        commands.addAll(List(new Exit(), new Help(), new Corpus(), new Show(), new Break(), new Details()));

        try {
            out = new PrintStream(System.out, true, "UTF8");
        } catch (UnsupportedEncodingException e) {
            System.err.println("Unsupported encoding: " + e.getMessage());
            System.exit(1);
        }
        dist.setSplitter(new GermanEnglishSplitter(dist.getWordParser()));
    }

    public static List<Translation> loadCorpus() {
        try {
            return new LineupCorpusReader().readCorpus("src/main/resources/europarl3.txt");
        } catch (FileNotFoundException e) {
            InputStream in = Demo.class.getClassLoader().getResourceAsStream("europarl3.txt");

            if (in != null) {
                try {
                    return new LineupCorpusReader().readCorpus(new InputStreamReader(in, "UTF8"));
                } catch (UnsupportedEncodingException ex) {
                    System.err.println("Unsupported encoding: " + ex.getMessage());
                    System.exit(1);
                }
            } else {
                System.err.println("Could not find corpus: " + e.getMessage());
                System.exit(1);
            }
        }
        return null;
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        new Demo().run(args);
    }

    public void run(String[] args) {
        showCommands();
        repl: while (true) {
            prompt();

            String input = nextCommand();
            for (Command cmd : commands) {
                if (cmd.respondTo(input)) {
                    cmd.perform(input);
                    continue repl;
                }
            }
            out.println("Unknown command");
        }
    }

    public void showCommands() {
        out.println("+-----------------------------------------------------------------------------+");
        out.println("| line-up                  ==== Commands =====                     09.08.2013 |");
        out.println("+-----------------------------------------------------------------------------+");
        out.println("|                                                                             |");
        out.println("| help    - all this                                                          |");
        out.println("| exit    - Quit the Demo                                                     |");
        out.println("| - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - |");
        out.println("| corpus  - Info about corpus                                                 |");
        out.println("| show n  - Show sentence n                                                   |");
        out.println("| break n - Break sentence(s) n [breaks random sentence(s) if no n is given]  |");
        out.println("| details - Shows the relation table for the last shown sentence(s)           |");
        out.println("|                                                                             |");
        out.println("+-----------------------------------------------------------------------------+");
    }

    public void prompt() {
        out.println();
        out.print("demo> ");
    }

    public String nextCommand() {
        try {
            return in.readLine();
        } catch (IOException e) {
            System.err.println("Unexpected error while reading stdin: " + e.getMessage());
            System.exit(1); // I could try opening a new input stream on std in but you could also just rerun the demo
        }

        return null;
    }

    public void printRandomAligned() {
        int index = random.nextInt(corpus.size());
        out.println("============ " + index + " ============");
        printAligned(index);
    }

    public void show(int index) {
        printSentence(index);

        out.println("============ " + index + " ============");

        List<PossibleTranslations> pts = ptsCache = dist.associate(index, 6);

        printbr(index, pts);
    }

    public void showRandom() {
        show(random.nextInt(dist.getCorpus().size()));
    }

    public void printSentence(int index) {
        NtoNTranslation tr = dist.getCorpus().get(index);
        out.println(mkString(tr.getSourceSentences(), " "));
        out.println(mkString(tr.getTargetSentences(), " "));
    }

    public void printAligned(int index) {
        printAligned(index, false);
    }

    public void printAligned(int index, boolean highlightRelated) {
        NtoNTranslation translation = dist.getCorpus().get(index);
        List<PossibleTranslations> pts = ptsCache = dist.associate(index, 6);
        Tuple<Sentences, Sentences> sent = Sentences.wire(translation, pts,
                dist.getMaxTranslationDistance(), dist.getWordParser());

        printAligned(sent, highlightRelated);
    }

    public void printAligned(Tuple<Sentences, Sentences> sent, boolean highlightRelated) {
        try {
            Tuple<Sentences, Sentences> aligned = dist.getSplitter().insertLineBreaks(sent);
            Tuple<List<Token>, List<Token>> tokens = tuple(aligned._1.getTokens(), aligned._2.getTokens());
            LineBreak lineBreak = new LineBreak(42);
            int breaks = aligned._1.lineBreaks();

            java.io.StringWriter line1 = new java.io.StringWriter();
            java.io.StringWriter line2 = new java.io.StringWriter();
            java.io.PrintWriter del = new java.io.PrintWriter(line1);
            java.io.PrintWriter enl = new java.io.PrintWriter(line2);

            for (int i = 0; i <= breaks; ++i) {
                Tuple<List<Token>, List<Token>> src = splitAt(lineBreak, tokens._1);
                Tuple<List<Token>, List<Token>> tgt = splitAt(lineBreak, tokens._2);

                String de = Sentences.getValue(src._1);
                String en = Sentences.getValue(tgt._1);
                int width = Math.max(de.length(), en.length());
                boolean noRelations = true;

                for (Token token : src._1) {
                    if (token.isWord() && !((Word) token).getMatches().isEmpty()) {
                        noRelations = false;
                        break;
                    }
                }

                if (width > 0) {
                    if (highlightRelated) {
                        if (noRelations) {
                            del.print(startPaint(fgDefault));
                            enl.print(startPaint(fgDefault));
                        } else {
                            del.print(startPaint(green));
                            enl.print(startPaint(green));
                        }
                    }
                    del.printf("%" + width + "s", de);
                    enl.printf("%" + width + "s", en);

                    del.print(stopPaint());
                    enl.print(stopPaint());
                }

                String br = "|\u2424|";

                if (i < breaks) {
                    LineBreak deb = aligned._1.lineBreaksAt(i);
                    LineBreak enb = aligned._2.lineBreaksAt(i);

                    if (deb.getConfidence() >= 0.75) {
                        del.print(painted(green, br));
                        enl.print(painted(green, br));
                    } else if (deb.getConfidence() >= 0.5) {
                        del.print(painted(yellow, br));
                        enl.print(painted(yellow, br));
                    } else {
                        del.print(painted(red, br));
                        enl.print(painted(red, br));
                    }
                }

                tokens = tuple(src._2, tgt._2);
            }

            getOut().println(line1.toString());
            getOut().println(line2.toString());
        } catch (AssertionError e) {
            System.err.println("The algortihm failed on this one (" + e.getMessage() + ").");
        }
    }

    public void details() {
        if (ptsCache != null) {
            out.println("============ Relations ============");
            int maxWordLength = 0;
            for (PossibleTranslations pt : ptsCache) {
                if (pt.getSourceWord().length() > maxWordLength) {
                    maxWordLength = pt.getSourceWord().length();
                }
            }

            for (PossibleTranslations pt : ptsCache) {
                out.printf(" %" + maxWordLength + "s => ", pt.getSourceWord());
                boolean first = true;
                for (Candidate cand : pt.getCandidates()) {
                    if (first) {
                        first = false;
                    } else {
                        out.print(", ");
                    }
                    out.printf("(%s, %.2g)", cand.getWord(), cand.getProbability());
                }
                out.println();
            }
        }
    }

    public void printbr(int index) {
        printbr(index, dist.associate(index, 6));
    }

    public void printbr(int index, List<PossibleTranslations> pts) {
        for (Relation part : dist.split(index, pts)) {
            String src = part.getSource().trim();
            String tgt = part.getTarget().trim();
            int line = Math.max(src.length(), tgt.length());

            for (PossibleTranslations pt : pts) {
                if (pt.getCandidates().size() > 0) {
                    src = src.replaceAll("\\b(" + pt.getSourceWord() + ")\\b", "\033[0;32m$1\033[0m");

                    for (Candidate candidate : pt.getCandidates()) {
                        tgt = tgt.replaceAll("\\b(" + candidate.getWord() + ")\\b", "\033[0;34m$1\033[0m");
                    }
                }
            }

            out.println(src);
            out.println(tgt);

            StringBuilder sb = new StringBuilder(line);
            for (int i = 0; i < line; ++i) {
                sb.append("-");
            }
            out.println(sb.toString());
        }
    }

    public PrintStream getOut() {
        return out;
    }

    interface Command {
        public abstract boolean respondTo(String input);
        public abstract void perform(String input);
    }

    class Exit implements Command {
        public boolean respondTo(String input) {
            return "exit".equals(input);
        }

        public void perform(String input) {
            System.exit(0);
        }
    }

    class Corpus implements Command {
        public boolean respondTo(String input) {
            return "corpus".equals(input);
        }

        public void perform(String input) {
            out.println("Europarl 3 Corpus - German to English - " + corpus.size() + " sentence-aligned pairs");
        }
    }

    class Show implements Command {
        public boolean respondTo(String input) {
            return input != null && input.startsWith("show");
        }

        public void perform(String input) {
            List<String> args = drop(1, List(input.split("\\s+")));

            if (args.size() == 1) {
                Scanner scanner = new Scanner(head(args));
                if (scanner.hasNextInt()) {
                    int index = scanner.nextInt();
                    if (index >= 0 && index < corpus.size()) {
                        printSentence(index);
                    } else {
                        out.println("n out of range");
                    }
                    return;
                }
            }
            out.println("I was expecting a number, you know?");
        }
    }

    class Break implements Command {
        public boolean respondTo(String input) {
            return input != null && input.startsWith("break");
        }

        public void perform(String input) {
            Scanner scanner = new Scanner(input);
            if (scanner.hasNext() && scanner.next() != null && scanner.hasNextInt()) {
                int index = scanner.nextInt();
                if (index >= 0 && index < corpus.size()) {
                    if (scanner.hasNextInt()) {
                        int length = scanner.nextInt();
                        printAligned(dist.getSentences(index, length), false);
                    } else {
                        printAligned(index);
                    }
                } else {
                    out.println("n out of range");
                }
                return;
            } else {
                printRandomAligned();
            }
        }
    }

    class Details implements Command {
        public boolean respondTo(String input) {
            return "details".equals(input);
        }

        public void perform(String input) {
            details();
        }
    }

    class Help implements Command {
        public boolean respondTo(String input) {
            return "help".equals(input);
        }

        public void perform(String input) {
            showCommands();
        }
    }
}
