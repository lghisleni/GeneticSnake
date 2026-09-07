package gameEngine;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class BestSnakeViewer extends JFrame {

    public static final double PRESENTATION_SPEED = 2.0;

    private final GameLoop gameLoop;

    public BestSnakeViewer() {
        this("Genetic Snake - Best of generation");
    }

    private BestSnakeViewer(String windowTitle) {
        World world = new World(12345L);
        Snake snake = new Snake(world);

        double[] neutralDNA = new double[AISnakeController.getRequiredDNALength()];
        AISnakeController controller = new AISnakeController(neutralDNA);

        gameLoop = new GameLoop(snake, controller, world);
        gameLoop.setSimulationSpeed(PRESENTATION_SPEED);

        setTitle(windowTitle);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(1050, 600));
        add(gameLoop);

        fitToUsableScreen();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                gameLoop.stopLoop();
            }
        });

        setVisible(true);
    }

    private void fitToUsableScreen() {
        Rectangle usable = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        int width = Math.min(1880, Math.max(1050, usable.width - 30));
        int height = Math.min(930, Math.max(600, usable.height - 30));
        setSize(width, height);
    }

    public void recordGeneration(
            int generation,
            double bestFitness,
            double meanFitness,
            String selectionName
    ) {
        gameLoop.setTrainingInfo(generation, bestFitness, meanFitness, selectionName);
    }

    public void playAndWait(
            double[] dna,
            int generation,
            double bestFitness,
            double meanFitness,
            String selectionName
    ) {
        if (!isDisplayable()) {
            return;
        }

        World world = new World(10_000L + generation);
        Snake snake = new Snake(world);
        snake.setColorSeed(dna);

        AISnakeController controller = new AISnakeController(dna);

        gameLoop.setTrainingInfo(generation, bestFitness, meanFitness, selectionName);
        gameLoop.replaceSnake(snake, controller, world);

        while (isDisplayable() && !snake.isDead()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        try {
            Thread.sleep(350);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void playFreshBestAndWait(double[] dna, double sourceTrainingFitness, String selectionName) {
        if (!isDisplayable()) return;

        World world = new World();
        Snake snake = new Snake(world);
        snake.setColorSeed(dna);
        AISnakeController controller = new AISnakeController(dna);

        gameLoop.setShowcaseInfo(sourceTrainingFitness, selectionName);
        gameLoop.replaceSnake(snake, controller, world);
        System.out.println("Nuova partita generata con world seed: " + world.getSeed());

        while (isDisplayable() && !snake.isDead()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static BestSnakeViewer createOnEdt() {
        return createOnEdt("Genetic Snake - Best of generation");
    }

    public static BestSnakeViewer createOnEdt(String windowTitle) {
        if (SwingUtilities.isEventDispatchThread()) {
            return new BestSnakeViewer(windowTitle);
        }

        final BestSnakeViewer[] holder = new BestSnakeViewer[1];
        try {
            SwingUtilities.invokeAndWait(() -> holder[0] = new BestSnakeViewer(windowTitle));
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile creare il viewer Swing.", e);
        }
        return holder[0];
    }
}
