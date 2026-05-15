package gameEngine;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import helpers.KeyboardListener;

public class Snake {

    private static final double MAX_SPEED = 5.0;
    private static final double TURN_SPEED = Math.PI / 32.0;
    private static final double BODY_RADIUS = 18.0;
    private static final int INITIAL_LENGTH = 2;

    private static final double FOOD_COLLISION_THRESHOLD = -6.0;
    private static final double BODY_COLLISION_THRESHOLD = -4.0;
    private static final int SELF_COLLISION_START_INDEX = 8;

    private final List<PhysicalCircle> segments = new ArrayList<>();

    private double angle;
    private boolean dead;
    private double score;
    private double health;

    public Snake(World world) {
        reset(world);
    }

    public void reset(World world) {
        segments.clear();

        double startX = world.getWidth() / 2.0;
        double startY = world.getHeight() / 2.0;

        angle = 0;
        dead = false;
        score = 0;
        health = 100;

        for (int i = 0; i < INITIAL_LENGTH; i++) {
            segments.add(new PhysicalCircle(
                    startX - i * BODY_RADIUS * 2,
                    startY,
                    BODY_RADIUS
            ));
        }
    }

    public void update(World world, KeyboardListener keyboard) {
        if (dead) {
            return;
        }

        handleKeyboardInput(keyboard);
        move();
        eatFood(world);
        checkDeath(world);

        health -= 0.03;

        if (health <= 0) {
            dead = true;
        }
    }

    private void handleKeyboardInput(KeyboardListener keyboard) {
        if (keyboard.isKeyPressed(KeyEvent.VK_LEFT) || keyboard.isKeyPressed(KeyEvent.VK_A)) {
            angle -= TURN_SPEED;
        }

        if (keyboard.isKeyPressed(KeyEvent.VK_RIGHT) || keyboard.isKeyPressed(KeyEvent.VK_D)) {
            angle += TURN_SPEED;
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

    private void eatFood(World world) {
        List<PhysicalCircle> eatenFood = new ArrayList<>();

        for (PhysicalCircle food : world.getFoodList()) {
            if (getHead().isColliding(food, FOOD_COLLISION_THRESHOLD)) {
                eatenFood.add(food);

                int value = world.calculateFoodValue(food);
                score += value;
                world.addScore(value);

                health = Math.min(100, health + 20);
                grow();
            }
        }

        if (!eatenFood.isEmpty()) {
            world.removeFood(eatenFood);
            world.spawnFood(eatenFood.size());
        }
    }

    private void grow() {
        PhysicalCircle tail = segments.get(segments.size() - 1);

        segments.add(new PhysicalCircle(
                tail.x,
                tail.y,
                BODY_RADIUS
        ));
    }

    private void checkDeath(World world) {
        PhysicalCircle head = getHead();

        if (world.isOutsideBounds(head)) {
            dead = true;
            score *= 0.5;
            return;
        }

        for (int i = SELF_COLLISION_START_INDEX; i < segments.size(); i++) {
            if (head.isColliding(segments.get(i), BODY_COLLISION_THRESHOLD)) {
                dead = true;
                score *= 0.5;
                return;
            }
        }
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

    public void draw(Graphics g) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            PhysicalCircle segment = segments.get(i);

            if (i == 0) {
                g.setColor(new Color(40, 220, 90));
            } else {
                float brightness = 0.5f + (float) i / segments.size() * 0.4f;
                g.setColor(Color.getHSBColor(0.33f, 0.8f, brightness));
            }

            int x = (int) (segment.x - segment.radius);
            int y = (int) (segment.y - segment.radius);
            int size = (int) (segment.radius * 2);

            g.fillOval(x, y, size, size);
        }

        drawEyes(g);
    }

    private void drawEyes(Graphics g) {
        PhysicalCircle head = getHead();

        double eyeDistance = head.radius * 0.45;
        double eyeSize = head.radius * 0.22;

        double perpendicularX = -Math.sin(angle);
        double perpendicularY = Math.cos(angle);

        double frontX = Math.cos(angle) * head.radius * 0.35;
        double frontY = Math.sin(angle) * head.radius * 0.35;

        drawEye(g, head.x + frontX + perpendicularX * eyeDistance,
                head.y + frontY + perpendicularY * eyeDistance, eyeSize);

        drawEye(g, head.x + frontX - perpendicularX * eyeDistance,
                head.y + frontY - perpendicularY * eyeDistance, eyeSize);
    }

    private void drawEye(Graphics g, double x, double y, double size) {
        g.setColor(Color.WHITE);
        g.fillOval((int) (x - size), (int) (y - size), (int) (size * 2), (int) (size * 2));

        g.setColor(Color.BLACK);
        g.fillOval((int) (x - size / 2), (int) (y - size / 2), (int) size, (int) size);
    }
}