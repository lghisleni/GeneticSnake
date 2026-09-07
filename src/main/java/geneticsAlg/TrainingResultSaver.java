package geneticsAlg;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TrainingResultSaver implements AutoCloseable {

    private static final DateTimeFormatter SESSION_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Path runDirectory;
    private final Path bestSnakesDirectory;
    private final Path csvPath;
    private final PrintWriter csv;

    private final SelectionMethod selectionMethod;
    private final int runNumber;
    private final long seed;
    private final int saveEvery;

    private double bestEverFitness = Double.NEGATIVE_INFINITY;
    private double[] bestEverDna;
    private int bestEverGeneration = -1;
    private double bestEverMeanFitness = Double.NaN;

    private double finalBestFitness = Double.NaN;
    private double finalMeanFitness = Double.NaN;
    private double[] finalBestDna;
    private int lastGeneration = -1;

    public TrainingResultSaver(
            Path runDirectory,
            SelectionMethod selectionMethod,
            int runNumber,
            long seed,
            int saveEvery
    ) {
        if (saveEvery <= 0) {
            throw new IllegalArgumentException("saveEvery deve essere > 0");
        }

        this.runDirectory = runDirectory;
        this.bestSnakesDirectory = runDirectory.resolve("best_snakes");
        this.csvPath = runDirectory.resolve("generations.csv");
        this.selectionMethod = selectionMethod;
        this.runNumber = runNumber;
        this.seed = seed;
        this.saveEvery = saveEvery;

        try {
            Files.createDirectories(bestSnakesDirectory);
            this.csv = new PrintWriter(Files.newBufferedWriter(
                    csvPath,
                    StandardCharsets.UTF_8
            ));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile creare la cartella risultati: " + runDirectory.toAbsolutePath(),
                    e
            );
        }

        csv.println(
                "generation,best_fitness,mean_fitness,best_ever_fitness,best_ever_generation,"
                + "dna_file,is_checkpoint,is_new_record"
        );
        csv.flush();
        writeRunInfo();
    }

    public static Path createSessionDirectory(String prefix) {
        String timestamp = LocalDateTime.now().format(SESSION_FORMAT);
        Path directory = Paths.get("results", sanitize(prefix) + "_" + timestamp);

        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile creare la sessione risultati: " + directory.toAbsolutePath(),
                    e
            );
        }
        return directory;
    }

    public String saveGeneration(
            int generation,
            double bestFitness,
            double meanFitness,
            double[] bestDna
    ) {
        if (bestDna == null) {
            throw new IllegalArgumentException("Il DNA della generazione non puo' essere null.");
        }

        boolean newRecord = bestEverDna == null || bestFitness > bestEverFitness;
        if (newRecord) {
            bestEverFitness = bestFitness;
            bestEverDna = bestDna.clone();
            bestEverGeneration = generation;
            bestEverMeanFitness = meanFitness;

            writeDnaFile(
                    runDirectory.resolve("best_overall.dna"),
                    bestEverDna,
                    selectionMethod,
                    runNumber,
                    seed,
                    generation,
                    bestFitness,
                    meanFitness
            );
        }

        boolean periodicCheckpoint = generation == 0 || generation % saveEvery == 0;
        boolean saveGenerationDna = periodicCheckpoint;
        String relativeDnaFile = "";

        if (saveGenerationDna) {
            relativeDnaFile = checkpointRelativePath(generation);
            writeDnaFile(
                    runDirectory.resolve(relativeDnaFile),
                    bestDna,
                    selectionMethod,
                    runNumber,
                    seed,
                    generation,
                    bestFitness,
                    meanFitness
            );
        }

        finalBestFitness = bestFitness;
        finalMeanFitness = meanFitness;
        finalBestDna = bestDna.clone();
        lastGeneration = generation;

        csv.printf(
                Locale.US,
                "%d,%.10f,%.10f,%.10f,%d,%s,%s,%s%n",
                generation,
                bestFitness,
                meanFitness,
                bestEverFitness,
                bestEverGeneration,
                relativeDnaFile,
                periodicCheckpoint,
                newRecord
        );
        csv.flush();
        return relativeDnaFile;
    }

    public void saveFinalCheckpointIfNeeded() {
        if (lastGeneration < 0 || finalBestDna == null) {
            return;
        }

        Path finalCheckpoint = runDirectory.resolve(checkpointRelativePath(lastGeneration));
        if (!Files.exists(finalCheckpoint)) {
            writeDnaFile(
                    finalCheckpoint,
                    finalBestDna,
                    selectionMethod,
                    runNumber,
                    seed,
                    lastGeneration,
                    finalBestFitness,
                    finalMeanFitness
            );
        }

        if (bestEverDna != null && bestEverGeneration >= 0) {
            Path bestCheckpoint = runDirectory.resolve(checkpointRelativePath(bestEverGeneration));
            if (!Files.exists(bestCheckpoint)) {
                writeDnaFile(
                        bestCheckpoint,
                        bestEverDna,
                        selectionMethod,
                        runNumber,
                        seed,
                        bestEverGeneration,
                        bestEverFitness,
                        bestEverMeanFitness
                );
            }
        }
    }

    public void finish(
            String terminationReason,
            int configuredMaxGenerations,
            int patience,
            int minimumGeneration,
            double minRelativeImprovement
    ) {
        saveFinalCheckpointIfNeeded();

        Path summaryPath = runDirectory.resolve("summary.txt");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                summaryPath,
                StandardCharsets.UTF_8
        ))) {
            out.println("Genetic Snake - Run summary");
            out.println("selection=" + selectionMethod.getDisplayName());
            out.println("selection_id=" + selectionMethod.name());
            out.println("run=" + runNumber);
            out.println("seed=" + seed);
            out.println("last_generation=" + lastGeneration);
            out.println("generations_executed=" + (lastGeneration + 1));
            out.println("configured_max_generations=" + configuredMaxGenerations);
            out.println("termination_reason=" + terminationReason);
            out.println("early_stopping_patience=" + patience);
            out.println("early_stopping_min_generation=" + minimumGeneration);
            out.printf(Locale.US, "early_stopping_min_relative_improvement=%.10f%n", minRelativeImprovement);
            out.println("save_dna_every_generations=" + saveEvery);
            out.printf(Locale.US, "final_best_fitness=%.10f%n", finalBestFitness);
            out.printf(Locale.US, "final_mean_fitness=%.10f%n", finalMeanFitness);
            out.printf(Locale.US, "best_ever_fitness=%.10f%n", bestEverFitness);
            out.println("best_ever_generation=" + bestEverGeneration);
            out.println("best_overall_dna=best_overall.dna");
            out.println("generation_history=generations.csv");
            out.println("video_checkpoint_directory=best_snakes");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile scrivere il riepilogo: " + summaryPath.toAbsolutePath(),
                    e
            );
        }
    }

    private String checkpointRelativePath(int generation) {
        return "best_snakes/generation_"
                + String.format(Locale.US, "%03d", generation)
                + ".dna";
    }

    public Path getRunDirectory() {
        return runDirectory;
    }

    public Path getCsvPath() {
        return csvPath;
    }

    public double getBestEverFitness() {
        return bestEverFitness;
    }

    public double[] getBestEverDna() {
        return bestEverDna == null ? null : bestEverDna.clone();
    }

    public int getBestEverGeneration() {
        return bestEverGeneration;
    }

    public static void writeDnaFile(
            Path path,
            double[] dna,
            SelectionMethod selectionMethod,
            int runNumber,
            long seed,
            int generation,
            double bestFitness,
            double meanFitness
    ) {
        Path parent = path.getParent();

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempPath = path.resolveSibling(path.getFileName().toString() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(
                    tempPath,
                    StandardCharsets.UTF_8
            )) {
                writer.write("# GENETIC_SNAKE_DNA_V2");
                writer.newLine();
                writer.write("# selection=" + selectionMethod.getDisplayName());
                writer.newLine();
                writer.write("# selection_id=" + selectionMethod.name());
                writer.newLine();
                writer.write("# run=" + runNumber);
                writer.newLine();
                writer.write("# seed=" + seed);
                writer.newLine();
                writer.write("# generation=" + generation);
                writer.newLine();
                writer.write(String.format(Locale.US, "# best_fitness=%.10f", bestFitness));
                writer.newLine();
                writer.write(String.format(Locale.US, "# mean_fitness=%.10f", meanFitness));
                writer.newLine();
                writer.write("# dna_length=" + dna.length);
                writer.newLine();

                for (double gene : dna) {
                    writer.write(String.format(Locale.US, "%.17g", gene));
                    writer.newLine();
                }
            }

            try {
                Files.move(
                        tempPath,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException atomicMoveNotSupported) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile salvare il DNA: " + path.toAbsolutePath(),
                    e
            );
        }
    }

    private void writeRunInfo() {
        Path infoPath = runDirectory.resolve("run_info.txt");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                infoPath,
                StandardCharsets.UTF_8
        ))) {
            out.println("selection=" + selectionMethod.getDisplayName());
            out.println("selection_id=" + selectionMethod.name());
            out.println("run=" + runNumber);
            out.println("seed=" + seed);
            out.println("save_dna_every_generations=" + saveEvery);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile scrivere " + infoPath.toAbsolutePath(),
                    e
            );
        }
    }

    private static String sanitize(String value) {
        return value == null
                ? "run"
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9_-]+", "_")
                        .replaceAll("_+", "_")
                        .replaceAll("^_|_$", "");
    }

    @Override
    public void close() {
        csv.flush();
        csv.close();
    }
}
