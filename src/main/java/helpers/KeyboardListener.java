package helpers;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KeyboardListener implements KeyListener {

    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();
    private final Set<Integer> justPressedKeys = ConcurrentHashMap.newKeySet();

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (pressedKeys.add(keyCode)) {
            justPressedKeys.add(keyCode);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    public boolean isKeyPressed(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    public boolean consumeKeyPress(int keyCode) {
        return justPressedKeys.remove(keyCode);
    }

    public void clear() {
        pressedKeys.clear();
        justPressedKeys.clear();
    }
}

