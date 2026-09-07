package geneticsAlg;

import org.uncommons.watchmaker.framework.PopulationData;
import org.uncommons.watchmaker.framework.TerminationCondition;

public final class FitnessStagnationTermination implements TerminationCondition {

    private final int patience;
    private final int minimumGeneration;
    private final double minRelativeImprovement;

    private double significantBest = Double.NEGATIVE_INFINITY;
    private int lastSignificantImprovementGeneration = -1;
    private boolean terminatedByStagnation = false;

    public FitnessStagnationTermination(
            int patience,
            int minimumGeneration,
            double minRelativeImprovement
    ) {
        if (patience <= 0) {
            throw new IllegalArgumentException("patience deve essere > 0");
        }
        if (minimumGeneration < 0) {
            throw new IllegalArgumentException("minimumGeneration deve essere >= 0");
        }
        if (minRelativeImprovement < 0) {
            throw new IllegalArgumentException("minRelativeImprovement deve essere >= 0");
        }

        this.patience = patience;
        this.minimumGeneration = minimumGeneration;
        this.minRelativeImprovement = minRelativeImprovement;
    }

    @Override
    public boolean shouldTerminate(PopulationData<?> populationData) {
        int generation = populationData.getGenerationNumber();
        double currentBest = populationData.getBestCandidateFitness();

        if (!Double.isFinite(significantBest)) {
            significantBest = currentBest;
            lastSignificantImprovementGeneration = generation;
            return false;
        }

        double requiredImprovement = Math.max(
                Math.abs(significantBest) * minRelativeImprovement,
                1e-9
        );

        if (currentBest > significantBest + requiredImprovement) {
            significantBest = currentBest;
            lastSignificantImprovementGeneration = generation;
        }

        if (generation < minimumGeneration) {
            return false;
        }

        int stagnantFor = generation - lastSignificantImprovementGeneration;
        terminatedByStagnation = stagnantFor >= patience;
        return terminatedByStagnation;
    }

    public int getPatience() {
        return patience;
    }

    public int getMinimumGeneration() {
        return minimumGeneration;
    }

    public double getMinRelativeImprovement() {
        return minRelativeImprovement;
    }

    public double getSignificantBest() {
        return significantBest;
    }

    public int getLastSignificantImprovementGeneration() {
        return lastSignificantImprovementGeneration;
    }

    public boolean isTerminatedByStagnation() {
        return terminatedByStagnation;
    }

    @Override
    public String toString() {
        return "FitnessStagnationTermination{"
                + "patience=" + patience
                + ", minimumGeneration=" + minimumGeneration
                + ", minRelativeImprovement=" + minRelativeImprovement
                + '}';
    }
}
