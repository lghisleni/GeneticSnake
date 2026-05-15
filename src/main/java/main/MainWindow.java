package main;

import gameEngine.GameLoop;
import helpers.KeyboardListener;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class MainWindow extends JFrame {

    private static final String TITLE = "Genetic Snake AI";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainWindow::new);
    }

    public MainWindow() {
        setTitle(TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        KeyboardListener keyboard = new KeyboardListener();
        GameLoop gameLoop = new GameLoop(keyboard);

        addKeyListener(keyboard);
        add(gameLoop);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        gameLoop.requestFocusInWindow();
    }
}