package geneticsAlg;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.uncommons.watchmaker.framework.EvolutionaryOperator;

public class SnakeDNACrossover implements EvolutionaryOperator<double[]> {

    private static final double CROSSOVER_PROBABILITY = 0.85;

    @Override
    public List<double[]> apply(List<double[]> selectedCandidates, Random random) {
        List<double[]> children = new ArrayList<>(selectedCandidates.size());

        for (int i = 0; i < selectedCandidates.size(); i += 2) {
            double[] parent1 = selectedCandidates.get(i);
            double[] parent2 = selectedCandidates.get((i + 1) % selectedCandidates.size());

            validateSameLength(parent1, parent2);

            double[] child1 = parent1.clone();
            double[] child2 = parent2.clone();

            if (parent1.length > 1 && random.nextDouble() < CROSSOVER_PROBABILITY) {
                int cut = 1 + random.nextInt(parent1.length - 1);

                for (int gene = cut; gene < parent1.length; gene++) {
                    child1[gene] = parent2[gene];
                    child2[gene] = parent1[gene];
                }
            }

            children.add(child1);
            if (children.size() < selectedCandidates.size()) {
                children.add(child2);
            }
        }

        return children;
    }

    private void validateSameLength(double[] parent1, double[] parent2) {
        if (parent1.length != parent2.length) {
            throw new IllegalArgumentException("I due DNA devono avere la stessa lunghezza.");
        }
    }
}
