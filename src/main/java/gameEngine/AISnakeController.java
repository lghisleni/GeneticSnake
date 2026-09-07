package gameEngine;

import java.util.Arrays;

import neuralNet.NeuralNetwork;

public class AISnakeController implements SnakeController {

    private static final int[] NETWORK_LAYERS = {
            SnakeVision.INPUT_SIZE, 32, 16, 3
    };

    private final NeuralNetwork brain;
    private volatile double[] lastInputs = new double[SnakeVision.INPUT_SIZE];
    private volatile double[] lastOutputs = new double[3];

    public AISnakeController(double[] dna) {
        brain = createNetwork();
        brain.setDNA(dna);
    }

    public static NeuralNetwork createNetwork() {
        return new NeuralNetwork(NETWORK_LAYERS);
    }

    public static int getRequiredDNALength() {
        return createNetwork().getDNALength();
    }

    @Override
    public int decide(Snake snake, World world) {
        double[] inputs = SnakeVision.getVision(snake, world);
        double[] outputs = brain.predict(inputs);

        lastInputs = Arrays.copyOf(inputs, inputs.length);
        lastOutputs = Arrays.copyOf(outputs, outputs.length);

        int best = 0;
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > outputs[best]) {
                best = i;
            }
        }
        return best;
    }

    public NeuralNetwork getBrain() {
        return brain;
    }

    public double[] getLastInputs() {
        return Arrays.copyOf(lastInputs, lastInputs.length);
    }

    public double[] getLastOutputs() {
        return Arrays.copyOf(lastOutputs, lastOutputs.length);
    }
}