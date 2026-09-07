package neuralNet;

import java.util.Arrays;
import java.util.Random;

public class NeuralNetwork {

    private final int[] layers;
    private final double[][] neurons;
    private final double[][] biases;
    private final double[][][] weights;

    private final Random random = new Random();

    public NeuralNetwork(int... layers) {
        if (layers == null || layers.length < 2) {
            throw new IllegalArgumentException("La rete deve avere almeno input e output layer.");
        }
        for (int layerSize : layers) {
            if (layerSize <= 0) {
                throw new IllegalArgumentException("Ogni layer deve avere almeno un neurone.");
            }
        }

        this.layers = Arrays.copyOf(layers, layers.length);
        this.neurons = new double[layers.length][];
        this.biases = new double[layers.length][];
        this.weights = new double[layers.length - 1][][];

        for (int i = 0; i < layers.length; i++) {
            neurons[i] = new double[layers[i]];
            biases[i] = new double[layers[i]];
        }

        for (int i = 0; i < layers.length - 1; i++) {
            weights[i] = new double[layers[i]][layers[i + 1]];
        }

        randomize();
    }

    private void randomize() {
        for (int layer = 1; layer < biases.length; layer++) {
            for (int i = 0; i < biases[layer].length; i++) {
                biases[layer][i] = randomWeight();
            }
        }

        for (int layer = 0; layer < weights.length; layer++) {
            for (int i = 0; i < weights[layer].length; i++) {
                for (int j = 0; j < weights[layer][i].length; j++) {
                    weights[layer][i][j] = randomWeight();
                }
            }
        }
    }

    public double[] predict(double[] inputs) {
        if (inputs == null || inputs.length != layers[0]) {
            throw new IllegalArgumentException(
                    "Input non valido: attesi " + layers[0] + " valori, ricevuti "
                            + (inputs == null ? 0 : inputs.length)
            );
        }

        System.arraycopy(inputs, 0, neurons[0], 0, inputs.length);

        for (int layer = 1; layer < layers.length; layer++) {
            for (int neuron = 0; neuron < layers[layer]; neuron++) {
                double sum = biases[layer][neuron];

                for (int prev = 0; prev < layers[layer - 1]; prev++) {
                    sum += neurons[layer - 1][prev] * weights[layer - 1][prev][neuron];
                }

                if (layer == layers.length - 1) {
                    neurons[layer][neuron] = sum;
                } else {
                    neurons[layer][neuron] = Math.tanh(sum);
                }
            }
        }

        double[] probabilities = softmax(neurons[layers.length - 1]);
        System.arraycopy(probabilities, 0, neurons[layers.length - 1], 0, probabilities.length);
        return probabilities;
    }

    private double[] softmax(double[] values) {
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            max = Math.max(max, value);
        }

        double sum = 0;
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = Math.exp(values[i] - max);
            sum += result[i];
        }

        if (sum == 0 || !Double.isFinite(sum)) {
            Arrays.fill(result, 1.0 / result.length);
            return result;
        }

        for (int i = 0; i < result.length; i++) {
            result[i] /= sum;
        }
        return result;
    }

    private double randomWeight() {
        return random.nextDouble() * 2.0 - 1.0;
    }

    public int getDNALength() {
        int total = 0;

        for (int layer = 1; layer < biases.length; layer++) {
            total += biases[layer].length;
        }

        for (double[][] layerWeights : weights) {
            total += layerWeights.length * layerWeights[0].length;
        }

        return total;
    }

    public void setDNA(double[] dna) {
        if (dna == null || dna.length != getDNALength()) {
            throw new IllegalArgumentException(
                    "DNA non valido: attesi " + getDNALength() + " geni, ricevuti "
                            + (dna == null ? 0 : dna.length)
            );
        }

        int index = 0;

        for (int layer = 1; layer < biases.length; layer++) {
            for (int i = 0; i < biases[layer].length; i++) {
                biases[layer][i] = dna[index++];
            }
        }

        for (int layer = 0; layer < weights.length; layer++) {
            for (int i = 0; i < weights[layer].length; i++) {
                for (int j = 0; j < weights[layer][i].length; j++) {
                    weights[layer][i][j] = dna[index++];
                }
            }
        }
    }

    public int[] getLayerSizes() {
        return Arrays.copyOf(layers, layers.length);
    }

    public double[][] getActivationsCopy() {
        double[][] copy = new double[neurons.length][];
        for (int i = 0; i < neurons.length; i++) {
            copy[i] = Arrays.copyOf(neurons[i], neurons[i].length);
        }
        return copy;
    }

    public double[][][] getWeightsCopy() {
        double[][][] copy = new double[weights.length][][];
        for (int layer = 0; layer < weights.length; layer++) {
            copy[layer] = new double[weights[layer].length][];
            for (int i = 0; i < weights[layer].length; i++) {
                copy[layer][i] = Arrays.copyOf(weights[layer][i], weights[layer][i].length);
            }
        }
        return copy;
    }
}