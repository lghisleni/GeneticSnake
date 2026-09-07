package geneticsAlg;

import java.util.List;

import org.uncommons.watchmaker.framework.FitnessEvaluator;

import gameEngine.AISnakeController;
import gameEngine.Snake;
import gameEngine.World;

public class SnakeFitnessEvaluator implements FitnessEvaluator<double[]> {

    private static final int MAX_STEPS = 2500;

    private static final long[] EVALUATION_SEEDS = {
            0x5EED_0001L,
            0x5EED_0002L
    };

    @Override
    public double getFitness(double[] dna, List<? extends double[]> population) {
        double totalFitness = 0.0;

        for (long seed : EVALUATION_SEEDS) {
            totalFitness += evaluateSingleRun(dna, seed);
        }

        return totalFitness / EVALUATION_SEEDS.length;
    }

    private double evaluateSingleRun(double[] dna, long seed) {
        World world = new World(seed);
        Snake snake = new Snake(world);
        AISnakeController controller = new AISnakeController(dna);

        for (int step = 0; step < MAX_STEPS && !snake.isDead(); step++) {
            world.update(World.DEFAULT_WIDTH, World.DEFAULT_HEIGHT);
            snake.update(world, controller);
        }

        double food = snake.getFoodsEaten();
        double valueScore = snake.getScore();
        double lifetime = snake.getLifetime();
        double averageCloseness = snake.getNavigationScore() / Math.max(1.0, lifetime);

        double fitness = 0.0;

        fitness += food * 1500.0;
        fitness += food * food * 250.0;
        fitness += valueScore * 20.0;

        fitness += lifetime * 0.30;
        fitness += averageCloseness * 250.0;

        if (snake.getDeathReason() == Snake.DeathReason.WALL
                || snake.getDeathReason() == Snake.DeathReason.SELF_COLLISION) {
            fitness *= 0.90;
        }

        return Math.max(0.0, fitness);
    }

    @Override
    public boolean isNatural() {
        return true;
    }
}
