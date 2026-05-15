package gameEngine;

import helpers.KeyboardListener;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;

public class GameLoop extends JPanel implements Runnable {

    public static final long UPDATE_PERIOD = 16;

    private static final int PANEL_WIDTH = World.DEFAULT_WIDTH;
    private static final int PANEL_HEIGHT = World.DEFAULT_HEIGHT;

    private final KeyboardListener keyboard;
    private final World world;
    private final Snake snake;

    private boolean paused;
    private boolean running;

    public GameLoop(KeyboardListener keyboard) {
        this.keyboard = keyboard;
        this.world = new World();
        this.snake = new Snake(world);
        this.paused = false;
        this.running = true;

        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);

        Thread gameThread = new Thread(this, "Snake-GameLoop");
        gameThread.start();
    }

    @Override
    public void run() {
        long lastUpdate = System.currentTimeMillis();

        while (running) {
            long now = System.currentTimeMillis();

            if (now - lastUpdate >= UPDATE_PERIOD) {
                updateGame();
                repaint();
                lastUpdate = now;
            }

            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private void updateGame() {
        handleInput();

        if (!paused && !snake.isDead()) {
            world.update(getWidth(), getHeight());
            snake.update(world, keyboard);
        }
    }

    private void handleInput() {
        if (keyboard.isKeyPressed(KeyEvent.VK_R)) {
            restart();
        }

        if (keyboard.isKeyPressed(KeyEvent.VK_P)) {
            paused = true;
        }

        if (keyboard.isKeyPressed(KeyEvent.VK_O)) {
            paused = false;
        }
    }

    private void restart() {
        world.reset();
        snake.reset(world);
        paused = false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        drawBackground(g);

        world.draw(g);
        snake.draw(g);

        drawHud(g);
        drawStateMessages(g);
    }

    private void drawBackground(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawHud(Graphics g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));

        g.drawString("Snake Score: " + (int) snake.getScore(), World.BORDER, 55);
        g.drawString("Health: " + (int) snake.getHealth(), World.BORDER + 180, 55);
        g.drawString("P = pausa | O = riprendi | R = reset", World.BORDER + 360, 55);
    }

    private void drawStateMessages(Graphics g) {
        if (paused) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 44));
            g.drawString("PAUSA", getWidth() / 2 - 75, getHeight() / 2);
        }

        if (snake.isDead()) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 44));
            g.drawString("GAME OVER", getWidth() / 2 - 140, getHeight() / 2);

            g.setFont(new Font("Arial", Font.PLAIN, 22));
            g.drawString("Premi R per ricominciare", getWidth() / 2 - 120, getHeight() / 2 + 40);
        }
    }
}