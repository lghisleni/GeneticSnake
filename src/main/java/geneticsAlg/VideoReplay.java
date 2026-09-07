package geneticsAlg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import gameEngine.BestSnakeViewer;

public final class VideoReplay {

    public static final int DEFAULT_GENERATION_STEP = 5;

    private VideoReplay() {
    }

    public static void playBestLatest(String resultsDirectory) {
        Path root = Paths.get(resultsDirectory).toAbsolutePath().normalize();
        ensureDirectory(root);

        Path latest = null;
        long latestModified = Long.MIN_VALUE;

        try (java.util.stream.Stream<Path> stream = Files.list(root)) {
            for (Path candidate : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(candidate)) {
                    continue;
                }
                if (!candidate.getFileName().toString().startsWith("selection_comparison_")) {
                    continue;
                }
                if (!Files.isRegularFile(candidate.resolve("best_overall_all_methods.dna"))) {
                    continue;
                }

                long modified = Files.getLastModifiedTime(candidate).toMillis();
                if (latest == null || modified > latestModified) {
                    latest = candidate;
                    latestModified = modified;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile cercare l'ultimo benchmark in " + root,
                    e
            );
        }

        if (latest == null) {
            throw new IllegalStateException(
                    "Nessun benchmark completato con best_overall_all_methods.dna trovato in "
                            + root
            );
        }

        System.out.println("Ultimo benchmark trovato: " + latest);
        playBest(latest.toString());
    }

    public static void playBest(String comparisonDirectory) {
        if (comparisonDirectory == null || comparisonDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Manca la cartella del confronto.");
        }

        Path comparisonDir = Paths.get(comparisonDirectory).toAbsolutePath().normalize();
        ensureDirectory(comparisonDir);
        Path dnaPath = comparisonDir.resolve("best_overall_all_methods.dna");
        if (!Files.isRegularFile(dnaPath)) {
            throw new IllegalStateException("Miglior DNA assoluto non trovato: " + dnaPath.toAbsolutePath());
        }

        Map<String, String> metadata = readDnaMetadata(dnaPath);
        double[] dna = readDna(dnaPath);
        String selectionName = metadata.getOrDefault("selection", "Best overall");
        double sourceFitness = parseDoubleOrDefault(metadata.get("best_fitness"), 0.0);

        System.out.println("\n=== BEST DNA - NUOVA PARTITA ===");
        System.out.println("DNA caricato: " + dnaPath);
        System.out.println("Selection: " + selectionName);
        System.out.printf(Locale.US, "Fitness ottenuta nel training: %.2f%n", sourceFitness);
        System.out.println("Run e generazione originali: ignorate.");
        System.out.println("Creo un nuovo mondo casuale e applico il DNA allo snake nuovo.");

        BestSnakeViewer viewer = BestSnakeViewer.createOnEdt(
                "Genetic Snake - BEST DNA | NEW RUN | " + selectionName
        );
        viewer.playFreshBestAndWait(dna, sourceFitness, selectionName);
        System.out.println("Snake terminato. R riavvia questo mondo; rilancia PLAY per un nuovo seed.");
    }

    public static void play(String comparisonDirectory, int generationStep) {
        if (comparisonDirectory == null || comparisonDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Manca la cartella del confronto da riprodurre.");
        }

        if (generationStep <= 0) {
            throw new IllegalArgumentException("--video-step deve essere maggiore di 0.");
        }

        Path comparisonDir = Paths.get(comparisonDirectory).toAbsolutePath().normalize();
        ensureDirectory(comparisonDir);

        SelectionMethod winner = readWinningSelection(comparisonDir.resolve("best_selection.txt"));
        Path methodDirectory = comparisonDir.resolve(winner.name().toLowerCase(Locale.ROOT));
        ensureDirectory(methodDirectory);

        RunData bestRun = findBestRun(methodDirectory);
        List<GenerationData> history = readGenerationHistory(bestRun.runDirectory.resolve("generations.csv"));

        if (history.isEmpty()) {
            throw new IllegalStateException(
                    "Nessuna generazione trovata in "
                            + bestRun.runDirectory.resolve("generations.csv").toAbsolutePath()
            );
        }

        GenerationData bestGeneration = Collections.max(
                history,
                Comparator.comparingDouble(data -> data.bestFitness)
        );

        Set<Integer> selectedGenerations = buildVideoSequence(
                history.get(history.size() - 1).generation,
                generationStep,
                bestGeneration.generation
        );

        System.out.println("\n=== GENETIC SNAKE - VIDEO REPLAY ===");
        System.out.println("Comparison: " + comparisonDir);
        System.out.println("Best selection: " + winner.getDisplayName());
        System.out.println("Best run: " + bestRun.runNumber);
        System.out.printf(Locale.US, "Best run fitness: %.2f%n", bestRun.bestFitness);
        System.out.println("Video speed: " + BestSnakeViewer.PRESENTATION_SPEED + "x");
        System.out.println("Generations shown: " + selectedGenerations);
        System.out.println("Best generation automatically included: " + bestGeneration.generation);
        System.out.println();

        String title = "Genetic Snake - VIDEO | "
                + winner.getDisplayName()
                + " | Run " + bestRun.runNumber
                + " | " + formatSpeed(BestSnakeViewer.PRESENTATION_SPEED);

        BestSnakeViewer viewer = BestSnakeViewer.createOnEdt(title);

        int lastRecordedGeneration = -1;
        Map<Integer, GenerationData> byGeneration = new LinkedHashMap<>();
        for (GenerationData data : history) {
            byGeneration.put(data.generation, data);
        }

        for (Integer generation : selectedGenerations) {
            GenerationData target = byGeneration.get(generation);
            if (target == null) {
                continue;
            }

            for (int gen = lastRecordedGeneration + 1; gen <= generation; gen++) {
                GenerationData point = byGeneration.get(gen);
                if (point != null) {
                    viewer.recordGeneration(
                            point.generation,
                            point.bestFitness,
                            point.meanFitness,
                            winner.getDisplayName()
                    );
                }
            }
            lastRecordedGeneration = Math.max(lastRecordedGeneration, generation);

            Path dnaPath = resolveDnaPath(bestRun.runDirectory, target);
            double[] dna = readDna(dnaPath);

            System.out.printf(
                    Locale.US,
                    "VIDEO -> Gen %3d | Best %10.2f | Mean %10.2f | %s%n",
                    target.generation,
                    target.bestFitness,
                    target.meanFitness,
                    dnaPath
            );

            viewer.playAndWait(
                    dna,
                    target.generation,
                    target.bestFitness,
                    target.meanFitness,
                    winner.getDisplayName()
            );

            if (!viewer.isDisplayable()) {
                System.out.println("Viewer chiuso: replay interrotto.");
                return;
            }
        }

        System.out.println("\nVideo replay completato.");
        System.out.println("La finestra rimane aperta sull'ultimo snake mostrato.");
    }


    private static Path resolveDnaPath(Path runDirectory, GenerationData target) {
        if (target.dnaFile != null && !target.dnaFile.trim().isEmpty()) {
            Path fromCsv = runDirectory.resolve(target.dnaFile).normalize();
            if (Files.isRegularFile(fromCsv)) {
                return fromCsv;
            }
        }

        Path checkpoint = runDirectory.resolve(
                "best_snakes/generation_"
                        + String.format(Locale.US, "%03d", target.generation)
                        + ".dna"
        ).normalize();

        if (Files.isRegularFile(checkpoint)) {
            return checkpoint;
        }

        throw new IllegalStateException(
                "DNA non disponibile per la generazione " + target.generation
                        + " in " + runDirectory.toAbsolutePath()
        );
    }

    private static Set<Integer> buildVideoSequence(
            int lastGeneration,
            int generationStep,
            int bestGeneration
    ) {
        TreeSet<Integer> selected = new TreeSet<>();
        selected.add(0);

        for (int generation = generationStep;
                generation <= lastGeneration;
                generation += generationStep) {
            selected.add(generation);
        }

        selected.add(lastGeneration);
        selected.add(bestGeneration);
        return selected;
    }

    private static SelectionMethod readWinningSelection(Path path) {
        Map<String, String> values = readKeyValueFile(path);

        String id = values.get("best_selection_id");
        if (id != null) {
            try {
                return SelectionMethod.valueOf(id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }

        String displayName = values.get("best_selection");
        if (displayName != null) {
            return SelectionMethod.fromString(displayName);
        }

        throw new IllegalStateException(
                "best_selection.txt non contiene best_selection_id o best_selection: "
                        + path.toAbsolutePath()
        );
    }

    private static RunData findBestRun(Path methodDirectory) {
        List<RunData> runs = new ArrayList<>();

        try {
            Files.list(methodDirectory)
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("run_"))
                    .forEach(path -> {
                        Path csv = path.resolve("generations.csv");
                        if (!Files.isRegularFile(csv)) {
                            return;
                        }

                        List<GenerationData> history = readGenerationHistory(csv);
                        if (history.isEmpty()) {
                            return;
                        }

                        double best = history.stream()
                                .mapToDouble(data -> data.bestFitness)
                                .max()
                                .orElse(Double.NEGATIVE_INFINITY);

                        int runNumber = parseRunNumber(path.getFileName().toString());
                        runs.add(new RunData(runNumber, path, best));
                    });
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile leggere le run in " + methodDirectory.toAbsolutePath(),
                    e
            );
        }

        if (runs.isEmpty()) {
            throw new IllegalStateException(
                    "Nessuna run valida trovata in " + methodDirectory.toAbsolutePath()
            );
        }

        return Collections.max(runs, Comparator.comparingDouble(run -> run.bestFitness));
    }

    private static int parseRunNumber(String name) {
        String digits = name.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return -1;
        }
        return Integer.parseInt(digits);
    }

    private static List<GenerationData> readGenerationHistory(Path csvPath) {
        if (!Files.isRegularFile(csvPath)) {
            throw new IllegalStateException("File non trovato: " + csvPath.toAbsolutePath());
        }

        List<GenerationData> result = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length < 6) {
                    throw new IllegalStateException(
                            "Riga CSV non valida in " + csvPath + ": " + line
                    );
                }

                int generation = Integer.parseInt(parts[0].trim());
                double bestFitness = Double.parseDouble(parts[1].trim());
                double meanFitness = Double.parseDouble(parts[2].trim());
                String dnaFile = parts[5].trim();

                result.add(new GenerationData(
                        generation,
                        bestFitness,
                        meanFitness,
                        dnaFile
                ));
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile leggere " + csvPath.toAbsolutePath(),
                    e
            );
        }

        result.sort(Comparator.comparingInt(data -> data.generation));
        return result;
    }

    public static double[] readDna(Path dnaPath) {
        if (!Files.isRegularFile(dnaPath)) {
            throw new IllegalStateException("DNA non trovato: " + dnaPath.toAbsolutePath());
        }

        List<Double> genes = new ArrayList<>();
        Integer expectedLength = null;

        try {
            for (String rawLine : Files.readAllLines(dnaPath, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("#")) {
                    if (line.startsWith("# dna_length=")) {
                        expectedLength = Integer.parseInt(
                                line.substring("# dna_length=".length()).trim()
                        );
                    }
                    continue;
                }

                genes.add(Double.parseDouble(line));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere il DNA: " + dnaPath, e);
        }

        if (expectedLength != null && expectedLength.intValue() != genes.size()) {
            throw new IllegalStateException(
                    "DNA incompleto: attesi " + expectedLength
                            + " geni, trovati " + genes.size()
                            + " in " + dnaPath.toAbsolutePath()
            );
        }

        double[] dna = new double[genes.size()];
        for (int i = 0; i < genes.size(); i++) {
            dna[i] = genes.get(i);
        }
        return dna;
    }

    private static Map<String, String> readDnaMetadata(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (!line.startsWith("#")) {
                    continue;
                }

                String metadata = line.substring(1).trim();
                int separator = metadata.indexOf('=');
                if (separator <= 0) {
                    continue;
                }

                values.put(
                        metadata.substring(0, separator).trim(),
                        metadata.substring(separator + 1).trim()
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossibile leggere i metadati del DNA: " + path.toAbsolutePath(),
                    e
            );
        }
        return values;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static double parseDoubleOrDefault(String value, double defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static Map<String, String> readKeyValueFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("File non trovato: " + path.toAbsolutePath());
        }

        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }

                values.put(
                        line.substring(0, separator).trim(),
                        line.substring(separator + 1).trim()
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere " + path.toAbsolutePath(), e);
        }
        return values;
    }

    private static void ensureDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "Cartella risultati non trovata: " + path.toAbsolutePath()
            );
        }
    }

    private static String formatSpeed(double speed) {
        return String.format(Locale.US, "%.1fx", speed);
    }

    private static final class RunData {
        final int runNumber;
        final Path runDirectory;
        final double bestFitness;

        RunData(int runNumber, Path runDirectory, double bestFitness) {
            this.runNumber = runNumber;
            this.runDirectory = runDirectory;
            this.bestFitness = bestFitness;
        }
    }

    private static final class GenerationData {
        final int generation;
        final double bestFitness;
        final double meanFitness;
        final String dnaFile;

        GenerationData(
                int generation,
                double bestFitness,
                double meanFitness,
                String dnaFile
        ) {
            this.generation = generation;
            this.bestFitness = bestFitness;
            this.meanFitness = meanFitness;
            this.dnaFile = dnaFile;
        }
    }
}