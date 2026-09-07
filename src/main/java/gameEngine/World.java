package gameEngine;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World {

    public static final int DEFAULT_WIDTH = 1400;
    public static final int DEFAULT_HEIGHT = 900;
    public static final int BORDER = 100;

    public static final int INITIAL_FOOD = 6;
    public static final int MAX_FOOD = 12;
    public static final double FOOD_RADIUS = 12;
    public static final double FOOD_MAX_SPEED = 1.5;

    private final long seed;
    private final Random random;

    private int width;
    private int height;
    private long clock;
    private int score;

    private final List<PhysicalCircle> foodList = new ArrayList<>();

    public World() {
        this(System.nanoTime());
    }

    public World(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        this.width = DEFAULT_WIDTH;
        this.height = DEFAULT_HEIGHT;
        reset();
    }

    public void reset() {
        clock = 0;
        score = 0;
        foodList.clear();
        random.setSeed(seed);
        spawnFood(INITIAL_FOOD);
    }

    public void update(int width, int height) {
        this.width = Math.max(width, 2 * BORDER + 100);
        this.height = Math.max(height, 2 * BORDER + 100);

        for (PhysicalCircle food : foodList) {
            food.updatePosition();
            food.collideWall(BORDER, BORDER, this.width - BORDER, this.height - BORDER);
        }

        clock += GameLoop.UPDATE_PERIOD;
    }

    public void spawnFood(int amount) {
        for (int i = 0; i < amount && foodList.size() < MAX_FOOD; i++) {
            foodList.add(createRandomFood());
        }
    }

    private PhysicalCircle createRandomFood() {
        double usableWidth = Math.max(1, width - 2.0 * BORDER - 2.0 * FOOD_RADIUS);
        double usableHeight = Math.max(1, height - 2.0 * BORDER - 2.0 * FOOD_RADIUS);

        double x = BORDER + FOOD_RADIUS + random.nextDouble() * usableWidth;
        double y = BORDER + FOOD_RADIUS + random.nextDouble() * usableHeight;

        PhysicalCircle food = new PhysicalCircle(x, y, FOOD_RADIUS);
        food.vx = randomSpeed();
        food.vy = randomSpeed();
        return food;
    }

    private double randomSpeed() {
        return (random.nextDouble() * 2.0 - 1.0) * FOOD_MAX_SPEED;
    }

    public int calculateFoodValue(PhysicalCircle food) {
        return (int) Math.max(5, 15 - food.age / 200);
    }

    public void removeFood(List<PhysicalCircle> eatenFood) {
        foodList.removeAll(eatenFood);
    }

    public boolean isOutsideBounds(PhysicalCircle circle) {
        return circle.x - circle.radius < BORDER
                || circle.x + circle.radius > width - BORDER
                || circle.y - circle.radius < BORDER
                || circle.y + circle.radius > height - BORDER;
    }

    public double distanceToClosestFood(PhysicalCircle circle) {
        if (foodList.isEmpty()) {
            return getPlayableDiagonal();
        }

        double best = Double.POSITIVE_INFINITY;
        for (PhysicalCircle food : foodList) {
            best = Math.min(best, circle.distanceToCenter(food));
        }
        return best;
    }

    public double getPlayableDiagonal() {
        double playableWidth = Math.max(1, width - 2.0 * BORDER);
        double playableHeight = Math.max(1, height - 2.0 * BORDER);
        return Math.hypot(playableWidth, playableHeight);
    }

    public void addScore(int value) {
        score += value;
    }

    public List<PhysicalCircle> getFoodList() {
        return foodList;
    }

    public long getClock() {
        return clock;
    }

    public int getScore() {
        return score;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getSeed() {
        return seed;
    }

    public void draw(Graphics g) {
        drawBorder(g);
        drawFood(g);
    }

    private void drawBorder(Graphics g) {
        Graphics glow = g.create();
        Color snakeGreen = new Color(80, 255, 120);

        glow.setColor(new Color(80, 255, 120, 35));
        for (int i = 0; i < 14; i++) {
            glow.drawRect(
                    BORDER - i,
                    BORDER - i,
                    width - 2 * BORDER + i * 2,
                    height - 2 * BORDER + i * 2
            );
        }

        glow.setColor(snakeGreen);
        for (int i = 0; i < 5; i++) {
            glow.drawRect(
                    BORDER - i,
                    BORDER - i,
                    width - 2 * BORDER + i * 2,
                    height - 2 * BORDER + i * 2
            );
        }

        glow.dispose();
    }

    private void drawFood(Graphics g) {
        for (PhysicalCircle food : foodList) {
            int x = (int) (food.x - food.radius);
            int y = (int) (food.y - food.radius);
            int size = (int) (food.radius * 2);

            g.setColor(new Color(255, 80, 80, 90));
            g.fillOval(x - 5, y - 5, size + 10, size + 10);

            g.setColor(new Color(255, 60, 60));
            g.fillOval(x, y, size, size);

            g.setColor(new Color(255, 220, 220));
            g.fillOval(x + size / 4, y + size / 4, size / 4, size / 4);
        }
    }
}

