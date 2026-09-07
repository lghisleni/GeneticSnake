package gameEngine;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

public class Snake {

    public enum DeathReason {
        NONE,
        WALL,
        SELF_COLLISION,
        STARVATION
    }

    private static final double MAX_SPEED = 5.0;
    private static final double TURN_SPEED = Math.PI / 32.0;
    private static final double BODY_RADIUS = 18.0;
    private static final int INITIAL_LENGTH = 2;

    private static final double FOOD_COLLISION_THRESHOLD = -6.0;
    private static final double BODY_COLLISION_THRESHOLD = -4.0;
    private static final int SELF_COLLISION_START_INDEX = 2;

    public static final int MAX_STEPS_WITHOUT_FOOD = 900;
    private static final double MAX_HEALTH = 100.0;

    private volatile boolean showVision = true;
    private World world;

    private final List<PhysicalCircle> segments = new ArrayList<>();

    private double angle;
    private boolean dead;
    private double score;
    private double health;
    private int lifetime;
    private int foodsEaten;
    private int stepsSinceLastFood;
    private double navigationScore;
    private DeathReason deathReason;

    private Color headColor = new Color(140, 255, 160);
    private Color bodyColor = new Color(80, 255, 120);

    public Snake(World world) {
        reset(world);
    }

    public void reset(World world) {
        this.world = world;
        segments.clear();

        double startX = world.getWidth() / 2.0;
        double startY = world.getHeight() / 2.0;

        angle = 0;
        dead = false;
        score = 0;
        health = MAX_HEALTH;
        lifetime = 0;
        foodsEaten = 0;
        stepsSinceLastFood = 0;
        navigationScore = 0;
        deathReason = DeathReason.NONE;

        for (int i = 0; i < INITIAL_LENGTH; i++) {
            segments.add(new PhysicalCircle(
                    startX - i * BODY_RADIUS * 2,
                    startY,
                    BODY_RADIUS
            ));
        }
    }

    public void update(World world, SnakeController controller) {
        if (dead) {
            return;
        }

        int action = controller.decide(this, world);
        applyAction(action);
        move();

        boolean ateFood = eatFood(world);
        if (ateFood) {
            stepsSinceLastFood = 0;
        } else {
            stepsSinceLastFood++;
        }

        updateHealth();
        accumulateNavigationScore(world);
        lifetime++;
        checkDeath(world);
    }

    private void applyAction(int action) {
        if (action == SnakeController.TURN_LEFT) {
            angle -= TURN_SPEED;
        } else if (action == SnakeController.TURN_RIGHT) {
            angle += TURN_SPEED;
        }

        if (angle > Math.PI) {
            angle -= Math.PI * 2.0;
        } else if (angle < -Math.PI) {
            angle += Math.PI * 2.0;
        }
    }

    private void move() {
        double slowdown = 50.0 / (49.0 + segments.size());

        PhysicalCircle head = getHead();
        head.vx = Math.cos(angle) * MAX_SPEED * slowdown;
        head.vy = Math.sin(angle) * MAX_SPEED * slowdown;
        head.updatePosition();

        for (int i = 1; i < segments.size(); i++) {
            PhysicalCircle current = segments.get(i);
            PhysicalCircle previous = segments.get(i - 1);
            current.followStatic(previous);
        }
    }

    private boolean eatFood(World world) {
        List<PhysicalCircle> eatenFood = new ArrayList<>();

        for (PhysicalCircle food : world.getFoodList()) {
            if (getHead().isColliding(food, FOOD_COLLISION_THRESHOLD)) {
                eatenFood.add(food);

                int value = world.calculateFoodValue(food);
                score += value;
                world.addScore(value);
                foodsEaten++;
                grow();
            }
        }

        if (!eatenFood.isEmpty()) {
            world.removeFood(eatenFood);
            world.spawnFood(eatenFood.size());
            return true;
        }

        return false;
    }

    private void grow() {
        PhysicalCircle tail = segments.get(segments.size() - 1);
        segments.add(new PhysicalCircle(tail.x, tail.y, BODY_RADIUS));
    }

    private void updateHealth() {
        double ratio = 1.0 - (double) stepsSinceLastFood / MAX_STEPS_WITHOUT_FOOD;
        health = Math.max(0.0, MAX_HEALTH * ratio);
    }

    private void accumulateNavigationScore(World world) {
        double maxDistance = world.getPlayableDiagonal();
        if (maxDistance <= 0) {
            return;
        }

        double distance = world.distanceToClosestFood(getHead());
        double closeness = 1.0 - Math.min(1.0, distance / maxDistance);
        navigationScore += Math.max(0.0, closeness);
    }

    private void checkDeath(World world) {
        PhysicalCircle head = getHead();

        if (world.isOutsideBounds(head)) {
            die(DeathReason.WALL);
            return;
        }

        for (int i = SELF_COLLISION_START_INDEX; i < segments.size(); i++) {
            if (head.isColliding(segments.get(i), BODY_COLLISION_THRESHOLD)) {
                die(DeathReason.SELF_COLLISION);
                return;
            }
        }

        if (stepsSinceLastFood >= MAX_STEPS_WITHOUT_FOOD) {
            die(DeathReason.STARVATION);
        }
    }

    private void die(DeathReason reason) {
        dead = true;
        deathReason = reason;
    }


    public PhysicalCircle getHead() {
        return segments.get(0);
    }

    public List<PhysicalCircle> getSegments() {
        return segments;
    }

    public boolean isDead() {
        return dead;
    }

    public double getScore() {
        return score;
    }

    public double getHealth() {
        return health;
    }

    public double getAngle() {
        return angle;
    }

    public int getLifetime() {
        return lifetime;
    }

    public int getLength() {
        return segments.size();
    }

    public int getFoodsEaten() {
        return foodsEaten;
    }

    public int getStepsSinceLastFood() {
        return stepsSinceLastFood;
    }

    public double getNavigationScore() {
        return navigationScore;
    }

    public DeathReason getDeathReason() {
        return deathReason;
    }

    public void setShowVision(boolean showVision) {
        this.showVision = showVision;
    }

    public boolean isShowVision() {
        return showVision;
    }

    public void draw(Graphics g) {
        if (showVision) {
            drawVision(g);
        }

        for (int i = segments.size() - 1; i >= 0; i--) {
            PhysicalCircle segment = segments.get(i);

            int x = (int) (segment.x - segment.radius);
            int y = (int) (segment.y - segment.radius);
            int size = (int) (segment.radius * 2);

            g.setColor(new Color(80, 255, 120, 85));
            g.fillOval(x - 8, y - 8, size + 16, size + 16);

            if (i == 0) {
                g.setColor(headColor);
            } else {
                float brightness = 0.55f + (float) i / segments.size() * 0.4f;
                float[] hsb = Color.RGBtoHSB(
                        bodyColor.getRed(),
                        bodyColor.getGreen(),
                        bodyColor.getBlue(),
                        null
                );
                g.setColor(Color.getHSBColor(hsb[0], hsb[1], brightness));
            }

            g.fillOval(x, y, size, size);
            g.setColor(new Color(230, 255, 230, 150));
            g.drawOval(x, y, size, size);
        }

        drawEyes(g);
    }

    private void drawVision(Graphics g) {
        PhysicalCircle head = getHead();

        for (int ray = 0; ray < SnakeVision.RAY_COUNT; ray++) {
            double rayAngle = SnakeVision.getRayAngle(this, ray);
            int endX = (int) (head.x + Math.cos(rayAngle) * 130);
            int endY = (int) (head.y + Math.sin(rayAngle) * 130);

            g.setColor(new Color(80, 180, 255, 55));
            g.drawLine((int) head.x, (int) head.y, endX, endY);
        }
    }

    private void drawEyes(Graphics g) {
        PhysicalCircle head = getHead();

        double eyeDistance = head.radius * 0.45;
        double eyeSize = head.radius * 0.22;

        double perpendicularX = -Math.sin(angle);
        double perpendicularY = Math.cos(angle);
        double frontX = Math.cos(angle) * head.radius * 0.35;
        double frontY = Math.sin(angle) * head.radius * 0.35;

        drawEye(
                g,
                head.x + frontX + perpendicularX * eyeDistance,
                head.y + frontY + perpendicularY * eyeDistance,
                eyeSize
        );

        drawEye(
                g,
                head.x + frontX - perpendicularX * eyeDistance,
                head.y + frontY - perpendicularY * eyeDistance,
                eyeSize
        );
    }

    private void drawEye(Graphics g, double x, double y, double size) {
        g.setColor(Color.WHITE);
        g.fillOval(
                (int) (x - size),
                (int) (y - size),
                (int) (size * 2),
                (int) (size * 2)
        );

        g.setColor(Color.BLACK);
        g.fillOval(
                (int) (x - size / 2),
                (int) (y - size / 2),
                (int) size,
                (int) size
        );
    }

    public void setColorSeed(double[] dna) {
        int hash = 7;
        for (int i = 0; i < Math.min(50, dna.length); i++) {
            hash = 31 * hash + Double.valueOf(dna[i]).hashCode();
        }

        float hue = Math.abs(hash % 360) / 360f;
        headColor = Color.getHSBColor(hue, 0.8f, 1.0f);
        bodyColor = Color.getHSBColor(hue, 0.9f, 0.75f);
    }
}