package com.waterwheel.chaintransmission.simulation;

import lombok.Data;

@Data
public class Sprocket {

    private double centerX;
    private double centerY;
    private double centerZ;
    private double radius;
    private int teethCount;
    private double angularVelocity;
    private double angle;
    private double torque;
    private boolean isDriving;

    public Sprocket(double centerX, double centerY, double centerZ, double radius, int teethCount, boolean isDriving) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
        this.teethCount = teethCount;
        this.isDriving = isDriving;
    }

    public double getToothAngle(int toothIndex) {
        return 2.0 * Math.PI * toothIndex / teethCount;
    }

    public double getLinearSpeed() {
        return radius * angularVelocity;
    }

    public double getCentripetalAcceleration() {
        return radius * angularVelocity * angularVelocity;
    }

    public void rotate(double dt) {
        angle += angularVelocity * dt;
        angle = angle % (2.0 * Math.PI);
    }

    public double getTangentialForce() {
        if (Math.abs(radius) < 1e-10) return 0;
        return torque / radius;
    }
}
