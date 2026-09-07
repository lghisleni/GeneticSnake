package geneticsAlg;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.uncommons.watchmaker.framework.EvolutionaryOperator;

public class SnakeDNAMutation implements EvolutionaryOperator<double[]> {

    private static final double MUTATION_RATE = 0.025;
    private static final double MUTATION_STRENGTH = 0.25;
    private static final double RANDOM_RESET_RATE = 0.001;

    @Override
    public List<double[]> apply(List<double[]> selectedCandidates, Random random) {
        List<double[]> mutated = new ArrayList<>(selectedCandidates.size());

        for (double[] dna : selectedCandidates) {
            double[] child = dna.clone();

            for (int i = 0; i < child.length; i++) {
                if (random.nextDouble() < RANDOM_RESET_RATE) {
                    child[i] = random.nextDouble() * 2.0 - 1.0;
                } else if (random.nextDouble() < MUTATION_RATE) {
                    child[i] += random.nextGaussian() * MUTATION_STRENGTH;
                    child[i] = clamp(child[i], -1.0, 1.0);
                }
            }

            mutated.add(child);
        }

        return mutated;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}