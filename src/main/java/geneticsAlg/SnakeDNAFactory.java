package geneticsAlg;

import java.util.Random;

import org.uncommons.watchmaker.framework.factories.AbstractCandidateFactory;

import gameEngine.AISnakeController;

public class SnakeDNAFactory extends AbstractCandidateFactory<double[]> {

    private final int dnaLength;

    public SnakeDNAFactory() {
        this.dnaLength = AISnakeController.getRequiredDNALength();
        System.out.println("DNA Length = " + dnaLength);
    }

    @Override
    public double[] generateRandomCandidate(Random random) {
        double[] dna = new double[dnaLength];

        for (int i = 0; i < dna.length; i++) {
            dna[i] = random.nextDouble() * 2.0 - 1.0;
        }

        return dna;
    }
}

