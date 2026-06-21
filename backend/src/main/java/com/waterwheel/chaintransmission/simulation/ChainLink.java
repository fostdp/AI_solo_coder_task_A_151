package com.waterwheel.chaintransmission.simulation;

import lombok.Data;

@Data
public class ChainLink {

    private int id;
    private double mass;
    private double length;
    private double positionX;
    private double positionY;
    private double positionZ;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private double accelerationX;
    private double accelerationY;
    private double accelerationZ;
    private double tension;
    private double angle;
    private double angularVelocity;
    private boolean onSprocket;
    private double collisionForce;

    public ChainLink(int id, double mass, double length) {
        this.id = id;
        this.mass = mass;
        this.length = length;
    }

    public void updateKinematics(double dt) {
        velocityX += accelerationX * dt;
        velocityY += accelerationY * dt;
        velocityZ += accelerationZ * dt;
        positionX += velocityX * dt;
        positionY += velocityY * dt;
        positionZ += velocityZ * dt;
    }

    public double getSpeed() {
        return Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    }

    public double getKineticEnergy() {
        double v = getSpeed();
        return 0.5 * mass * v * v;
    }

    public double distanceTo(ChainLink other) {
        double dx = positionX - other.positionX;
        double dy = positionY - other.positionY;
        double dz = positionZ - other.positionZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
