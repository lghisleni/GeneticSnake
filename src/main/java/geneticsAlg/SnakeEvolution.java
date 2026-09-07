package geneticsAlg;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.uncommons.watchmaker.framework.CandidateFactory;
import org.uncommons.watchmaker.framework.EvolutionEngine;
import org.uncommons.watchmaker.framework.EvolutionObserver;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;
import org.uncommons.watchmaker.framework.GenerationalEvolutionEngine;
import org.uncommons.watchmaker.framework.PopulationData;
import org.uncommons.watchmaker.framework.SelectionStrategy;
import org.uncommons.watchmaker.framework.operators.EvolutionPipeline;
import org.uncommons.watchmaker.framework.termination.GenerationCount;

import gameEngine.BestSnakeViewer;

public final class SnakeEvolution {

    public static final int POPULATION_SIZE = 70;
    public static final int ELITE_COUNT = 6;
    public static final int GENERATIONS = 100;
    public static final int PREVIEW_EVERY_GENERATIONS = 10;

    public static final int DEFAULT_COMPARISON_RUNS = 5;
    public static final int DEFAULT_MAX_GENERATIONS = 250;
    public static final int DEFAULT_EARLY_STOP_PATIENCE = 40;
    public static final int DEFAULT_EARLY_STOP_MIN_GENERATION = 60;
    public static final double DEFAULT_MIN_RELATIVE_IMPROVEMENT = 0.001; // 0.1%
    public static final int DEFAULT_SAVE_EVERY = 5;

    private static final long BASE_EVOLUTION_SEED = 0x51A7E123L;

    private static volatile double lastTrainingBestFitness = 0.0;
    private static volatile double lastTrainingMeanFitness = 0.0;
    private static volatile Path lastResultsDirectory;

    private SnakeEvolution() {
    }

    public static double[] train() {
        return train(SelectionMethod.TOURNAMENT, true);
    }

    public static double[] train(SelectionMethod selectionMethod, boolean showViewer) {
        System.out.println("\n=== GENETIC SNAKE TRAINING ===");
        System.out.println("Selection: " + selectionMethod.getDisplayName());
        System.out.println("Population: " + POPULATION_SIZE);
        System.out.println("Elite: " + ELITE_COUNT);
        System.out.println("Generations: " + GENERATIONS);

        Path sessionDirectory = TrainingResultSaver.createSessionDirectory(
                "training_" + selectionMethod.name().toLowerCase(Locale.ROOT)
        );
        Path runDirectory = sessionDirectory.resolve("run_01");
        lastResultsDirectory = sessionDirectory;

        BestSnakeViewer viewer = showViewer ? BestSnakeViewer.createOnEdt() : null;
        RunResult result;

        try (TrainingResultSaver saver = new TrainingResultSaver(
                runDirectory,
                selectionMethod,
                1,
                BASE_EVOLUTION_SEED,
                DEFAULT_SAVE_EVERY
        )) {
            result = runEvolution(
                    selectionMethod,
                    POPULATION_SIZE,
                    ELITE_COUNT,
                    GENERATIONS,
                    BASE_EVOLUTION_SEED,
                    null,
                    (data, bestDna) -> {
                        saver.saveGeneration(
                                data.generation,
                                data.bestFitness,
                                data.meanFitness,
                                bestDna
                        );

                        System.out.printf(
                                Locale.US,
                                "Generation: %3d | Best: %10.2f | Mean: %10.2f | Selection: %s%n",
                                data.generation,
                                data.bestFitness,
                                data.meanFitness,
                                selectionMethod.getDisplayName()
                        );

                        lastTrainingBestFitness = data.bestFitness;
                        lastTrainingMeanFitness = data.meanFitness;

                        if (viewer != null) {
                            viewer.recordGeneration(
                                    data.generation,
                                    data.bestFitness,
                                    data.meanFitness,
                                    selectionMethod.getDisplayName()
                            );
                        }

                        if (viewer != null
                                && (data.generation == 0
                                || data.generation % PREVIEW_EVERY_GENERATIONS == 0
                                || data.generation == GENERATIONS - 1)) {
                            viewer.playAndWait(
                                    bestDna,
                                    data.generation,
                                    data.bestFitness,
                                    data.meanFitness,
                                    selectionMethod.getDisplayName()
                            );
                        }
                    }
            );

            saver.finish(
                    result.terminationReason,
                    GENERATIONS,
                    0,
                    0,
                    0.0
            );
        }

        writeTrainingSessionSummary(sessionDirectory, selectionMethod, result);

        System.out.printf(
                Locale.US,
                "Training completato. Best fitness: %.2f (generazione %d)%n",
                result.bestEverFitness,
                result.bestEverGeneration
        );
        System.out.println("Risultati: " + sessionDirectory.toAbsolutePath());
        return result.bestEverDna.clone();
    }

    public static SelectionMethod compareSelections() {
        return compareSelections(ComparisonConfig.recommended());
    }

    public static SelectionMethod compareSelections(ComparisonConfig config) {
        config.validate();

        Path comparisonDirectory = TrainingResultSaver.createSessionDirectory("selection_comparison");
        lastResultsDirectory = comparisonDirectory;

        writeExperimentConfig(comparisonDirectory, config);

        Path detailedCsvPath = comparisonDirectory.resolve("selection_comparison.csv");
        Path runsCsvPath = comparisonDirectory.resolve("selection_runs_summary.csv");

        Map<SelectionMethod, List<RunResult>> allResults = new EnumMap<>(SelectionMethod.class);

        RunResult globalBest = null;
        SelectionMethod globalBestMethod = null;

        try (PrintWriter detailedCsv = new PrintWriter(Files.newBufferedWriter(
                detailedCsvPath,
                StandardCharsets.UTF_8
        ));
             PrintWriter runsCsv = new PrintWriter(Files.newBufferedWriter(
                     runsCsvPath,
                     StandardCharsets.UTF_8
             ))) {

            detailedCsv.println(
                    "selection,run,generation,best_fitness,mean_fitness,dna_file"
            );
            runsCsv.println(
                    "selection,run,seed,generations_executed,termination_reason,"
                    + "final_best_fitness,final_mean_fitness,best_ever_fitness,best_ever_generation"
            );

            for (SelectionMethod method : SelectionMethod.values()) {
                List<RunResult> methodResults = new ArrayList<>();
                allResults.put(method, methodResults);
                Path methodDirectory = comparisonDirectory.resolve(
                        method.name().toLowerCase(Locale.ROOT)
                );

                System.out.println("\n============================================================");
                System.out.println("SELECTION: " + method.getDisplayName());
                System.out.println(method.getDescription());
                System.out.println("Runs: " + config.runs);
                System.out.println("Max generations: " + config.maxGenerations);
                System.out.println("Early stop patience: " + config.patience);
                System.out.println("Minimum generation before stop: " + config.minimumGeneration);
                System.out.printf(
                        Locale.US,
                        "Minimum significant improvement: %.3f%%%n",
                        config.minRelativeImprovement * 100.0
                );
                System.out.println("Save snake every: " + config.saveEvery + " generations");
                System.out.println("============================================================");

                RunResult bestOfMethod = null;

                for (int run = 1; run <= config.runs; run++) {
                    final int runNumber = run;
                    long seed = BASE_EVOLUTION_SEED + runNumber * 10_000L;
                    Path runDirectory = methodDirectory.resolve(
                            String.format(Locale.US, "run_%02d", runNumber)
                    );

                    System.out.printf(
                            Locale.US,
                            "%n[%s] RUN %d/%d | seed=%d%n",
                            method.getDisplayName(),
                            runNumber,
                            config.runs,
                            seed
                    );

                    FitnessStagnationTermination stagnation =
                            new FitnessStagnationTermination(
                                    config.patience,
                                    config.minimumGeneration,
                                    config.minRelativeImprovement
                            );

                    RunResult result;

                    try (TrainingResultSaver saver = new TrainingResultSaver(
                            runDirectory,
                            method,
                            runNumber,
                            seed,
                            config.saveEvery
                    )) {
                        result = runEvolution(
                                method,
                                POPULATION_SIZE,
                                ELITE_COUNT,
                                config.maxGenerations,
                                seed,
                                stagnation,
                                (data, bestDna) -> {
                                    String dnaFile = saver.saveGeneration(
                                            data.generation,
                                            data.bestFitness,
                                            data.meanFitness,
                                            bestDna
                                    );

                                    String rootRelativeDna = dnaFile.isEmpty()
                                            ? ""
                                            : method.name().toLowerCase(Locale.ROOT)
                                                    + "/run_"
                                                    + String.format(Locale.US, "%02d", runNumber)
                                                    + "/"
                                                    + dnaFile;

                                    detailedCsv.printf(
                                            Locale.US,
                                            "%s,%d,%d,%.10f,%.10f,%s%n",
                                            method.name(),
                                            runNumber,
                                            data.generation,
                                            data.bestFitness,
                                            data.meanFitness,
                                            rootRelativeDna
                                    );
                                    detailedCsv.flush();

                                    if (data.generation == 0
                                            || data.generation % config.saveEvery == 0) {
                                        System.out.printf(
                                                Locale.US,
                                                "  gen %3d | best %11.2f | mean %11.2f%n",
                                                data.generation,
                                                data.bestFitness,
                                                data.meanFitness
                                        );
                                    }
                                }
                        );
                        result.runNumber = runNumber;

                        saver.finish(
                                result.terminationReason,
                                config.maxGenerations,
                                config.patience,
                                config.minimumGeneration,
                                config.minRelativeImprovement
                        );
                    }

                    methodResults.add(result);

                    runsCsv.printf(
                            Locale.US,
                            "%s,%d,%d,%d,%s,%.10f,%.10f,%.10f,%d%n",
                            method.name(),
                            runNumber,
                            seed,
                            result.generationsExecuted,
                            result.terminationReason,
                            result.finalBestFitness,
                            result.finalMeanFitness,
                            result.bestEverFitness,
                            result.bestEverGeneration
                    );
                    runsCsv.flush();

                    System.out.printf(
                            Locale.US,
                            "  -> fine run: %s | generations=%d | best-ever=%.2f @ gen %d%n",
                            result.terminationReason,
                            result.generationsExecuted,
                            result.bestEverFitness,
                            result.bestEverGeneration
                    );

                    if (bestOfMethod == null
                            || result.bestEverFitness > bestOfMethod.bestEverFitness) {
                        bestOfMethod = result;
                    }

                    if (globalBest == null
                            || result.bestEverFitness > globalBest.bestEverFitness) {
                        globalBest = result;
                        globalBestMethod = method;
                    }
                }

                if (bestOfMethod != null) {
                    TrainingResultSaver.writeDnaFile(
                            methodDirectory.resolve("best_method_overall.dna"),
                            bestOfMethod.bestEverDna,
                            method,
                            bestOfMethod.runNumber,
                            bestOfMethod.seed,
                            bestOfMethod.bestEverGeneration,
                            bestOfMethod.bestEverFitness,
                            bestOfMethod.bestEverMeanFitness
                    );
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Errore durante il salvataggio del confronto.", e);
        }

        List<SelectionSummary> ranking = buildRanking(allResults);
        ranking.sort(Comparator.comparingDouble(SelectionSummary::getAverageBestEver).reversed());

        writeComparisonSummary(comparisonDirectory, ranking);
        SelectionSummary winnerSummary = ranking.get(0);
        SelectionMethod winner = winnerSummary.method;
        writeBestSelectionFile(comparisonDirectory, winnerSummary, config);

        if (globalBest != null && globalBestMethod != null) {
            TrainingResultSaver.writeDnaFile(
                    comparisonDirectory.resolve("best_overall_all_methods.dna"),
                    globalBest.bestEverDna,
                    globalBestMethod,
                    globalBest.runNumber,
                    globalBest.seed,
                    globalBest.bestEverGeneration,
                    globalBest.bestEverFitness,
                    globalBest.bestEverMeanFitness
            );
        }

        System.out.println("\n================ FINAL RANKING ================");
        for (int i = 0; i < ranking.size(); i++) {
            SelectionSummary s = ranking.get(i);
            System.out.printf(
                    Locale.US,
                    "%d. %-30s avg best=%11.2f | sd=%10.2f | absolute=%11.2f | avg gens=%.1f%n",
                    i + 1,
                    s.method.getDisplayName(),
                    s.averageBestEver,
                    s.stdDevBestEver,
                    s.absoluteBest,
                    s.averageGenerations
            );
        }

        System.out.println("\nConfronto completato.");
        System.out.println("Risultati: " + comparisonDirectory.toAbsolutePath());
        System.out.println("Migliore metodo medio: " + winner.getDisplayName());
        if (globalBest != null && globalBestMethod != null) {
            System.out.printf(
                    Locale.US,
                    "Miglior snake assoluto: %s | run %d | gen %d | fitness %.2f%n",
                    globalBestMethod.getDisplayName(),
                    globalBest.runNumber,
                    globalBest.bestEverGeneration,
                    globalBest.bestEverFitness
            );
        }

        return winner;
    }

    private static RunResult runEvolution(
            SelectionMethod selectionMethod,
            int populationSize,
            int eliteCount,
            int maxGenerations,
            long rngSeed,
            FitnessStagnationTermination stagnation,
            GenerationListener listener
    ) {
        CandidateFactory<double[]> factory = new SnakeDNAFactory();
        List<EvolutionaryOperator<double[]>> operators = Arrays.asList(
                new SnakeDNACrossover(),
                new SnakeDNAMutation()
        );

        EvolutionaryOperator<double[]> pipeline = new EvolutionPipeline<>(operators);
        SnakeFitnessEvaluator fitness = new SnakeFitnessEvaluator();
        SelectionStrategy<Object> selection = selectionMethod.createStrategy();
        Random rng = new Random(rngSeed);

        EvolutionEngine<double[]> engine = new GenerationalEvolutionEngine<double[]>(
                factory,
                pipeline,
                fitness,
                selection,
                rng
        );

        final double[] finalBestFitness = {Double.NEGATIVE_INFINITY};
        final double[] finalMeanFitness = {0.0};
        final double[][] finalBestDna = {null};

        final double[] bestEverFitness = {Double.NEGATIVE_INFINITY};
        final double[] bestEverMeanFitness = {0.0};
        final double[][] bestEverDna = {null};
        final int[] bestEverGeneration = {-1};
        final int[] lastGeneration = {-1};

        EvolutionObserver<double[]> observer = new EvolutionObserver<double[]>() {
            @Override
            public void populationUpdate(PopulationData<? extends double[]> data) {
                double[] currentBestCandidate = data.getBestCandidate();
                double[] generationBest = currentBestCandidate.clone();
                double generationBestFitness = data.getBestCandidateFitness();
                double generationMeanFitness = data.getMeanFitness();
                int generation = data.getGenerationNumber();

                finalBestFitness[0] = generationBestFitness;
                finalMeanFitness[0] = generationMeanFitness;
                finalBestDna[0] = generationBest.clone();
                lastGeneration[0] = generation;

                if (generationBestFitness > bestEverFitness[0] || bestEverDna[0] == null) {
                    bestEverFitness[0] = generationBestFitness;
                    bestEverMeanFitness[0] = generationMeanFitness;
                    bestEverDna[0] = generationBest.clone();
                    bestEverGeneration[0] = generation;
                }

                if (listener != null) {
                    listener.onGeneration(
                            new GenerationData(
                                    generation,
                                    generationBestFitness,
                                    generationMeanFitness
                            ),
                            generationBest
                    );
                }
            }
        };

        engine.addEvolutionObserver(observer);

        double[] engineBest;
        if (stagnation == null) {
            engineBest = engine.evolve(
                    populationSize,
                    eliteCount,
                    new GenerationCount(maxGenerations)
            );
        } else {
            engineBest = engine.evolve(
                    populationSize,
                    eliteCount,
                    new GenerationCount(maxGenerations),
                    stagnation
            );
        }

        if (finalBestDna[0] == null) {
            finalBestDna[0] = engineBest.clone();
        }
        if (bestEverDna[0] == null) {
            bestEverDna[0] = engineBest.clone();
            bestEverFitness[0] = finalBestFitness[0];
            bestEverMeanFitness[0] = finalMeanFitness[0];
            bestEverGeneration[0] = Math.max(0, lastGeneration[0]);
        }

        String terminationReason = stagnation != null && stagnation.isTerminatedByStagnation()
                ? "STAGNATION"
                : "MAX_GENERATIONS";

        return new RunResult(
                selectionMethod,
                -1,
                rngSeed,
                finalBestDna[0],
                finalBestFitness[0],
                finalMeanFitness[0],
                bestEverDna[0],
                bestEverFitness[0],
                bestEverMeanFitness[0],
                bestEverGeneration[0],
                lastGeneration[0] + 1,
                terminationReason
        );
    }

    private static List<SelectionSummary> buildRanking(
            Map<SelectionMethod, List<RunResult>> allResults
    ) {
        List<SelectionSummary> summaries = new ArrayList<>();

        for (Map.Entry<SelectionMethod, List<RunResult>> entry : allResults.entrySet()) {
            List<RunResult> results = entry.getValue();
            List<Double> bestValues = new ArrayList<>();
            double absoluteBest = Double.NEGATIVE_INFINITY;
            double generationSum = 0.0;

            for (RunResult result : results) {
                bestValues.add(result.bestEverFitness);
                absoluteBest = Math.max(absoluteBest, result.bestEverFitness);
                generationSum += result.generationsExecuted;
            }

            double average = average(bestValues);
            double stdDev = sampleStdDev(bestValues, average);
            double median = median(bestValues);
            double avgGenerations = results.isEmpty() ? 0.0 : generationSum / results.size();

            summaries.add(new SelectionSummary(
                    entry.getKey(),
                    results.size(),
                    average,
                    stdDev,
                    median,
                    absoluteBest,
                    avgGenerations
            ));
        }
        return summaries;
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double sampleStdDev(List<Double> values, double average) {
        if (values.size() <= 1) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            double delta = value - average;
            sum += delta * delta;
        }
        return Math.sqrt(sum / (values.size() - 1));
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private static void writeExperimentConfig(Path directory, ComparisonConfig config) {
        Path path = directory.resolve("experiment_config.txt");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8
        ))) {
            out.println("selection_methods=" + SelectionMethod.values().length);
            out.println("runs_per_method=" + config.runs);
            out.println("population=" + POPULATION_SIZE);
            out.println("elite=" + ELITE_COUNT);
            out.println("max_generations=" + config.maxGenerations);
            out.println("early_stop_patience=" + config.patience);
            out.println("early_stop_min_generation=" + config.minimumGeneration);
            out.printf(Locale.US, "min_relative_improvement=%.10f%n", config.minRelativeImprovement);
            out.println("save_snake_every_generations=" + config.saveEvery);
            out.println("viewer=false");
            out.println("ranking_criterion=highest average best-ever fitness across runs");
            out.println("seed_policy=same run seed across all selection methods");
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare experiment_config.txt", e);
        }
    }

    private static void writeComparisonSummary(
            Path directory,
            List<SelectionSummary> ranking
    ) {
        Path path = directory.resolve("selection_comparison_summary.csv");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8
        ))) {
            out.println(
                    "rank,selection,runs,average_best_ever_fitness,stddev_best_ever_fitness,"
                    + "median_best_ever_fitness,absolute_best_fitness,average_generations_executed"
            );

            for (int i = 0; i < ranking.size(); i++) {
                SelectionSummary s = ranking.get(i);
                out.printf(
                        Locale.US,
                        "%d,%s,%d,%.10f,%.10f,%.10f,%.10f,%.4f%n",
                        i + 1,
                        s.method.name(),
                        s.runs,
                        s.averageBestEver,
                        s.stdDevBestEver,
                        s.medianBestEver,
                        s.absoluteBest,
                        s.averageGenerations
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare il riepilogo confronto.", e);
        }
    }

    private static void writeBestSelectionFile(
            Path directory,
            SelectionSummary winner,
            ComparisonConfig config
    ) {
        Path path = directory.resolve("best_selection.txt");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8
        ))) {
            out.println("best_selection=" + winner.method.getDisplayName());
            out.println("best_selection_id=" + winner.method.name());
            out.printf(Locale.US, "average_best_ever_fitness=%.10f%n", winner.averageBestEver);
            out.printf(Locale.US, "stddev_best_ever_fitness=%.10f%n", winner.stdDevBestEver);
            out.printf(Locale.US, "median_best_ever_fitness=%.10f%n", winner.medianBestEver);
            out.printf(Locale.US, "absolute_best_fitness=%.10f%n", winner.absoluteBest);
            out.println("criterion=highest average best-ever fitness across " + config.runs + " runs");
            out.println("max_generations=" + config.maxGenerations);
            out.println("early_stop_patience=" + config.patience);
            out.println("early_stop_min_generation=" + config.minimumGeneration);
            out.printf(Locale.US, "min_relative_improvement=%.10f%n", config.minRelativeImprovement);
            out.println("save_every=" + config.saveEvery);
            out.println("population=" + POPULATION_SIZE);
            out.println("elite=" + ELITE_COUNT);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare best_selection.txt", e);
        }
    }

    private static void writeTrainingSessionSummary(
            Path sessionDirectory,
            SelectionMethod method,
            RunResult result
    ) {
        Path path = sessionDirectory.resolve("training_summary.txt");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8
        ))) {
            out.println("selection=" + method.getDisplayName());
            out.println("selection_id=" + method.name());
            out.println("population=" + POPULATION_SIZE);
            out.println("elite=" + ELITE_COUNT);
            out.println("generations=" + GENERATIONS);
            out.printf(Locale.US, "final_best_fitness=%.10f%n", result.finalBestFitness);
            out.printf(Locale.US, "final_mean_fitness=%.10f%n", result.finalMeanFitness);
            out.printf(Locale.US, "best_ever_fitness=%.10f%n", result.bestEverFitness);
            out.println("best_ever_generation=" + result.bestEverGeneration);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare training_summary.txt", e);
        }
    }

    public static double getLastTrainingBestFitness() {
        return lastTrainingBestFitness;
    }

    public static double getLastTrainingMeanFitness() {
        return lastTrainingMeanFitness;
    }

    public static Path getLastResultsDirectory() {
        return lastResultsDirectory;
    }

    public static final class ComparisonConfig {
        public final int runs;
        public final int maxGenerations;
        public final int patience;
        public final int minimumGeneration;
        public final double minRelativeImprovement;
        public final int saveEvery;

        public ComparisonConfig(
                int runs,
                int maxGenerations,
                int patience,
                int minimumGeneration,
                double minRelativeImprovement,
                int saveEvery
        ) {
            this.runs = runs;
            this.maxGenerations = maxGenerations;
            this.patience = patience;
            this.minimumGeneration = minimumGeneration;
            this.minRelativeImprovement = minRelativeImprovement;
            this.saveEvery = saveEvery;
        }

        public static ComparisonConfig recommended() {
            return new ComparisonConfig(
                    DEFAULT_COMPARISON_RUNS,
                    DEFAULT_MAX_GENERATIONS,
                    DEFAULT_EARLY_STOP_PATIENCE,
                    DEFAULT_EARLY_STOP_MIN_GENERATION,
                    DEFAULT_MIN_RELATIVE_IMPROVEMENT,
                    DEFAULT_SAVE_EVERY
            );
        }

        public void validate() {
            if (runs <= 0) {
                throw new IllegalArgumentException("runs deve essere > 0");
            }
            if (maxGenerations <= 0) {
                throw new IllegalArgumentException("maxGenerations deve essere > 0");
            }
            if (patience <= 0) {
                throw new IllegalArgumentException("patience deve essere > 0");
            }
            if (minimumGeneration < 0 || minimumGeneration >= maxGenerations) {
                throw new IllegalArgumentException(
                        "minimumGeneration deve essere >= 0 e < maxGenerations"
                );
            }
            if (minRelativeImprovement < 0) {
                throw new IllegalArgumentException("minRelativeImprovement deve essere >= 0");
            }
            if (saveEvery <= 0) {
                throw new IllegalArgumentException("saveEvery deve essere > 0");
            }
        }
    }

    private interface GenerationListener {
        void onGeneration(GenerationData data, double[] bestDna);
    }

    private static final class GenerationData {
        final int generation;
        final double bestFitness;
        final double meanFitness;

        GenerationData(int generation, double bestFitness, double meanFitness) {
            this.generation = generation;
            this.bestFitness = bestFitness;
            this.meanFitness = meanFitness;
        }
    }

    private static final class RunResult {
        final SelectionMethod method;
        int runNumber;
        final long seed;
        final double[] finalBestDna;
        final double finalBestFitness;
        final double finalMeanFitness;
        final double[] bestEverDna;
        final double bestEverFitness;
        final double bestEverMeanFitness;
        final int bestEverGeneration;
        final int generationsExecuted;
        final String terminationReason;

        RunResult(
                SelectionMethod method,
                int runNumber,
                long seed,
                double[] finalBestDna,
                double finalBestFitness,
                double finalMeanFitness,
                double[] bestEverDna,
                double bestEverFitness,
                double bestEverMeanFitness,
                int bestEverGeneration,
                int generationsExecuted,
                String terminationReason
        ) {
            this.method = method;
            this.runNumber = runNumber;
            this.seed = seed;
            this.finalBestDna = finalBestDna.clone();
            this.finalBestFitness = finalBestFitness;
            this.finalMeanFitness = finalMeanFitness;
            this.bestEverDna = bestEverDna.clone();
            this.bestEverFitness = bestEverFitness;
            this.bestEverMeanFitness = bestEverMeanFitness;
            this.bestEverGeneration = bestEverGeneration;
            this.generationsExecuted = generationsExecuted;
            this.terminationReason = terminationReason;
        }
    }

    private static final class SelectionSummary {
        final SelectionMethod method;
        final int runs;
        final double averageBestEver;
        final double stdDevBestEver;
        final double medianBestEver;
        final double absoluteBest;
        final double averageGenerations;

        SelectionSummary(
                SelectionMethod method,
                int runs,
                double averageBestEver,
                double stdDevBestEver,
                double medianBestEver,
                double absoluteBest,
                double averageGenerations
        ) {
            this.method = method;
            this.runs = runs;
            this.averageBestEver = averageBestEver;
            this.stdDevBestEver = stdDevBestEver;
            this.medianBestEver = medianBestEver;
            this.absoluteBest = absoluteBest;
            this.averageGenerations = averageGenerations;
        }

        double getAverageBestEver() {
            return averageBestEver;
        }
    }
}
