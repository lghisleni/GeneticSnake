package gameEngine;

public interface SnakeController {

    int GO_STRAIGHT = 0;
    int TURN_LEFT = 1;
    int TURN_RIGHT = 2;

    int decide(Snake snake, World world);
}