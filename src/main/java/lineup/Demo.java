package lineup;

import java.io.*;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import lineup.splitters.GermanEnglishSplitter;

import static lineup.util.Fun.*;

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

    public Demo() throws UnsupportedEncodingException {
        corpus = loadCorpus();
        dist = Alignment.byWordDistribution(corpus);

        commands.addAll(List(new Exit(), new Help(), new Corpus(), new Show(), new Break(), new Details()));

        dist.setOut(new PrintStream(System.out, true, "UTF8"));
        dist.setSplitter(new GermanEnglishSplitter(dist.getWordParser()));

        out = dist.getOut();
    }

    protected List<Translation> loadCorpus() throws UnsupportedEncodingException {
        try {
            return new LineupCorpusReader().readCorpus("src/main/resources/europarl3.txt");
        } catch (FileNotFoundException e) {
            InputStream in = getClass().getClassLoader().getResourceAsStream("europarl3.txt");

            if (in != null) {
                return new LineupCorpusReader().readCorpus(new InputStreamReader(in, "UTF8"));
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
                        dist.printSentence(index);
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
                        dist.printAligned(dist.getSentences(index, length), false);
                    } else {
                        dist.printAligned(index);
                    }
                } else {
                    out.println("n out of range");
                }
                return;
            } else {
                dist.printRandomAligned();
            }
        }
    }

    class Details implements Command {
        public boolean respondTo(String input) {
            return "details".equals(input);
        }

        public void perform(String input) {
            dist.details();
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
