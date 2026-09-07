package gameEngine;

import java.util.List;

public final class SnakeVision {

    public static final int RAY_COUNT = 32;
    public static final int VALUES_PER_RAY = 3;
    public static final int INPUT_SIZE = RAY_COUNT * VALUES_PER_RAY;
    public static final double MAX_VIEW_DISTANCE = 600.0;

    private static final double EPSILON = 1e-9;

    private SnakeVision() {
    }

    public static double[] getVision(Snake snake, World world) {
        double[] vision = new double[INPUT_SIZE];
        PhysicalCircle head = snake.getHead();
        List<PhysicalCircle> segments = snake.getSegments();

        for (int ray = 0; ray < RAY_COUNT; ray++) {
            double rayAngle = getRayAngle(snake, ray);
            double dirX = Math.cos(rayAngle);
            double dirY = Math.sin(rayAngle);

            double wallDistance = distanceToWall(head.x, head.y, dirX, dirY, world);
            double foodDistance = nearestCircleDistance(
                    head.x, head.y, dirX, dirY, world.getFoodList(), 0
            );
            double bodyDistance = nearestCircleDistance(
                    head.x, head.y, dirX, dirY, segments, 1
            );

            int index = ray * VALUES_PER_RAY;
            vision[index] = proximityValue(wallDistance);
            vision[index + 1] = proximityValue(foodDistance);
            vision[index + 2] = proximityValue(bodyDistance);
        }

        return vision;
    }

    public static double getRayAngle(Snake snake, int ray) {
        double angleStep = (Math.PI * 2.0) / RAY_COUNT;
        return snake.getAngle() - Math.PI + ray * angleStep;
    }

    private static double proximityValue(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > MAX_VIEW_DISTANCE) {
            return 0.0;
        }
        return 1.0 - distance / MAX_VIEW_DISTANCE;
    }

    private static double distanceToWall(
            double originX,
            double originY,
            double dirX,
            double dirY,
            World world
    ) {
        double minX = World.BORDER;
        double maxX = world.getWidth() - World.BORDER;
        double minY = World.BORDER;
        double maxY = world.getHeight() - World.BORDER;

        double best = Double.POSITIVE_INFINITY;

        if (dirX > EPSILON) {
            best = Math.min(best, (maxX - originX) / dirX);
        } else if (dirX < -EPSILON) {
            best = Math.min(best, (minX - originX) / dirX);
        }

        if (dirY > EPSILON) {
            best = Math.min(best, (maxY - originY) / dirY);
        } else if (dirY < -EPSILON) {
            best = Math.min(best, (minY - originY) / dirY);
        }

        return best >= 0 ? best : Double.POSITIVE_INFINITY;
    }

    private static double nearestCircleDistance(
            double originX,
            double originY,
            double dirX,
            double dirY,
            List<PhysicalCircle> circles,
            int startIndex
    ) {
        double best = Double.POSITIVE_INFINITY;

        for (int i = startIndex; i < circles.size(); i++) {
            PhysicalCircle circle = circles.get(i);
            double distance = rayCircleIntersectionDistance(
                    originX, originY, dirX, dirY,
                    circle.x, circle.y, circle.radius
            );

            if (distance >= 0 && distance < best) {
                best = distance;
            }
        }

        return best;
    }

    private static double rayCircleIntersectionDistance(
            double originX,
            double originY,
            double dirX,
            double dirY,
            double centerX,
            double centerY,
            double radius
    ) {
        double toCenterX = centerX - originX;
        double toCenterY = centerY - originY;
        double projection = toCenterX * dirX + toCenterY * dirY;
        double centerDistanceSquared = toCenterX * toCenterX + toCenterY * toCenterY;
        double perpendicularSquared = centerDistanceSquared - projection * projection;
        double radiusSquared = radius * radius;

        if (perpendicularSquared > radiusSquared) {
            return Double.POSITIVE_INFINITY;
        }

        double halfChord = Math.sqrt(Math.max(0.0, radiusSquared - perpendicularSquared));
        double first = projection - halfChord;
        double second = projection + halfChord;

        if (first >= 0) {
            return first;
        }
        if (second >= 0) {
            return second;
        }
        return Double.POSITIVE_INFINITY;
    }
}