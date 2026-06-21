package com.waterwheel.chaintransmission.simulation;

import com.waterwheel.chaintransmission.chain_simulator.config.ChainDynamicsProperties;
import com.waterwheel.chaintransmission.chain_simulator.config.ChainLinkDefaultProperties;
import com.waterwheel.chaintransmission.dto.ChainDynamicsResultDTO;
import com.waterwheel.chaintransmission.entity.ChainLinkParams;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ChainDynamicsSimulator {

    @Autowired
    private ChainDynamicsProperties dynamicsProperties;

    @Autowired
    private ChainLinkDefaultProperties defaultChainProperties;

    private static final double COLLISION_THRESHOLD = 1e-6;

    public ChainDynamicsResultDTO simulate(WaterwheelDevice device,
                                            ChainLinkParams params,
                                            double inputSpeedRPM,
                                            double inputTorque) {
        long startTime = System.currentTimeMillis();

        double timeStep = dynamicsProperties.getTimeStep();
        double simulationDuration = dynamicsProperties.getSimulationDuration();
        int maxIterations = dynamicsProperties.getMaxIterations();
        double GRAVITY = dynamicsProperties.getGravity();
        double COLLISION_RESTITUTION = dynamicsProperties.getCollisionRestitution();
        double GRID_CELL_MULTIPLIER = dynamicsProperties.getGridCellMultiplier();
        int MAX_ADAPTIVE_SUBSTEPS = dynamicsProperties.getMaxAdaptiveSubsteps();
        double HIGH_SPEED_THRESHOLD = dynamicsProperties.getHighSpeedThreshold();
        int COLLISION_CHECK_STRIDE_LOW_SPEED = dynamicsProperties.getCollisionCheckStrideLowSpeed();

        double sprocketRadius = device.getSprocketRadius() != null ?
                device.getSprocketRadius().doubleValue() : 0.35;
        int numLinks = device.getNumLinks() != null ?
                device.getNumLinks() : 120;
        double chainLength = device.getChainLength() != null ?
                device.getChainLength().doubleValue() : 15.5;

        double linkMass = params.getLinkMass() != null ?
                params.getLinkMass().doubleValue() : 0.25;
        double linkLength = params.getLinkLength() != null ?
                params.getLinkLength().doubleValue() : 0.125;
        double stiffness = params.getLinkStiffness() != null ?
                params.getLinkStiffness().doubleValue() : 500000.0;
        double damping = params.getLinkDamping() != null ?
                params.getLinkDamping().doubleValue() : 150.0;
        double frictionCoeff = params.getFrictionCoefficient() != null ?
                params.getFrictionCoefficient().doubleValue() : 0.15;
        double allowableTension = params.getAllowableTension() != null ?
                params.getAllowableTension().doubleValue() : 15000.0;

        double angularVelocity = inputSpeedRPM * 2.0 * Math.PI / 60.0;

        Sprocket drivingSprocket = new Sprocket(0, sprocketRadius, 0, sprocketRadius, 20, true);
        drivingSprocket.setAngularVelocity(angularVelocity);
        drivingSprocket.setTorque(inputTorque);

        double horizontalDistance = chainLength / 2.0 - Math.PI * sprocketRadius;
        Sprocket drivenSprocket = new Sprocket(horizontalDistance, sprocketRadius, 0, sprocketRadius * 0.95, 18, false);

        List<ChainLink> links = initializeChainLinks(numLinks, linkMass, linkLength,
                drivingSprocket, drivenSprocket);

        double[] tensionHistory = new double[numLinks];
        List<Double> allTensions = new ArrayList<>();
        List<Double> collisionForces = new ArrayList<>();
        double[] maxTensionPerLink = new double[numLinks];
        double[] maxCollisionPerLink = new double[numLinks];

        int iterations = Math.min((int) (simulationDuration / timeStep), maxIterations);
        boolean resonanceRisk = false;
        double[] velocityHistory = new double[100];
        int velocityIndex = 0;

        double gridCellSize = GRID_CELL_MULTIPLIER * linkLength;
        Map<String, List<Integer>> spatialGrid = new HashMap<>();
        double[] linkAvgCollision = new double[numLinks];
        int collisionCheckCounter = 0;

        for (int iter = 0; iter < iterations; iter++) {
            double t = iter * timeStep;

            double avgSpeed = 0;
            for (ChainLink link : links) avgSpeed += link.getSpeed();
            avgSpeed = avgSpeed / numLinks + 1e-10;

            int subSteps = 1;
            if (avgSpeed > HIGH_SPEED_THRESHOLD) {
                double speedRatio = avgSpeed / HIGH_SPEED_THRESHOLD;
                subSteps = Math.min(MAX_ADAPTIVE_SUBSTEPS, (int) Math.ceil(speedRatio));
            }
            double subDt = timeStep / subSteps;

            boolean doFullCollisionThisStep;
            if (avgSpeed < HIGH_SPEED_THRESHOLD * 0.6) {
                collisionCheckCounter = (collisionCheckCounter + 1) % COLLISION_CHECK_STRIDE_LOW_SPEED;
                doFullCollisionThisStep = (collisionCheckCounter == 0);
            } else {
                doFullCollisionThisStep = true;
            }

            for (int sub = 0; sub < subSteps; sub++) {
                double subProgress = (double) sub / subSteps;
                drivingSprocket.rotate(subDt);
                drivenSprocket.setAngularVelocity(drivingSprocket.getAngularVelocity() * 0.98);
                drivenSprocket.rotate(subDt);

                for (int i = 0; i < numLinks; i++) {
                    ChainLink link = links.get(i);
                    calculateLinkDynamics(link, links, i, drivingSprocket, drivenSprocket,
                            stiffness, damping, frictionCoeff, numLinks);
                }

                if (doFullCollisionThisStep) {
                    resolveNonAdjacentCollisionsSpatialGrid(links, linkLength, gridCellSize,
                            spatialGrid, stiffness, damping, maxCollisionPerLink);
                }

                for (ChainLink link : links) {
                    link.updateKinematics(subDt);
                }
            }

            for (int i = 0; i < numLinks; i++) {
                ChainLink current = links.get(i);
                ChainLink next = links.get((i + 1) % numLinks);
                double collisionForce = calculateCollisionForce(current, next, stiffness);
                current.setCollisionForce(Math.max(collisionForce, maxCollisionPerLink[i]));
                if (iter % Math.max(1, iterations / 200) == 0) {
                    collisionForces.add(current.getCollisionForce());
                }
                maxCollisionPerLink[i] *= 0.9;
            }

            for (int i = 0; i < numLinks; i++) {
                ChainLink link = links.get(i);
                double tension = calculateLinkTension(link, links, i, stiffness, numLinks);
                link.setTension(tension);
                tensionHistory[i] = tension;
                if (iter % Math.max(1, iterations / 500) == 0) {
                    allTensions.add(tension);
                }
                if (tension > maxTensionPerLink[i]) {
                    maxTensionPerLink[i] = tension;
                }
            }

            velocityHistory[velocityIndex] = avgSpeed;
            velocityIndex = (velocityIndex + 1) % velocityHistory.length;

            if (iter > velocityHistory.length && iter % 100 == 0) {
                if (detectResonance(velocityHistory)) {
                    resonanceRisk = true;
                    log.warn("检测到链条共振风险, 时间: {}s", t);
                }
            }

            if (tensionHasCriticalValue(maxTensionPerLink, allowableTension * 0.95)) {
                log.warn("链条张力接近临界值");
            }
        }

        double maxTension = Arrays.stream(maxTensionPerLink).max().orElse(0);
        double minTension = Arrays.stream(tensionHistory).min().orElse(0);
        double avgTension = allTensions.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        List<Double> tensionDist = new ArrayList<>();
        for (double v : maxTensionPerLink) {
            tensionDist.add(v);
        }

        List<Double> frequencies = calculateVibrationFrequencies(links, stiffness, linkMass);

        Map<String, Object> linkPositions = new HashMap<>();
        Map<String, Object> linkVelocities = new HashMap<>();
        for (int i = 0; i < Math.min(numLinks, 50); i++) {
            ChainLink link = links.get(i);
            linkPositions.put(String.valueOf(i), Arrays.asList(
                    round(link.getPositionX(), 4),
                    round(link.getPositionY(), 4),
                    round(link.getPositionZ(), 4)
            ));
            linkVelocities.put(String.valueOf(i), Arrays.asList(
                    round(link.getVelocityX(), 4),
                    round(link.getVelocityY(), 4),
                    round(link.getVelocityZ(), 4)
            ));
        }

        ChainDynamicsResultDTO result = new ChainDynamicsResultDTO();
        result.setDeviceId(device.getDeviceId());
        result.setInputSpeed(java.math.BigDecimal.valueOf(inputSpeedRPM));
        result.setInputTorque(java.math.BigDecimal.valueOf(inputTorque));
        result.setLinkCount(numLinks);
        result.setTensionDistribution(tensionDist);
        result.setVibrationFrequencies(frequencies);
        result.setCollisionForces(collisionForces.subList(0, Math.min(collisionForces.size(), 200)));
        result.setMaxTension(java.math.BigDecimal.valueOf(round(maxTension, 4)));
        result.setMinTension(java.math.BigDecimal.valueOf(round(minTension, 4)));
        result.setAvgTension(java.math.BigDecimal.valueOf(round(avgTension, 4)));
        result.setResonanceRisk(resonanceRisk);
        result.setSimulationDurationMs((int) (System.currentTimeMillis() - startTime));
        result.setLinkPositions(linkPositions);
        result.setLinkVelocities(linkVelocities);

        log.info("链传动仿真完成, 耗时: {}ms, 最大张力: {}N, 共振风险: {}",
                result.getSimulationDurationMs(), maxTension, resonanceRisk);

        return result;
    }

    private List<ChainLink> initializeChainLinks(int numLinks, double linkMass, double linkLength,
                                                  Sprocket driving, Sprocket driven) {
        List<ChainLink> links = new ArrayList<>();
        double totalChainLength = numLinks * linkLength;

        double upperLength = driven.getCenterX() - driving.getCenterX();
        double sprocketCircumference1 = 2 * Math.PI * driving.getRadius();
        double sprocketCircumference2 = 2 * Math.PI * driven.getRadius();
        int upperLinks = (int) (numLinks * upperLength / totalChainLength);
        int sprocket1Links = (int) (numLinks * sprocketCircumference1 / totalChainLength / 2);
        int sprocket2Links = (int) (numLinks * sprocketCircumference2 / totalChainLength / 2);

        for (int i = 0; i < numLinks; i++) {
            ChainLink link = new ChainLink(i, linkMass, linkLength);

            if (i < upperLinks) {
                double ratio = (double) i / upperLinks;
                link.setPositionX(driving.getCenterX() + ratio * upperLength);
                link.setPositionY(driving.getCenterY() + driving.getRadius());
                link.setPositionZ(0);
                link.setOnSprocket(false);
            } else if (i < upperLinks + sprocket2Links) {
                int localIdx = i - upperLinks;
                double angle = Math.PI / 2 + Math.PI * localIdx / sprocket2Links;
                link.setPositionX(driven.getCenterX() + driven.getRadius() * Math.cos(angle));
                link.setPositionY(driven.getCenterY() + driven.getRadius() * Math.sin(angle));
                link.setPositionZ(0);
                link.setOnSprocket(true);
            } else if (i < numLinks - sprocket1Links) {
                int localIdx = i - upperLinks - sprocket2Links;
                int lowerLinks = numLinks - upperLinks - sprocket1Links - sprocket2Links;
                double ratio = (double) localIdx / lowerLinks;
                link.setPositionX(driven.getCenterX() - ratio * upperLength);
                link.setPositionY(driving.getCenterY() - driving.getRadius());
                link.setPositionZ(0);
                link.setOnSprocket(false);
            } else {
                int localIdx = i - (numLinks - sprocket1Links);
                double angle = -Math.PI / 2 + Math.PI * localIdx / sprocket1Links;
                link.setPositionX(driving.getCenterX() + driving.getRadius() * Math.cos(angle));
                link.setPositionY(driving.getCenterY() + driving.getRadius() * Math.sin(angle));
                link.setPositionZ(0);
                link.setOnSprocket(true);
            }

            links.add(link);
        }
        return links;
    }

    private void calculateLinkDynamics(ChainLink link, List<ChainLink> links, int index,
                                        Sprocket driving, Sprocket driven,
                                        double stiffness, double damping, double friction, int numLinks) {
        ChainLink prev = links.get((index - 1 + numLinks) % numLinks);
        ChainLink next = links.get((index + 1) % numLinks);

        double forceX = 0;
        double forceY = -link.getMass() * GRAVITY;
        double forceZ = 0;

        double dx1 = link.getPositionX() - prev.getPositionX();
        double dy1 = link.getPositionY() - prev.getPositionY();
        double dz1 = link.getPositionZ() - prev.getPositionZ();
        double dist1 = Math.sqrt(dx1 * dx1 + dy1 * dy1 + dz1 * dz1);

        if (dist1 > 1e-10) {
            double elongation = dist1 - link.getLength();
            double springForce = stiffness * elongation;
            double dampingForce = damping * (
                    (link.getVelocityX() - prev.getVelocityX()) * dx1 / dist1 +
                    (link.getVelocityY() - prev.getVelocityY()) * dy1 / dist1 +
                    (link.getVelocityZ() - prev.getVelocityZ()) * dz1 / dist1
            );
            double totalForce = springForce + dampingForce;
            forceX -= totalForce * dx1 / dist1;
            forceY -= totalForce * dy1 / dist1;
            forceZ -= totalForce * dz1 / dist1;
        }

        double dx2 = link.getPositionX() - next.getPositionX();
        double dy2 = link.getPositionY() - next.getPositionY();
        double dz2 = link.getPositionZ() - next.getPositionZ();
        double dist2 = Math.sqrt(dx2 * dx2 + dy2 * dy2 + dz2 * dz2);

        if (dist2 > 1e-10) {
            double elongation = dist2 - next.getLength();
            double springForce = stiffness * elongation;
            double dampingForce = damping * (
                    (link.getVelocityX() - next.getVelocityX()) * dx2 / dist2 +
                    (link.getVelocityY() - next.getVelocityY()) * dy2 / dist2 +
                    (link.getVelocityZ() - next.getVelocityZ()) * dz2 / dist2
            );
            double totalForce = springForce + dampingForce;
            forceX -= totalForce * dx2 / dist2;
            forceY -= totalForce * dy2 / dist2;
            forceZ -= totalForce * dz2 / dist2;
        }

        if (link.isOnSprocket()) {
            double[] sprocketForce = calculateSprocketInteraction(link, driving, driven, friction);
            forceX += sprocketForce[0];
            forceY += sprocketForce[1];
        }

        double speed = link.getSpeed();
        if (speed > 1e-10) {
            double frictionForce = friction * link.getMass() * GRAVITY;
            forceX -= frictionForce * link.getVelocityX() / speed;
            forceY -= frictionForce * link.getVelocityY() / speed;
            forceZ -= frictionForce * link.getVelocityZ() / speed;
        }

        link.setAccelerationX(forceX / link.getMass());
        link.setAccelerationY(forceY / link.getMass());
        link.setAccelerationZ(forceZ / link.getMass());
    }

    private double[] calculateSprocketInteraction(ChainLink link, Sprocket driving, Sprocket driven, double friction) {
        double forceX = 0;
        double forceY = 0;

        double distToDriving = Math.sqrt(
                Math.pow(link.getPositionX() - driving.getCenterX(), 2) +
                Math.pow(link.getPositionY() - driving.getCenterY(), 2)
        );
        double distToDriven = Math.sqrt(
                Math.pow(link.getPositionX() - driven.getCenterX(), 2) +
                Math.pow(link.getPositionY() - driven.getCenterY(), 2)
        );

        Sprocket sprocket = distToDriving < distToDriven ? driving : driven;
        double dist = Math.min(distToDriving, distToDriven);
        double radialDist = dist - sprocket.getRadius();

        if (Math.abs(radialDist) < 5 * link.getLength()) {
            double dx = link.getPositionX() - sprocket.getCenterX();
            double dy = link.getPositionY() - sprocket.getCenterY();
            double normalizer = Math.sqrt(dx * dx + dy * dy) + 1e-10;

            double normalForce = -500000 * radialDist;
            forceX += normalForce * dx / normalizer;
            forceY += normalForce * dy / normalizer;

            double tangentialDx = -dy / normalizer;
            double tangentialDy = dx / normalizer;
            double tangentialSpeed = link.getVelocityX() * tangentialDx + link.getVelocityY() * tangentialDy;
            double sprocketSpeed = sprocket.getLinearSpeed();
            double slipSpeed = tangentialSpeed - sprocketSpeed;

            double frictionForce = -friction * Math.abs(normalForce) * Math.signum(slipSpeed);
            forceX += frictionForce * tangentialDx;
            forceY += frictionForce * tangentialDy;
        }

        return new double[]{forceX, forceY};
    }

    private double calculateCollisionForce(ChainLink a, ChainLink b, double stiffness) {
        double distance = a.distanceTo(b);
        double minDistance = (a.getLength() + b.getLength()) / 2;

        if (distance < minDistance - COLLISION_THRESHOLD) {
            double penetration = minDistance - distance;
            double relativeVelocity = a.getSpeed() - b.getSpeed();
            double force = stiffness * penetration + (1 + COLLISION_RESTITUTION) * relativeVelocity * 10;
            return Math.max(0, force);
        }
        return 0;
    }

    private double calculateLinkTension(ChainLink link, List<ChainLink> links, int index,
                                         double stiffness, int numLinks) {
        ChainLink prev = links.get((index - 1 + numLinks) % numLinks);
        ChainLink next = links.get((index + 1) % numLinks);

        double dist1 = link.distanceTo(prev);
        double dist2 = link.distanceTo(next);

        double tension1 = Math.max(0, stiffness * (dist1 - link.getLength()));
        double tension2 = Math.max(0, stiffness * (dist2 - next.getLength()));

        return (tension1 + tension2) / 2;
    }

    private List<Double> calculateVibrationFrequencies(List<ChainLink> links, double stiffness, double linkMass) {
        List<Double> frequencies = new ArrayList<>();
        int numModes = Math.min(5, links.size() / 4);

        double totalLength = 0;
        for (ChainLink link : links) {
            totalLength += link.getLength();
        }

        double linearDensity = links.size() * linkMass / totalLength;
        double avgTension = 0;
        for (ChainLink link : links) {
            avgTension += link.getTension();
        }
        avgTension /= links.size();
        avgTension = Math.max(avgTension, 100);

        for (int n = 1; n <= numModes; n++) {
            double frequency = (n / (2 * totalLength)) * Math.sqrt(avgTension / linearDensity);
            frequencies.add(round(frequency, 4));
        }

        double lateralFreq = (1 / (2 * Math.PI)) * Math.sqrt(stiffness / (2 * linkMass));
        frequencies.add(round(lateralFreq, 4));

        return frequencies;
    }

    private boolean detectResonance(double[] velocityHistory) {
        int n = velocityHistory.length;
        double mean = 0;
        for (double v : velocityHistory) {
            mean += v;
        }
        mean /= n;

        double variance = 0;
        for (double v : velocityHistory) {
            variance += (v - mean) * (v - mean);
        }
        variance /= n;

        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = stdDev / (Math.abs(mean) + 1e-10);

        return coefficientOfVariation > 0.5;
    }

    private void resolveNonAdjacentCollisionsSpatialGrid(List<ChainLink> links, double linkLength,
                                                          double gridCellSize,
                                                          Map<String, List<Integer>> spatialGrid,
                                                          double stiffness, double damping,
                                                          double[] maxCollisionPerLink) {
        spatialGrid.clear();
        for (int i = 0; i < links.size(); i++) {
            ChainLink l = links.get(i);
            int gx = (int) Math.floor(l.getPositionX() / gridCellSize);
            int gy = (int) Math.floor(l.getPositionY() / gridCellSize);
            int gz = (int) Math.floor(l.getPositionZ() / gridCellSize);
            String key = gx + "," + gy + "," + gz;
            spatialGrid.computeIfAbsent(key, k -> new ArrayList<>(8)).add(i);
        }

        double minContactDist = linkLength * 0.9;
        double minContactSq = minContactDist * minContactDist;

        for (int i = 0; i < links.size(); i++) {
            ChainLink a = links.get(i);
            int gx = (int) Math.floor(a.getPositionX() / gridCellSize);
            int gy = (int) Math.floor(a.getPositionY() / gridCellSize);
            int gz = (int) Math.floor(a.getPositionZ() / gridCellSize);

            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        String key = (gx + ox) + "," + (gy + oy) + "," + (gz + oz);
                        List<Integer> bucket = spatialGrid.get(key);
                        if (bucket == null) continue;

                        for (int j : bucket) {
                            if (j <= i) continue;
                            if (Math.abs(j - i) <= 1 || (i == 0 && j == links.size() - 1)) continue;
                            ChainLink b = links.get(j);

                            double dx = a.getPositionX() - b.getPositionX();
                            double dy = a.getPositionY() - b.getPositionY();
                            double dz = a.getPositionZ() - b.getPositionZ();
                            double distSq = dx * dx + dy * dy + dz * dz;

                            if (distSq < minContactSq && distSq > 1e-14) {
                                double dist = Math.sqrt(distSq);
                                double penetration = minContactDist - dist;
                                double nx = dx / dist;
                                double ny = dy / dist;
                                double nz = dz / dist;

                                double relVx = a.getVelocityX() - b.getVelocityX();
                                double relVy = a.getVelocityY() - b.getVelocityY();
                                double relVz = a.getVelocityZ() - b.getVelocityZ();
                                double relVn = relVx * nx + relVy * ny + relVz * nz;

                                if (relVn < 0) {
                                    double normalForce = stiffness * penetration - damping * relVn;
                                    normalForce = Math.max(0, normalForce);

                                    double totalMass = a.getMass() + b.getMass();
                                    double massRatioA = b.getMass() / totalMass;
                                    double massRatioB = a.getMass() / totalMass;

                                    double impulse = normalForce * timeStep;
                                    a.setVelocityX(a.getVelocityX() + impulse * nx * massRatioA / a.getMass());
                                    a.setVelocityY(a.getVelocityY() + impulse * ny * massRatioA / a.getMass());
                                    a.setVelocityZ(a.getVelocityZ() + impulse * nz * massRatioA / a.getMass());
                                    b.setVelocityX(b.getVelocityX() - impulse * nx * massRatioB / b.getMass());
                                    b.setVelocityY(b.getVelocityY() - impulse * ny * massRatioB / b.getMass());
                                    b.setVelocityZ(b.getVelocityZ() - impulse * nz * massRatioB / b.getMass());

                                    double overlapCorrection = penetration * 0.5;
                                    a.setPositionX(a.getPositionX() + nx * overlapCorrection * massRatioA);
                                    a.setPositionY(a.getPositionY() + ny * overlapCorrection * massRatioA);
                                    a.setPositionZ(a.getPositionZ() + nz * overlapCorrection * massRatioA);
                                    b.setPositionX(b.getPositionX() - nx * overlapCorrection * massRatioB);
                                    b.setPositionY(b.getPositionY() - ny * overlapCorrection * massRatioB);
                                    b.setPositionZ(b.getPositionZ() - nz * overlapCorrection * massRatioB);

                                    if (normalForce > maxCollisionPerLink[i])
                                        maxCollisionPerLink[i] = normalForce;
                                    if (normalForce > maxCollisionPerLink[j])
                                        maxCollisionPerLink[j] = normalForce;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean tensionHasCriticalValue(double[] tensions, double criticalValue) {
        for (double t : tensions) {
            if (t >= criticalValue) return true;
        }
        return false;
    }

    private double round(double value, int decimalPlaces) {
        double factor = Math.pow(10, decimalPlaces);
        return Math.round(value * factor) / factor;
    }
}
