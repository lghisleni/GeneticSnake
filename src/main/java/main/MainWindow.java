package main;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import gameEngine.BestSnakeViewer;
import gameEngine.GameLoop;
import geneticsAlg.SelectionMethod;
import geneticsAlg.SnakeEvolution;
import geneticsAlg.VideoReplay;
import helpers.KeyboardListener;

public class MainWindow extends JFrame {

    private static final String TITLE = "Genetic Snake AI";

    public static double[] bestDNA;

    public static void main(String[] args) {
        System.out.println("ARGOMENTI RICEVUTI: " + java.util.Arrays.toString(args));

        Arguments arguments = Arguments.parse(args);

        if (arguments.help) {
            printHelp();
            return;
        }

        if (arguments.playBestLatest) {
            VideoReplay.playBestLatest("results");
            return;
        }

        if (arguments.playBestDirectory != null) {
            VideoReplay.playBest(arguments.playBestDirectory);
            return;
        }

        if (arguments.videoDirectory != null) {
            VideoReplay.play(arguments.videoDirectory, arguments.videoStep);
            return;
        }

        if (arguments.compareSelections) {
            SnakeEvolution.ComparisonConfig config = new SnakeEvolution.ComparisonConfig(
                    arguments.runs,
                    arguments.maxGenerations,
                    arguments.patience,
                    arguments.minimumGeneration,
                    arguments.minRelativeImprovement,
                    arguments.saveEvery
            );
            SnakeEvolution.compareSelections(config);
            return;
        }

        SelectionMethod selection = arguments.selection;
        bestDNA = SnakeEvolution.train(selection, !arguments.noViewer);
        SelectionMethod finalSelection = selection;
        SwingUtilities.invokeLater(() -> new MainWindow(bestDNA, finalSelection));
    }

    public MainWindow(double[] dna, SelectionMethod selectionMethod) {
        setTitle(TITLE + " - " + selectionMethod.getDisplayName());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(1050, 600));

        KeyboardListener keyboard = new KeyboardListener();
        GameLoop gameLoop = new GameLoop(keyboard, dna);
        gameLoop.setSimulationSpeed(BestSnakeViewer.PRESENTATION_SPEED);
        gameLoop.setTrainingInfo(
                SnakeEvolution.GENERATIONS - 1,
                SnakeEvolution.getLastTrainingBestFitness(),
                SnakeEvolution.getLastTrainingMeanFitness(),
                selectionMethod.getDisplayName()
        );

        add(gameLoop);
        fitToUsableScreen();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                gameLoop.stopLoop();
            }
        });

        setVisible(true);
        gameLoop.requestFocusInWindow();
    }

    private void fitToUsableScreen() {
        Rectangle usable = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        int width = Math.min(1880, Math.max(1050, usable.width - 30));
        int height = Math.min(930, Math.max(600, usable.height - 30));
        setSize(width, height);
    }

    private static void printHelp() {
        System.out.println("Genetic Snake AI");
        System.out.println();
        System.out.println("Training singolo:");
        System.out.println("  --selection tournament|roulette|rank|sus|truncation|sigma");
        System.out.println("  --no-viewer");
        System.out.println();
        System.out.println("Benchmark di tutti i metodi:");
        System.out.println("  --compare");
        System.out.println("  --runs N                     numero di run per metodo (default 5)");
        System.out.println("  --max-generations N          massimo generazioni per run (default 250)");
        System.out.println("  --patience N                 stop dopo N gen senza progresso significativo (default 40)");
        System.out.println("  --min-generations N          non fermare prima di questa gen (default 60)");
        System.out.println("  --min-relative-improvement X miglioramento minimo relativo (default 0.001 = 0.1%)");
        System.out.println("  --save-every N               salva DNA per video ogni N gen (default 5)");
        System.out.println();
        System.out.println("Replay video:");
        System.out.println("  --play-best-latest           mostra il best assoluto dell'ultimo benchmark completato");
        System.out.println("  --play-best <cartella>       mostra il best assoluto di un benchmark specifico");
        System.out.println("  --video <cartella>           replay evoluzione salvata");
        System.out.println("  --video-step N               default 5");
        System.out.println();
        System.out.println("Configurazione consigliata:");
        System.out.println("  --compare --runs 5 --max-generations 250 --patience 40 --min-generations 60 --min-relative-improvement 0.001 --save-every 5");
    }

    private static final class Arguments {
        SelectionMethod selection = SelectionMethod.TOURNAMENT;
        boolean compareSelections = false;
        boolean noViewer = false;
        boolean help = false;
        String videoDirectory = null;
        String playBestDirectory = null;
        boolean playBestLatest = false;
        int videoStep = 5;

        int runs = SnakeEvolution.DEFAULT_COMPARISON_RUNS;
        int maxGenerations = SnakeEvolution.DEFAULT_MAX_GENERATIONS;
        int patience = SnakeEvolution.DEFAULT_EARLY_STOP_PATIENCE;
        int minimumGeneration = SnakeEvolution.DEFAULT_EARLY_STOP_MIN_GENERATION;
        double minRelativeImprovement = SnakeEvolution.DEFAULT_MIN_RELATIVE_IMPROVEMENT;
        int saveEvery = SnakeEvolution.DEFAULT_SAVE_EVERY;

        static Arguments parse(String[] args) {
            Arguments result = new Arguments();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];

                if ("--selection".equals(arg)) {
                    result.selection = SelectionMethod.fromString(requireValue(args, ++i, arg));
                } else if ("--compare".equals(arg)) {
                    result.compareSelections = true;
                } else if ("--no-viewer".equals(arg)) {
                    result.noViewer = true;
                } else if ("--play-best-latest".equals(arg)) {
                    result.playBestLatest = true;
                } else if ("--play-best".equals(arg)) {
                    result.playBestDirectory = requireValue(args, ++i, arg);
                } else if ("--video".equals(arg)) {
                    result.videoDirectory = requireValue(args, ++i, arg);
                } else if ("--video-step".equals(arg)) {
                    result.videoStep = parsePositiveInt(requireValue(args, ++i, arg), arg);
                } else if ("--runs".equals(arg)) {
                    result.runs = parsePositiveInt(requireValue(args, ++i, arg), arg);
                } else if ("--max-generations".equals(arg)) {
                    result.maxGenerations = parsePositiveInt(requireValue(args, ++i, arg), arg);
                } else if ("--patience".equals(arg)) {
                    result.patience = parsePositiveInt(requireValue(args, ++i, arg), arg);
                } else if ("--min-generations".equals(arg)) {
                    result.minimumGeneration = parseNonNegativeInt(requireValue(args, ++i, arg), arg);
                } else if ("--min-relative-improvement".equals(arg)) {
                    result.minRelativeImprovement = parseNonNegativeDouble(requireValue(args, ++i, arg), arg);
                } else if ("--save-every".equals(arg)) {
                    result.saveEvery = parsePositiveInt(requireValue(args, ++i, arg), arg);
                } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    result.help = true;
                } else {
                    throw new IllegalArgumentException("Argomento sconosciuto: " + arg);
                }
            }

            int exclusiveModes = 0;
            if (result.compareSelections) exclusiveModes++;
            if (result.videoDirectory != null) exclusiveModes++;
            if (result.playBestDirectory != null) exclusiveModes++;
            if (result.playBestLatest) exclusiveModes++;
            if (exclusiveModes > 1) {
                throw new IllegalArgumentException(
                        "Usa una sola modalita' tra --compare, --video, --play-best e --play-best-latest."
                );
            }

            if (result.minimumGeneration >= result.maxGenerations) {
                throw new IllegalArgumentException(
                        "--min-generations deve essere minore di --max-generations"
                );
            }

            return result;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Manca il valore dopo " + option);
            }
            return args[index];
        }

        private static int parsePositiveInt(String value, String option) {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(option + " deve essere > 0");
            }
            return parsed;
        }

        private static int parseNonNegativeInt(String value, String option) {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(option + " deve essere >= 0");
            }
            return parsed;
        }

        private static double parseNonNegativeDouble(String value, String option) {
            double parsed = Double.parseDouble(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(option + " deve essere >= 0");
            }
            return parsed;
        }
    }
}
