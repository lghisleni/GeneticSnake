package gameEngine;

import java.awt.Point;

public class PhysicalCircle {

    public double x;
    public double y;
    public double vx;
    public double vy;
    public double radius;
    public long age;

    public PhysicalCircle(double x, double y, double radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.vx = 0;
        this.vy = 0;
        this.age = 0;
    }

    public PhysicalCircle(Point point, double radius) {
        this(point.x, point.y, radius);
    }

    public Point toPoint() {
        return new Point((int) Math.round(x), (int) Math.round(y));
    }

    public void updatePosition() {
        x += vx;
        y += vy;
        age++;
    }

    public void constrainSpeed(double maxSpeed, double damping) {
        vx *= damping;
        vy *= damping;

        double speed = getSpeed();
        if (speed > maxSpeed && speed > 0) {
            vx = vx / speed * maxSpeed;
            vy = vy / speed * maxSpeed;
        }

        if (Math.abs(vx) < 0.001) {
            vx = 0;
        }
        if (Math.abs(vy) < 0.001) {
            vy = 0;
        }
    }

    public void collideWall(double minX, double minY, double maxX, double maxY) {
        if (x - radius < minX) {
            x = minX + radius;
            vx = -vx * 0.9;
        }
        if (x + radius > maxX) {
            x = maxX - radius;
            vx = -vx * 0.9;
        }
        if (y - radius < minY) {
            y = minY + radius;
            vy = -vy * 0.9;
        }
        if (y + radius > maxY) {
            y = maxY - radius;
            vy = -vy * 0.9;
        }
    }

    public boolean isColliding(PhysicalCircle other) {
        return isColliding(other, 0);
    }

    public boolean isColliding(PhysicalCircle other, double thresholdDistance) {
        double minDistance = this.radius + other.radius + thresholdDistance;
        return distanceToCenter(other) < minDistance;
    }

    public void separateFrom(PhysicalCircle other) {
        if (this == other) {
            return;
        }

        double minDistance = this.radius + other.radius;
        double distance = distanceToCenter(other);

        if (distance == 0) {
            x += minDistance;
            return;
        }

        if (distance < minDistance) {
            double angle = Math.atan2(this.y - other.y, this.x - other.x);
            this.x = other.x + minDistance * Math.cos(angle);
            this.y = other.y + minDistance * Math.sin(angle);
        }
    }

    public void bounceFrom(PhysicalCircle other, double strength) {
        if (this == other) {
            return;
        }

        double minDistance = this.radius + other.radius;
        double distance = distanceToCenter(other);

        if (distance == 0) {
            return;
        }

        if (distance < minDistance) {
            double angle = Math.atan2(this.y - other.y, this.x - other.x);
            this.x = other.x + minDistance * Math.cos(angle);
            this.y = other.y + minDistance * Math.sin(angle);
            this.vx += Math.cos(angle) * strength;
            this.vy += Math.sin(angle) * strength;
        }
    }

    public void followStatic(PhysicalCircle target) {
        if (this == target) {
            return;
        }

        double desiredDistance = this.radius + target.radius;
        double angle = Math.atan2(this.y - target.y, this.x - target.x);
        this.x = target.x + desiredDistance * Math.cos(angle);
        this.y = target.y + desiredDistance * Math.sin(angle);
    }

    public void followSmooth(PhysicalCircle target, double smoothness) {
        if (this == target) {
            return;
        }

        double desiredDistance = this.radius + target.radius;
        double angle = Math.atan2(this.y - target.y, this.x - target.x);
        double targetX = target.x + desiredDistance * Math.cos(angle);
        double targetY = target.y + desiredDistance * Math.sin(angle);

        this.x += (targetX - this.x) * smoothness;
        this.y += (targetY - this.y) * smoothness;
    }

    public double distanceToCenter(PhysicalCircle other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceToEdge(PhysicalCircle other) {
        return distanceToCenter(other) - this.radius - other.radius;
    }

    public double angleTo(PhysicalCircle other) {
        return Math.atan2(other.y - this.y, other.x - this.x);
    }

    public double getSpeed() {
        return Math.sqrt(vx * vx + vy * vy);
    }
}
