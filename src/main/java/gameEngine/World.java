package gameEngine;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World {

    public static final int DEFAULT_WIDTH = 1000;
    public static final int DEFAULT_HEIGHT = 700;
    public static final int BORDER = 50;

    public static final int MAX_FOOD = 12;
    public static final double FOOD_RADIUS = 12;
    public static final double FOOD_MAX_SPEED = 1.5;

    private final Random random = new Random();

    private int width;
    private int height;
    private long clock;
    private int score;

    private final List<PhysicalCircle> foodList = new ArrayList<>();

    public World() {
        this.width = DEFAULT_WIDTH;
        this.height = DEFAULT_HEIGHT;
        reset();
    }

    public void reset() {
        clock = 0;
        score = 0;
        foodList.clear();
        spawnFood(4);
    }

    public void update(int width, int height) {
        this.width = width;
        this.height = height;

        for (PhysicalCircle food : foodList) {
            food.updatePosition();
            food.collideWall(BORDER, BORDER, width - BORDER, height - BORDER);
        }

        clock += GameLoop.UPDATE_PERIOD;
    }

    public void spawnFood(int amount) {
        for (int i = 0; i < amount && foodList.size() < MAX_FOOD; i++) {
            foodList.add(createRandomFood());
        }
    }

    private PhysicalCircle createRandomFood() {
        double x = BORDER + FOOD_RADIUS + random.nextDouble() * (width - 2 * BORDER - 2 * FOOD_RADIUS);
        double y = BORDER + FOOD_RADIUS + random.nextDouble() * (height - 2 * BORDER - 2 * FOOD_RADIUS);

        PhysicalCircle food = new PhysicalCircle(x, y, FOOD_RADIUS);

        food.vx = randomSpeed();
        food.vy = randomSpeed();

        return food;
    }

    private double randomSpeed() {
        return (random.nextDouble() * 2 - 1) * FOOD_MAX_SPEED;
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

    public void draw(Graphics g) {
        drawBorder(g);
        drawFood(g);
        drawHud(g);
    }

    private void drawBorder(Graphics g) {
        g.setColor(Color.DARK_GRAY);
        g.drawRect(BORDER, BORDER, width - 2 * BORDER, height - 2 * BORDER);
    }

    private void drawFood(Graphics g) {
        g.setColor(Color.RED);

        for (PhysicalCircle food : foodList) {
            int x = (int) (food.x - food.radius);
            int y = (int) (food.y - food.radius);
            int size = (int) (food.radius * 2);

            g.fillOval(x, y, size, size);
        }
    }

    private void drawHud(Graphics g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));

        g.drawString("Score: " + score, BORDER, 30);
        g.drawString("Time: " + clock / 1000 + "s", BORDER + 130, 30);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}