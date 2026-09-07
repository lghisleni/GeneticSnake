package gameEngine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import helpers.KeyboardListener;

public class GameLoop extends JPanel implements Runnable {

    public static final long UPDATE_PERIOD = 16;

    private static final int ARENA_WIDTH = World.DEFAULT_WIDTH;
    private static final int ARENA_HEIGHT = World.DEFAULT_HEIGHT;
    private static final int ANALYTICS_WIDTH = 480;
    private static final int PANEL_WIDTH = ARENA_WIDTH + ANALYTICS_WIDTH;
    private static final int PANEL_HEIGHT = ARENA_HEIGHT;

    private static final Color GREEN = new Color(170, 255, 185);
    private static final Color GREEN_STRONG = new Color(70, 255, 115);
    private static final Color GREEN_BRIGHT = new Color(180, 255, 105);
    private static final Color BLUE = new Color(105, 190, 255);
    private static final Color RED = new Color(255, 92, 105);
    private static final Color YELLOW = new Color(255, 245, 125);
    private static final Color PANEL_BG = new Color(4, 13, 7);
    private static final Color PANEL_BORDER = new Color(80, 255, 120, 145);
    private static final Color GRID = new Color(130, 190, 145, 48);
    private static final Color MUTED = new Color(145, 195, 155);

    private volatile SnakeController controller;
    private final KeyboardListener keyboard;
    private volatile World world;
    private volatile Snake snake;

    private Image backgroundImage;

    private volatile boolean paused;
    private volatile boolean running;
    private volatile boolean showNetwork = true;

    private volatile double simulationSpeed = 1.0;

    private volatile int generation = 0;
    private volatile double bestFitness = 0;
    private volatile double meanFitness = 0;
    private volatile String selectionName = "-";
    private volatile boolean showcaseMode = false;
    private volatile double sourceTrainingFitness = 0.0;
    private final List<TrainingPoint> trainingHistory = new ArrayList<>();

    private volatile double renderScale = 1.0;
    private volatile double renderOffsetX = 0.0;
    private volatile double renderOffsetY = 0.0;

    private final ControlButton pauseButton = new ControlButton("pause", "P  PAUSA", 100, 68, 126, 25);
    private final ControlButton resumeButton = new ControlButton("resume", "O  RIPRENDI", 236, 68, 145, 25);
    private final ControlButton resetButton = new ControlButton("reset", "R  RESET", 391, 68, 124, 25);
    private final ControlButton visionButton = new ControlButton("vision", "V  RAGGI", 525, 68, 145, 25);
    private final ControlButton networkButton = new ControlButton("network", "N  RETE", 680, 68, 140, 25);

    private final ControlButton speed1Button = new ControlButton("speed1", "1X", 840, 68, 64, 25);
    private final ControlButton speed2Button = new ControlButton("speed2", "2X", 914, 68, 64, 25);
    private final ControlButton speed4Button = new ControlButton("speed4", "4X", 988, 68, 64, 25);
    private final ControlButton speed8Button = new ControlButton("speed8", "8X", 1062, 68, 64, 25);

    private final ControlButton[] controlButtons = {
            pauseButton, resumeButton, resetButton, visionButton, networkButton,
            speed1Button, speed2Button, speed4Button, speed8Button
    };

    public GameLoop(KeyboardListener keyboard, double[] dna) {
        this.keyboard = keyboard;
        this.world = new World();
        this.snake = new Snake(world);
        this.snake.setColorSeed(dna);
        this.controller = new AISnakeController(dna);
        this.paused = false;
        this.running = true;

        initPanel();
        startGameThread();
    }

    public GameLoop(Snake snake, SnakeController controller, World world) {
        this.keyboard = null;
        this.world = world;
        this.snake = snake;
        this.controller = controller;
        this.paused = false;
        this.running = true;

        initPanel();
        startGameThread();
    }

    private void initPanel() {
        loadBackgroundImage();

        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setMinimumSize(new Dimension(940, 500));
        setBackground(Color.BLACK);
        setFocusable(true);

        if (keyboard != null) {
            addKeyListener(keyboard);
        }

        installKeyBindings();
        installMouseControls();
    }

    private void installKeyBindings() {
        bindKey(KeyEvent.VK_P, "pause", () -> paused = true);
        bindKey(KeyEvent.VK_O, "resume", () -> paused = false);
        bindKey(KeyEvent.VK_R, "reset", this::restart);
        bindKey(KeyEvent.VK_V, "vision", this::toggleVision);
        bindKey(KeyEvent.VK_N, "network", () -> showNetwork = !showNetwork);

        // Cambio velocita' anche da tastiera: 1 / 2 / 4 / 8.
        bindKey(KeyEvent.VK_1, "speed1", () -> setSimulationSpeed(1.0));
        bindKey(KeyEvent.VK_2, "speed2", () -> setSimulationSpeed(2.0));
        bindKey(KeyEvent.VK_4, "speed4", () -> setSimulationSpeed(4.0));
        bindKey(KeyEvent.VK_8, "speed8", () -> setSimulationSpeed(8.0));
    }

    private void bindKey(int keyCode, String actionName, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0), actionName);

        getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
                repaint();
            }
        });
    }

    private void installMouseControls() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point virtual = toVirtualPoint(e.getX(), e.getY());
                if (virtual == null) {
                    return;
                }

                for (ControlButton button : controlButtons) {
                    if (button.bounds.contains(virtual)) {
                        executeControl(button.action);
                        repaint();
                        return;
                    }
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point virtual = toVirtualPoint(e.getX(), e.getY());
                boolean overButton = false;

                if (virtual != null) {
                    for (ControlButton button : controlButtons) {
                        if (button.bounds.contains(virtual)) {
                            overButton = true;
                            break;
                        }
                    }
                }

                setCursor(Cursor.getPredefinedCursor(
                        overButton ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR
                ));
            }
        });
    }

    private Point toVirtualPoint(int x, int y) {
        double scale = renderScale;
        if (scale <= 0) {
            return null;
        }

        double virtualX = (x - renderOffsetX) / scale;
        double virtualY = (y - renderOffsetY) / scale;

        if (virtualX < 0 || virtualY < 0 || virtualX >= PANEL_WIDTH || virtualY >= PANEL_HEIGHT) {
            return null;
        }

        return new Point((int) virtualX, (int) virtualY);
    }

    private void executeControl(String action) {
        switch (action) {
            case "pause":
                paused = true;
                break;
            case "resume":
                paused = false;
                break;
            case "reset":
                restart();
                break;
            case "vision":
                toggleVision();
                break;
            case "network":
                showNetwork = !showNetwork;
                break;
            case "speed1":
                setSimulationSpeed(1.0);
                break;
            case "speed2":
                setSimulationSpeed(2.0);
                break;
            case "speed4":
                setSimulationSpeed(4.0);
                break;
            case "speed8":
                setSimulationSpeed(8.0);
                break;
            default:
                break;
        }
    }

    private void toggleVision() {
        Snake currentSnake = snake;
        if (currentSnake != null) {
            currentSnake.setShowVision(!currentSnake.isShowVision());
        }
    }

    private void startGameThread() {
        Thread gameThread = new Thread(this, "Snake-GameLoop");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    public void replaceSnake(Snake snake, SnakeController controller, World world) {
        this.snake = snake;
        this.controller = controller;
        this.world = world;
        this.paused = false;
        repaint();
    }

    public void setSimulationSpeed(double simulationSpeed) {
        if (!Double.isFinite(simulationSpeed) || simulationSpeed <= 0.0) {
            throw new IllegalArgumentException("La velocita' deve essere > 0.");
        }
        this.simulationSpeed = Math.min(simulationSpeed, 8.0);
    }

    public double getSimulationSpeed() {
        return simulationSpeed;
    }

    public void setTrainingInfo(
            int generation,
            double bestFitness,
            double meanFitness,
            String selectionName
    ) {
        this.showcaseMode = false;
        this.generation = generation;
        this.bestFitness = bestFitness;
        this.meanFitness = meanFitness;
        this.selectionName = selectionName == null ? "-" : selectionName;

        synchronized (trainingHistory) {
            if (trainingHistory.isEmpty()
                    || trainingHistory.get(trainingHistory.size() - 1).generation != generation) {
                trainingHistory.add(new TrainingPoint(generation, bestFitness, meanFitness));
            }
        }
    }

    public void setShowcaseInfo(double sourceTrainingFitness, String selectionName) {
        this.showcaseMode = true;
        this.sourceTrainingFitness = sourceTrainingFitness;
        this.selectionName = selectionName == null ? "Best DNA" : selectionName;
        this.generation = 0;
        this.bestFitness = 0.0;
        this.meanFitness = 0.0;
        synchronized (trainingHistory) {
            trainingHistory.clear();
        }
        repaint();
    }

    private void loadBackgroundImage() {
        try (InputStream input = getClass().getResourceAsStream("/images/background.png")) {
            if (input != null) {
                backgroundImage = ImageIO.read(input);
            }
        } catch (IOException e) {
            System.err.println("Errore caricamento background: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double accumulatedSimulationMillis = 0.0;

        while (running) {
            long now = System.nanoTime();
            double elapsedMillis = (now - lastTime) / 1_000_000.0;
            lastTime = now;

            elapsedMillis = Math.min(elapsedMillis, 100.0);
            accumulatedSimulationMillis += elapsedMillis * simulationSpeed;

            int updates = 0;
            while (accumulatedSimulationMillis >= UPDATE_PERIOD && updates < 12) {
                updateGame();
                accumulatedSimulationMillis -= UPDATE_PERIOD;
                updates++;
            }

            if (updates > 0) {
                repaint();
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
        Snake currentSnake = snake;
        World currentWorld = world;
        SnakeController currentController = controller;

        if (!paused && currentSnake != null && currentWorld != null
                && currentController != null && !currentSnake.isDead()) {
            currentWorld.update(ARENA_WIDTH, ARENA_HEIGHT);
            currentSnake.update(currentWorld, currentController);
        }
    }

    private void restart() {
        World currentWorld = world;
        Snake currentSnake = snake;

        if (currentWorld != null && currentSnake != null) {
            currentWorld.reset();
            currentSnake.reset(currentWorld);
        }

        if (keyboard != null) {
            keyboard.clear();
        }
        paused = false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D screen = (Graphics2D) g.create();
        screen.setColor(Color.BLACK);
        screen.fillRect(0, 0, getWidth(), getHeight());

        double scaleX = getWidth() / (double) PANEL_WIDTH;
        double scaleY = getHeight() / (double) PANEL_HEIGHT;
        double scale = Math.min(scaleX, scaleY);

        double scaledWidth = PANEL_WIDTH * scale;
        double scaledHeight = PANEL_HEIGHT * scale;
        double offsetX = (getWidth() - scaledWidth) / 2.0;
        double offsetY = (getHeight() - scaledHeight) / 2.0;

        renderScale = scale;
        renderOffsetX = offsetX;
        renderOffsetY = offsetY;

        screen.translate(offsetX, offsetY);
        screen.scale(scale, scale);
        screen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        screen.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        screen.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        drawBackground(screen);

        World currentWorld = world;
        Snake currentSnake = snake;
        if (currentWorld != null) {
            currentWorld.draw(screen);
        }
        if (currentSnake != null) {
            currentSnake.draw(screen);
        }

        drawHud(screen);
        drawAnalyticsSidebar(screen);
        drawStateMessages(screen);

        screen.dispose();
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        int fieldX = World.BORDER;
        int fieldY = World.BORDER;
        int fieldWidth = ARENA_WIDTH - 2 * World.BORDER;
        int fieldHeight = ARENA_HEIGHT - 2 * World.BORDER;

        if (backgroundImage != null) {
            g.drawImage(backgroundImage, fieldX, fieldY, fieldWidth, fieldHeight, null);
            g.setColor(new Color(0, 0, 0, 130));
            g.fillRect(fieldX, fieldY, fieldWidth, fieldHeight);
        } else {
            g.setColor(new Color(5, 12, 8));
            g.fillRect(fieldX, fieldY, fieldWidth, fieldHeight);
        }

        g.setColor(new Color(2, 8, 4));
        g.fillRect(ARENA_WIDTH, 0, ANALYTICS_WIDTH, PANEL_HEIGHT);
        g.setColor(new Color(80, 255, 120, 90));
        g.fillRect(ARENA_WIDTH, 0, 2, PANEL_HEIGHT);
    }

    private void drawHud(Graphics2D g) {
        Snake currentSnake = snake;
        World currentWorld = world;
        if (currentSnake == null || currentWorld == null) {
            return;
        }

        Font arcadeFont = new Font("Monospaced", Font.BOLD, 18);
        g.setFont(arcadeFont);

        drawGlowString(g, "SCORE: " + currentWorld.getScore(), World.BORDER, 29);
        drawGlowString(g, "FOOD: " + currentSnake.getFoodsEaten(), World.BORDER + 215, 29);
        drawGlowString(g, "HEALTH: " + (int) currentSnake.getHealth(), World.BORDER + 405, 29);
        drawGlowString(g, "LIFETIME: " + currentSnake.getLifetime(), World.BORDER + 630, 29);

        if (showcaseMode) {
            drawGlowString(g, "MODE: NEW RUN", World.BORDER, 56);
            drawGlowString(g, "DNA FITNESS: " + formatCompact(sourceTrainingFitness), World.BORDER + 235, 56);
            drawGlowString(g, "SELECTION: " + selectionName, World.BORDER + 555, 56);
        } else {
            drawGlowString(g, "GEN: " + generation, World.BORDER, 56);
            drawGlowString(g, "BEST: " + formatCompact(bestFitness), World.BORDER + 180, 56);
            drawGlowString(g, "MEAN: " + formatCompact(meanFitness), World.BORDER + 405, 56);
            drawGlowString(g, "SELECTION: " + selectionName, World.BORDER + 630, 56);
        }

        drawControlButtons(g);
    }

    private void drawControlButtons(Graphics2D g) {
        drawControlButton(g, pauseButton, paused);
        drawControlButton(g, resumeButton, !paused);
        drawControlButton(g, resetButton, false);
        drawControlButton(g, visionButton, snake != null && snake.isShowVision());
        drawControlButton(g, networkButton, showNetwork);

        drawControlButton(g, speed1Button, isSpeedSelected(1.0));
        drawControlButton(g, speed2Button, isSpeedSelected(2.0));
        drawControlButton(g, speed4Button, isSpeedSelected(4.0));
        drawControlButton(g, speed8Button, isSpeedSelected(8.0));
    }

    private boolean isSpeedSelected(double speed) {
        return Math.abs(simulationSpeed - speed) < 0.001;
    }

    private void drawControlButton(Graphics2D g, ControlButton button, boolean active) {
        Rectangle r = button.bounds;

        if (active) {
            g.setColor(new Color(60, 220, 100, 70));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setColor(new Color(110, 255, 145));
        } else {
            g.setColor(new Color(20, 50, 28, 205));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setColor(new Color(95, 190, 115));
        }

        g.setStroke(new BasicStroke(active ? 1.8f : 1.1f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);

        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        int textWidth = g.getFontMetrics().stringWidth(button.label);
        g.setColor(active ? GREEN_BRIGHT : new Color(185, 225, 193));
        g.drawString(button.label, r.x + (r.width - textWidth) / 2, r.y + 17);
    }

    private void drawGlowString(Graphics2D g, String text, int x, int y) {
        g.setColor(new Color(80, 255, 120, 70));
        g.drawString(text, x + 2, y + 2);
        g.setColor(GREEN);
        g.drawString(text, x, y);
    }

    private void drawAnalyticsSidebar(Graphics2D g) {
        int x = ARENA_WIDTH + 14;
        int width = ANALYTICS_WIDTH - 28;

        g.setFont(new Font("Monospaced", Font.BOLD, 19));
        g.setColor(GREEN);
        g.drawString(showcaseMode ? "BEST DNA - NEW RUN" : "TRAINING ANALYTICS", x + 4, 27);

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(170, 220, 180));
        String sidebarInfo = showcaseMode
                ? selectionName + "   |   NEW WORLD   |   SPEED "
                        + String.format(Locale.US, "%.1fx", simulationSpeed)
                : "GEN " + generation + "   |   " + selectionName
                        + "   |   SPEED " + String.format(Locale.US, "%.1fx", simulationSpeed);
        g.drawString(sidebarInfo, x + 4, 47);

        if (controller instanceof AISnakeController) {
            if (showNetwork) {
                drawNeuralNetwork(g, (AISnakeController) controller, x, 60, width, 500);
            } else {
                drawHiddenNetworkPanel(g, x, 60, width, 500);
            }
        }

        if (showcaseMode) {
            drawFreshRunStatus(g, x, 574, width, 306);
        } else {
            drawFitnessChart(g, x, 574, width, 306);
        }
    }

    private void drawFreshRunStatus(Graphics2D g, int x, int y, int panelWidth, int panelHeight) {
        drawPanel(g, x, y, panelWidth, panelHeight);
        Snake currentSnake = snake;
        World currentWorld = world;
        if (currentSnake == null || currentWorld == null) return;

        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        g.setColor(GREEN);
        g.drawString("FRESH RUN STATUS", x + 18, y + 28);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(MUTED);
        g.drawString("Same trained DNA, new environment", x + 18, y + 50);

        int labelX = x + 22;
        int valueX = x + 235;
        int rowY = y + 82;
        int gap = 30;
        drawStatusRow(g, "TRAINING FITNESS", formatCompact(sourceTrainingFitness), labelX, valueX, rowY);
        drawStatusRow(g, "CURRENT SCORE", String.valueOf(currentWorld.getScore()), labelX, valueX, rowY + gap);
        drawStatusRow(g, "FOOD EATEN", String.valueOf(currentSnake.getFoodsEaten()), labelX, valueX, rowY + gap * 2);
        drawStatusRow(g, "LIFETIME", String.valueOf(currentSnake.getLifetime()), labelX, valueX, rowY + gap * 3);
        drawStatusRow(g, "HEALTH", String.format(Locale.US, "%.0f", currentSnake.getHealth()), labelX, valueX, rowY + gap * 4);
        drawStatusRow(g, "WORLD SEED", String.valueOf(currentWorld.getSeed()), labelX, valueX, rowY + gap * 5);

        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.setColor(GREEN_BRIGHT);
        g.drawString("No benchmark run is being replayed.", x + 22, y + panelHeight - 29);
        g.setColor(MUTED);
        g.drawString("R restarts this world; relaunch PLAY for a new seed.", x + 22, y + panelHeight - 11);
    }

    private void drawStatusRow(Graphics2D g, String label, String value, int labelX, int valueX, int y) {
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(MUTED);
        g.drawString(label, labelX, y);
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.setColor(GREEN_BRIGHT);
        g.drawString(value, valueX, y);
    }

    private void drawPanel(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(PANEL_BG);
        g.fillRoundRect(x, y, width, height, 18, 18);
        g.setStroke(new BasicStroke(2f));
        g.setColor(PANEL_BORDER);
        g.drawRoundRect(x, y, width, height, 18, 18);
    }

    private void drawHiddenNetworkPanel(Graphics2D g, int x, int y, int width, int height) {
        drawPanel(g, x, y, width, height);
        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        g.setColor(GREEN);
        g.drawString("NEURAL NETWORK", x + 18, y + 29);

        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(170, 220, 180));
        g.drawString("Rete nascosta", x + 18, y + 60);
        g.drawString("Premi N o clicca 'N RETE' per mostrarla", x + 18, y + 84);
    }

    private void drawNeuralNetwork(
            Graphics2D g,
            AISnakeController ai,
            int x,
            int y,
            int panelWidth,
            int panelHeight
    ) {
        drawPanel(g, x, y, panelWidth, panelHeight);

        int[] layerSizes = ai.getBrain().getLayerSizes();
        double[][] activations = ai.getBrain().getActivationsCopy();
        double[][][] weights = ai.getBrain().getWeightsCopy();
        double[] outputs = ai.getLastOutputs();
        int chosenAction = indexOfMax(outputs);

        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        g.setColor(GREEN);
        g.drawString("NEURAL NETWORK", x + 18, y + 27);

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(175, 225, 185));
        g.drawString(layerSizes[0] + " -> " + layerSizes[1] + " -> "
                + layerSizes[2] + " -> " + layerSizes[3], x + 18, y + 48);

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(GREEN_STRONG);
        g.drawString("VERDE = eccitazione / peso +", x + 18, y + 68);
        g.setColor(RED);
        g.drawString("ROSSO = inibizione / peso -", x + 230, y + 68);

        int graphLeft = x + 48;
        int graphTop = y + 112;
        int graphWidth = panelWidth - 155;
        int graphHeight = 235;

        int layerCount = layerSizes.length;
        int[] visibleCounts = {
                Math.min(layerSizes[0], 12),
                Math.min(layerSizes[1], 10),
                Math.min(layerSizes[2], 8),
                layerSizes[3]
        };

        int[][] sampledIndices = new int[layerCount][];
        int[][] nodeX = new int[layerCount][];
        int[][] nodeY = new int[layerCount][];

        for (int layer = 0; layer < layerCount; layer++) {
            int count = visibleCounts[layer];
            sampledIndices[layer] = sampleIndices(layerSizes[layer], count);
            nodeX[layer] = new int[count];
            nodeY[layer] = new int[count];

            int cx = graphLeft + (int) Math.round(layer * graphWidth / (double) (layerCount - 1));
            for (int i = 0; i < count; i++) {
                nodeX[layer][i] = cx;
                nodeY[layer][i] = graphTop + (count == 1
                        ? graphHeight / 2
                        : (int) Math.round(i * graphHeight / (double) (count - 1)));
            }
        }

        for (int layer = 0; layer < layerCount - 1; layer++) {
            drawStrongestConnections(
                    g,
                    layer,
                    sampledIndices,
                    nodeX,
                    nodeY,
                    visibleCounts,
                    activations,
                    weights,
                    3
            );
        }

        String[] layerLabels = {"INPUT", "HIDDEN 1", "HIDDEN 2", "OUTPUT"};

        for (int layer = 0; layer < layerCount; layer++) {
            int centerX = nodeX[layer][0];

            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(new Color(200, 245, 208));
            drawCenteredString(g, layerLabels[layer], centerX, graphTop - 24);

            g.setFont(new Font("Monospaced", Font.PLAIN, 9));
            g.setColor(MUTED);
            drawCenteredString(g, String.valueOf(layerSizes[layer]), centerX, graphTop - 10);

            for (int i = 0; i < visibleCounts[layer]; i++) {
                int sourceIndex = sampledIndices[layer][i];
                double activation = activations[layer][sourceIndex];
                boolean selectedOutput = layer == layerCount - 1 && sourceIndex == chosenAction;

                drawNeuron(
                        g,
                        nodeX[layer][i],
                        nodeY[layer][i],
                        activation,
                        layer,
                        layerCount,
                        selectedOutput
                );
            }
        }

        String[] outputNames = {"STRAIGHT", "LEFT", "RIGHT"};
        int outputLayer = layerCount - 1;
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        for (int i = 0; i < visibleCounts[outputLayer]; i++) {
            int actualIndex = sampledIndices[outputLayer][i];
            g.setColor(actualIndex == chosenAction ? YELLOW : new Color(185, 230, 195));
            g.drawString(outputNames[actualIndex], nodeX[outputLayer][i] + 15, nodeY[outputLayer][i] + 3);
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(MUTED);
        g.drawString("Visualizzati " + visibleCounts[0] + "/" + layerSizes[0]
                + " input; collegamenti = segnali piu' forti", x + 18, y + 376);

        String actionName = chosenAction >= 0 && chosenAction < outputNames.length
                ? outputNames[chosenAction]
                : "-";
        double actionProbability = chosenAction >= 0 && chosenAction < outputs.length
                ? outputs[chosenAction]
                : 0.0;

        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.setColor(YELLOW);
        g.drawString("DECISIONE: " + actionName + "  "
                + String.format(Locale.US, "%.0f%%", actionProbability * 100.0), x + 18, y + 401);

        drawOutputProbabilities(g, outputs, chosenAction, x + 18, y + 420, panelWidth - 36);
    }

    private void drawStrongestConnections(
            Graphics2D g,
            int layer,
            int[][] sampledIndices,
            int[][] nodeX,
            int[][] nodeY,
            int[] visibleCounts,
            double[][] activations,
            double[][][] weights,
            int connectionsPerTarget
    ) {
        Stroke oldStroke = g.getStroke();

        for (int targetVisible = 0; targetVisible < visibleCounts[layer + 1]; targetVisible++) {
            int targetIndex = sampledIndices[layer + 1][targetVisible];
            boolean[] used = new boolean[visibleCounts[layer]];

            for (int rank = 0; rank < Math.min(connectionsPerTarget, visibleCounts[layer]); rank++) {
                int bestSourceVisible = -1;
                double bestSignal = -1;

                for (int sourceVisible = 0; sourceVisible < visibleCounts[layer]; sourceVisible++) {
                    if (used[sourceVisible]) {
                        continue;
                    }

                    int sourceIndex = sampledIndices[layer][sourceVisible];
                    double weight = weights[layer][sourceIndex][targetIndex];
                    double sourceStrength = activationStrength(activations[layer][sourceIndex], layer,
                            activations.length);
                    double targetStrength = activationStrength(activations[layer + 1][targetIndex], layer + 1,
                            activations.length);

                    double signal = Math.min(1.0, Math.abs(weight))
                            * (0.18 + 0.82 * sourceStrength)
                            * (0.35 + 0.65 * targetStrength);

                    if (signal > bestSignal) {
                        bestSignal = signal;
                        bestSourceVisible = sourceVisible;
                    }
                }

                if (bestSourceVisible < 0) {
                    continue;
                }

                used[bestSourceVisible] = true;
                int sourceIndex = sampledIndices[layer][bestSourceVisible];
                double weight = weights[layer][sourceIndex][targetIndex];
                double signal = Math.max(0.0, Math.min(1.0, bestSignal));

                int alpha = 55 + (int) Math.round(signal * 190.0);
                Color color = weight >= 0
                        ? new Color(70, 255, 115, alpha)
                        : new Color(255, 80, 95, alpha);

                float thickness = (float) (0.8 + 2.6 * signal);
                g.setColor(color);
                g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(
                        nodeX[layer][bestSourceVisible],
                        nodeY[layer][bestSourceVisible],
                        nodeX[layer + 1][targetVisible],
                        nodeY[layer + 1][targetVisible]
                );
            }
        }

        g.setStroke(oldStroke);
    }

    private void drawNeuron(
            Graphics2D g,
            int x,
            int y,
            double activation,
            int layer,
            int layerCount,
            boolean selectedOutput
    ) {
        double strength = activationStrength(activation, layer, layerCount);
        Color activeColor = colorForActivation(activation, layer, layerCount);

        int baseRadius = selectedOutput ? 8 : 7;
        int glowRadius = baseRadius + 5 + (int) Math.round(7 * strength);

        if (strength > 0.05 || selectedOutput) {
            for (int i = 3; i >= 1; i--) {
                int r = glowRadius + i * 3;
                int alpha = (int) Math.round((18 + strength * 32) / i);
                Color glow = new Color(
                        activeColor.getRed(),
                        activeColor.getGreen(),
                        activeColor.getBlue(),
                        Math.min(100, alpha)
                );
                g.setColor(glow);
                g.fillOval(x - r, y - r, r * 2, r * 2);
            }
        }

        g.setColor(new Color(3, 8, 5));
        g.fillOval(x - baseRadius - 2, y - baseRadius - 2,
                (baseRadius + 2) * 2, (baseRadius + 2) * 2);

        g.setColor(activeColor);
        g.fillOval(x - baseRadius, y - baseRadius, baseRadius * 2, baseRadius * 2);

        if (selectedOutput) {
            g.setStroke(new BasicStroke(2.2f));
            g.setColor(YELLOW);
            g.drawOval(x - baseRadius - 3, y - baseRadius - 3,
                    (baseRadius + 3) * 2, (baseRadius + 3) * 2);
        } else {
            g.setStroke(new BasicStroke(1.1f));
            g.setColor(new Color(220, 255, 225, 150));
            g.drawOval(x - baseRadius, y - baseRadius, baseRadius * 2, baseRadius * 2);
        }
    }

    private double activationStrength(double activation, int layer, int layerCount) {
        if (layer == 0 || layer == layerCount - 1) {
            return Math.max(0.0, Math.min(1.0, activation));
        }
        return Math.max(0.0, Math.min(1.0, Math.abs(activation)));
    }

    private Color colorForActivation(double activation, int layer, int layerCount) {
        double strength = activationStrength(activation, layer, layerCount);

        if (strength < 0.04) {
            return new Color(48, 84, 62);
        }

        if (layer > 0 && layer < layerCount - 1 && activation < 0) {
            return interpolate(new Color(105, 55, 63), new Color(255, 80, 95), strength);
        }

        return interpolate(new Color(50, 105, 67), new Color(125, 255, 100), strength);
    }

    private Color interpolate(Color low, Color high, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(low.getRed() + (high.getRed() - low.getRed()) * t);
        int g = (int) Math.round(low.getGreen() + (high.getGreen() - low.getGreen()) * t);
        int b = (int) Math.round(low.getBlue() + (high.getBlue() - low.getBlue()) * t);
        return new Color(r, g, b);
    }

    private int indexOfMax(double[] values) {
        if (values == null || values.length == 0) {
            return -1;
        }

        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private int[] sampleIndices(int totalCount, int visibleCount) {
        int[] indices = new int[visibleCount];
        if (visibleCount == 1) {
            indices[0] = 0;
            return indices;
        }

        for (int i = 0; i < visibleCount; i++) {
            indices[i] = (int) Math.round(i * (totalCount - 1.0) / (visibleCount - 1.0));
        }
        return indices;
    }

    private void drawOutputProbabilities(
            Graphics2D g,
            double[] outputs,
            int chosenAction,
            int x,
            int y,
            int width
    ) {
        String[] labels = {"STRAIGHT", "LEFT", "RIGHT"};

        int labelWidth = 82;
        int barX = x + labelWidth;
        int percentWidth = 45;
        int barWidth = Math.max(30, width - labelWidth - percentWidth - 10);

        for (int i = 0; i < Math.min(outputs.length, labels.length); i++) {
            int rowY = y + i * 21;
            double value = Math.max(0.0, Math.min(1.0, outputs[i]));
            boolean selected = i == chosenAction;

            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(selected ? YELLOW : new Color(190, 235, 198));
            g.drawString((selected ? "> " : "  ") + labels[i], x, rowY + 11);

            g.setColor(new Color(80, 255, 120, 35));
            g.fillRoundRect(barX, rowY, barWidth, 12, 6, 6);

            g.setColor(selected ? GREEN_BRIGHT : GREEN_STRONG);
            g.fillRoundRect(barX, rowY, (int) Math.round(barWidth * value), 12, 6, 6);

            if (selected) {
                g.setColor(new Color(255, 245, 125, 180));
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(barX, rowY, barWidth, 12, 6, 6);
            }

            g.setColor(selected ? YELLOW : new Color(215, 250, 220));
            g.drawString(String.format(Locale.US, "%3.0f%%", value * 100.0),
                    barX + barWidth + 7, rowY + 11);
        }
    }

    private void drawFitnessChart(Graphics2D g, int x, int y, int panelWidth, int panelHeight) {
        drawPanel(g, x, y, panelWidth, panelHeight);

        List<TrainingPoint> history;
        synchronized (trainingHistory) {
            history = new ArrayList<>(trainingHistory);
        }

        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        g.setColor(GREEN);
        g.drawString("FITNESS EVOLUTION", x + 18, y + 27);

        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.setColor(GREEN_BRIGHT);
        g.drawString("BEST " + formatCompact(bestFitness), x + 18, y + 49);
        g.setColor(BLUE);
        g.drawString("MEAN " + formatCompact(meanFitness), x + 155, y + 49);
        g.setColor(new Color(185, 220, 190));
        g.drawString("GAP " + formatCompact(Math.max(0, bestFitness - meanFitness)), x + 292, y + 49);

        g.setStroke(new BasicStroke(3f));
        g.setColor(GREEN_BRIGHT);
        g.drawLine(x + 19, y + 67, x + 46, y + 67);
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.drawString("best", x + 52, y + 70);

        g.setColor(BLUE);
        g.drawLine(x + 105, y + 67, x + 132, y + 67);
        g.drawString("mean", x + 138, y + 70);

        int left = x + 58;
        int top = y + 82;
        int chartWidth = panelWidth - 82;
        int chartHeight = panelHeight - 128;
        int bottom = top + chartHeight;

        double maxFitness = 1.0;
        int maxGeneration = 1;
        for (TrainingPoint point : history) {
            maxFitness = Math.max(maxFitness, Math.max(point.bestFitness, point.meanFitness));
            maxGeneration = Math.max(maxGeneration, point.generation);
        }
        maxFitness = niceCeiling(maxFitness);

        g.setFont(new Font("Monospaced", Font.PLAIN, 9));

        for (int i = 0; i <= 4; i++) {
            int gy = top + i * chartHeight / 4;
            double value = maxFitness * (4 - i) / 4.0;

            g.setColor(GRID);
            g.drawLine(left, gy, left + chartWidth, gy);

            g.setColor(MUTED);
            String text = formatCompact(value);
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, left - tw - 7, gy + 3);
        }

        for (int i = 0; i <= 4; i++) {
            int gx = left + i * chartWidth / 4;
            int value = (int) Math.round(maxGeneration * i / 4.0);

            g.setColor(GRID);
            g.drawLine(gx, top, gx, bottom);

            g.setColor(MUTED);
            String text = String.valueOf(value);
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, gx - tw / 2, bottom + 15);
        }

        g.setColor(new Color(170, 225, 180, 130));
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(left, bottom, left + chartWidth, bottom);
        g.drawLine(left, top, left, bottom);

        if (!history.isEmpty()) {
            drawFitnessSeries(g, history, true, left, top, chartWidth, chartHeight,
                    maxGeneration, maxFitness);
            drawFitnessSeries(g, history, false, left, top, chartWidth, chartHeight,
                    maxGeneration, maxFitness);
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(MUTED);
        String generationLabel = "GENERATION";
        drawCenteredString(g, generationLabel, left + chartWidth / 2, y + panelHeight - 8);
    }

    private void drawFitnessSeries(
            Graphics2D g,
            List<TrainingPoint> history,
            boolean bestSeries,
            int left,
            int top,
            int chartWidth,
            int chartHeight,
            int maxGeneration,
            double maxFitness
    ) {
        if (history.isEmpty()) {
            return;
        }

        Color mainColor = bestSeries ? GREEN_BRIGHT : BLUE;
        Color glowColor = bestSeries
                ? new Color(80, 255, 120, 80)
                : new Color(105, 190, 255, 70);

        Stroke oldStroke = g.getStroke();

        g.setColor(glowColor);
        g.setStroke(new BasicStroke(bestSeries ? 7f : 6f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawSeriesPath(g, history, bestSeries, left, top, chartWidth, chartHeight,
                maxGeneration, maxFitness);

        g.setColor(mainColor);
        g.setStroke(new BasicStroke(bestSeries ? 3.2f : 2.6f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawSeriesPath(g, history, bestSeries, left, top, chartWidth, chartHeight,
                maxGeneration, maxFitness);

        TrainingPoint last = history.get(history.size() - 1);
        double lastFitness = bestSeries ? last.bestFitness : last.meanFitness;
        int px = left + (int) Math.round(last.generation * chartWidth / (double) maxGeneration);
        int py = top + chartHeight - (int) Math.round(lastFitness * chartHeight / maxFitness);

        g.setColor(mainColor);
        g.fillOval(px - 5, py - 5, 10, 10);
        g.setColor(new Color(240, 255, 242));
        g.fillOval(px - 2, py - 2, 4, 4);

        g.setStroke(oldStroke);
    }

    private void drawSeriesPath(
            Graphics2D g,
            List<TrainingPoint> history,
            boolean bestSeries,
            int left,
            int top,
            int chartWidth,
            int chartHeight,
            int maxGeneration,
            double maxFitness
    ) {
        TrainingPoint previous = null;
        for (TrainingPoint point : history) {
            if (previous != null) {
                double previousFitness = bestSeries ? previous.bestFitness : previous.meanFitness;
                double currentFitness = bestSeries ? point.bestFitness : point.meanFitness;

                int x1 = left + (int) Math.round(previous.generation * chartWidth / (double) maxGeneration);
                int x2 = left + (int) Math.round(point.generation * chartWidth / (double) maxGeneration);
                int y1 = top + chartHeight - (int) Math.round(previousFitness * chartHeight / maxFitness);
                int y2 = top + chartHeight - (int) Math.round(currentFitness * chartHeight / maxFitness);

                g.drawLine(x1, y1, x2, y2);
            }
            previous = point;
        }
    }

    private void drawCenteredString(Graphics2D g, String text, int centerX, int baselineY) {
        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, centerX - width / 2, baselineY);
    }

    private double niceCeiling(double value) {
        if (value <= 1.0) {
            return 1.0;
        }

        double magnitude = Math.pow(10.0, Math.floor(Math.log10(value)));
        double normalized = value / magnitude;
        double nice;

        if (normalized <= 1.0) {
            nice = 1.0;
        } else if (normalized <= 2.0) {
            nice = 2.0;
        } else if (normalized <= 5.0) {
            nice = 5.0;
        } else {
            nice = 10.0;
        }
        return nice * magnitude;
    }

    private String formatCompact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000.0) {
            return String.format(Locale.US, "%.2fM", value / 1_000_000.0);
        }
        if (abs >= 1_000.0) {
            return String.format(Locale.US, "%.1fk", value / 1_000.0);
        }
        if (abs >= 100.0) {
            return String.format(Locale.US, "%.0f", value);
        }
        if (abs >= 10.0) {
            return String.format(Locale.US, "%.1f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private void drawStateMessages(Graphics2D g) {
        Snake currentSnake = snake;
        if (currentSnake == null) {
            return;
        }

        Font titleFont = new Font("Monospaced", Font.BOLD, 52);
        Font subtitleFont = new Font("Monospaced", Font.BOLD, 22);

        if (paused) {
            drawGlowText(g, "PAUSA", titleFont, ARENA_WIDTH / 2 - 85, ARENA_HEIGHT / 2);
        }

        if (currentSnake.isDead()) {
            drawGlowText(g, "GAME OVER", titleFont, ARENA_WIDTH / 2 - 155, ARENA_HEIGHT / 2);
            drawGlowText(
                    g,
                    "DEATH: " + currentSnake.getDeathReason(),
                    subtitleFont,
                    ARENA_WIDTH / 2 - 170,
                    ARENA_HEIGHT / 2 + 42
            );
        }
    }

    private void drawGlowText(Graphics2D g, String text, Font font, int x, int y) {
        g.setFont(font);
        g.setColor(new Color(80, 255, 120, 80));
        g.drawString(text, x + 2, y + 2);
        g.setColor(GREEN);
        g.drawString(text, x, y);
    }

    public void stopLoop() {
        running = false;
    }

    private static final class ControlButton {
        final String action;
        final String label;
        final Rectangle bounds;

        ControlButton(String action, String label, int x, int y, int width, int height) {
            this.action = action;
            this.label = label;
            this.bounds = new Rectangle(x, y, width, height);
        }
    }

    private static final class TrainingPoint {
        final int generation;
        final double bestFitness;
        final double meanFitness;

        TrainingPoint(int generation, double bestFitness, double meanFitness) {
            this.generation = generation;
            this.bestFitness = bestFitness;
            this.meanFitness = meanFitness;
        }
    }
}
