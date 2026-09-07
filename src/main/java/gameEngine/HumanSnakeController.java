package gameEngine;

import java.awt.event.KeyEvent;

import helpers.KeyboardListener;

public class HumanSnakeController implements SnakeController {

    private final KeyboardListener keyboard;

    public HumanSnakeController(KeyboardListener keyboard) {
        this.keyboard = keyboard;
    }

    @Override
    public int decide(Snake snake, World world) {
        if (keyboard.isKeyPressed(KeyEvent.VK_LEFT) || keyboard.isKeyPressed(KeyEvent.VK_A)) {
            return TURN_LEFT;
        }

        if (keyboard.isKeyPressed(KeyEvent.VK_RIGHT) || keyboard.isKeyPressed(KeyEvent.VK_D)) {
            return TURN_RIGHT;
        }

        return GO_STRAIGHT;
    }
}
