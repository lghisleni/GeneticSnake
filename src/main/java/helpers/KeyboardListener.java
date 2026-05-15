package helpers;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyboardListener implements KeyListener {

    private int currentKey = -1;

    @Override
    public void keyPressed(KeyEvent e) {
        currentKey = e.getKeyCode();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        currentKey = -1;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    public int getKey() {
        return currentKey;
    }

    public boolean isKeyPressed(int keyCode) {
        return currentKey == keyCode;
    }
}