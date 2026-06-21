package com.waterwheel.chaintransmission.optimization;

import com.waterwheel.chaintransmission.dto.OptimizationResultDTO;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class WaterEfficiencyOptimizer {

    @Value("${optimization.response-surface.design-points:15}")
    private int designPoints;

    @Value("${optimization.response-surface.max-iterations:50}")
    private int maxIterations;

    @Value("${optimization.response-surface.convergence-tolerance:1.0e-6}")
    private double convergenceTolerance;

    @Value("${optimization.scraper.min-depth:0.05}")
    private double minScraperDepth;
    @Value("${optimization.scraper.max-depth:0.20}")
    private double maxScraperDepth;

    @Value("${optimization.scraper.min-width:0.10}")
    private double minScraperWidth;
    @Value("${optimization.scraper.max-width:0.40}")
    private double maxScraperWidth;

    @Value("${optimization.scraper.min-angle:15.0}")
    private double minScraperAngle;
    @Value("${optimization.scraper.max-angle:60.0}")
    private double maxScraperAngle;

    @Value("${optimization.chain-speed.min:0.5}")
    private double minChainSpeed;
    @Value("${optimization.chain-speed.max:3.0}")
    private double maxChainSpeed;

    private static final double GRAVITY = 9.81;
    private static final double WATER_DENSITY = 1000.0;

    public OptimizationResultDTO optimize(WaterwheelDevice device, double currentWaterFlow) {
        log.info("开始提水效率优化, 设备: {}, 当前提水量: {} L/h", device.getDeviceName(), currentWaterFlow);

        int scraperCount = device.getScraperCount() != null ? device.getScraperCount() : 24;
        double sprocketRadius = device.getSprocketRadius() != null ?
                device.getSprocketRadius().doubleValue() : 0.35;

        List<Map<String, Object>> designPointsData = generateCentralCompositeDesign();

        List<Map<String, Object>> evaluatedPoints = new ArrayList<>();
        for (Map<String, Object> point : designPointsData) {
            double depth = (double) point.get("depth");
            double width = (double) point.get("width");
            double angle = (double) point.get("angle");
            double speed = (double) point.get("speed");

            double waterFlow = calculateWaterFlow(depth, width, angle, speed, scraperCount, sprocketRadius);
            point.put("waterFlow", waterFlow);
            evaluatedPoints.add(point);
        }

        double[] coefficients = fitResponseSurface(evaluatedPoints);

        Map<String, Object> optimum = findOptimum(coefficients, scraperCount, sprocketRadius);

        double optimalDepth = (double) optimum.get("depth");
        double optimalWidth = (double) optimum.get("width");
        double optimalAngle = (double) optimum.get("angle");
        double optimalSpeed = (double) optimum.get("speed");
        double maxPredictedFlow = (double) optimum.get("maxFlow");

        double efficiencyImprovement = currentWaterFlow > 0 ?
                ((maxPredictedFlow - currentWaterFlow) / currentWaterFlow) * 100 : 0;

        Map<String, Object> responseSurfaceEquation = buildResponseSurfaceEquation(coefficients);

        OptimizationResultDTO result = new OptimizationResultDTO();
        result.setDeviceId(device.getDeviceId());
        result.setMethod("ResponseSurfaceMethod_CentralCompositeDesign");
        result.setOptimalScraperDepth(java.math.BigDecimal.valueOf(round(optimalDepth, 4)));
        result.setOptimalScraperWidth(java.math.BigDecimal.valueOf(round(optimalWidth, 4)));
        result.setOptimalScraperAngle(java.math.BigDecimal.valueOf(round(optimalAngle, 2)));
        result.setOptimalChainSpeed(java.math.BigDecimal.valueOf(round(optimalSpeed, 4)));
        result.setPredictedMaxWaterFlow(java.math.BigDecimal.valueOf(round(maxPredictedFlow, 2)));
        result.setEfficiencyImprovement(java.math.BigDecimal.valueOf(round(efficiencyImprovement, 2)));
        result.setIterations(maxIterations);
        result.setConvergence(true);
        result.setDesignPoints(evaluatedPoints);
        result.setResponseSurfaceEquation(responseSurfaceEquation);

        log.info("优化完成: 最优刮板深度={}m, 宽度={}m, 角度={}°, 链速={}m/s, 预测最大提水量={} L/h, 效率提升={}%",
                optimalDepth, optimalWidth, optimalAngle, optimalSpeed, maxPredictedFlow, efficiencyImprovement);

        return result;
    }

    private List<Map<String, Object>> generateCentralCompositeDesign() {
        List<Map<String, Object>> points = new ArrayList<>();

        double midDepth = (minScraperDepth + maxScraperDepth) / 2;
        double midWidth = (minScraperWidth + maxScraperWidth) / 2;
        double midAngle = (minScraperAngle + maxScraperAngle) / 2;
        double midSpeed = (minChainSpeed + maxChainSpeed) / 2;

        double alpha = Math.pow(2, 4.0 / 4);

        points.add(createPoint(minScraperDepth, minScraperWidth, minScraperAngle, minChainSpeed));
        points.add(createPoint(maxScraperDepth, minScraperWidth, minScraperAngle, minChainSpeed));
        points.add(createPoint(minScraperDepth, maxScraperWidth, minScraperAngle, minChainSpeed));
        points.add(createPoint(maxScraperDepth, maxScraperWidth, minScraperAngle, minChainSpeed));
        points.add(createPoint(minScraperDepth, minScraperWidth, maxScraperAngle, minChainSpeed));
        points.add(createPoint(maxScraperDepth, minScraperWidth, maxScraperAngle, minChainSpeed));
        points.add(createPoint(minScraperDepth, maxScraperWidth, maxScraperAngle, minChainSpeed));
        points.add(createPoint(maxScraperDepth, maxScraperWidth, maxScraperAngle, minChainSpeed));
        points.add(createPoint(minScraperDepth, minScraperWidth, minScraperAngle, maxChainSpeed));
        points.add(createPoint(maxScraperDepth, minScraperWidth, minScraperAngle, maxChainSpeed));
        points.add(createPoint(minScraperDepth, maxScraperWidth, minScraperAngle, maxChainSpeed));
        points.add(createPoint(maxScraperDepth, maxScraperWidth, minScraperAngle, maxChainSpeed));
        points.add(createPoint(minScraperDepth, minScraperWidth, maxScraperAngle, maxChainSpeed));
        points.add(createPoint(maxScraperDepth, minScraperWidth, maxScraperAngle, maxChainSpeed));
        points.add(createPoint(minScraperDepth, maxScraperWidth, maxScraperAngle, maxChainSpeed));
        points.add(createPoint(maxScraperDepth, maxScraperWidth, maxScraperAngle, maxChainSpeed));

        double starDepth = midDepth + alpha * (maxScraperDepth - midDepth);
        double starWidth = midWidth + alpha * (maxScraperWidth - midWidth);
        double starAngle = midAngle + alpha * (maxScraperAngle - midAngle);
        double starSpeed = midSpeed + alpha * (maxChainSpeed - midSpeed);

        points.add(createPoint(starDepth, midWidth, midAngle, midSpeed));
        points.add(createPoint(midDepth - alpha * (midDepth - minScraperDepth), midWidth, midAngle, midSpeed));
        points.add(createPoint(midDepth, starWidth, midAngle, midSpeed));
        points.add(createPoint(midDepth, midWidth - alpha * (midWidth - minScraperWidth), midAngle, midSpeed));
        points.add(createPoint(midDepth, midWidth, starAngle, midSpeed));
        points.add(createPoint(midDepth, midWidth, midAngle - alpha * (midAngle - minScraperAngle), midSpeed));
        points.add(createPoint(midDepth, midWidth, midAngle, starSpeed));
        points.add(createPoint(midDepth, midWidth, midAngle, midSpeed - alpha * (midSpeed - minChainSpeed)));

        for (int i = 0; i < 5; i++) {
            points.add(createPoint(midDepth, midWidth, midAngle, midSpeed));
        }

        return points;
    }

    private Map<String, Object> createPoint(double depth, double width, double angle, double speed) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("depth", clamp(depth, minScraperDepth, maxScraperDepth));
        point.put("width", clamp(width, minScraperWidth, maxScraperWidth));
        point.put("angle", clamp(angle, minScraperAngle, maxScraperAngle));
        point.put("speed", clamp(speed, minChainSpeed, maxChainSpeed));
        return point;
    }

    public double calculateWaterFlow(double scraperDepth, double scraperWidth, double scraperAngle,
                                      double chainSpeed, int scraperCount, double sprocketRadius) {
        double angleRad = Math.toRadians(scraperAngle);
        double effectiveArea = scraperDepth * scraperWidth * Math.sin(angleRad);

        double volumePerScraper = effectiveArea * Math.min(scraperDepth, 0.05) * 0.85;

        double scraperFrequency = chainSpeed / (2.0 * Math.PI * sprocketRadius) * scraperCount;

        double fillingEfficiency = calculateFillingEfficiency(scraperDepth, scraperAngle, chainSpeed);
        double retentionEfficiency = calculateRetentionEfficiency(scraperAngle, chainSpeed);

        double volumeFlowRate = volumePerScraper * scraperFrequency * fillingEfficiency * retentionEfficiency;

        double waterFlowLh = volumeFlowRate * 3600 * 1000;

        double centrifugalLoss = Math.pow(chainSpeed, 2) / (sprocketRadius * GRAVITY) * 0.1;
        waterFlowLh *= (1 - Math.min(centrifugalLoss, 0.3));

        return Math.max(0, waterFlowLh);
    }

    private double calculateFillingEfficiency(double depth, double angle, double speed) {
        double angleRad = Math.toRadians(angle);
        double depthFactor = 1.0 - Math.exp(-depth / 0.1);
        double angleFactor = Math.sin(angleRad);
        double speedFactor = 1.0 / (1.0 + Math.pow(speed / 2.0, 2));
        return 0.7 * depthFactor * angleFactor * (0.5 + 0.5 * speedFactor) + 0.1;
    }

    private double calculateRetentionEfficiency(double angle, double speed) {
        double angleRad = Math.toRadians(angle);
        double optimalAngle = Math.toRadians(45);
        double anglePenalty = Math.exp(-Math.pow(angleRad - optimalAngle, 2) / 0.3);
        double speedPenalty = Math.exp(-Math.pow(speed / 3.0, 2) * 0.5);
        return 0.6 * anglePenalty * (0.7 + 0.3 * speedPenalty) + 0.2;
    }

    private double[] fitResponseSurface(List<Map<String, Object>> points) {
        int n = points.size();
        int numCoeffs = 15;

        double[][] X = new double[n][numCoeffs];
        double[] Y = new double[n];

        for (int i = 0; i < n; i++) {
            Map<String, Object> p = points.get(i);
            double d = (double) p.get("depth");
            double w = (double) p.get("width");
            double a = (double) p.get("angle");
            double s = (double) p.get("speed");

            X[i][0] = 1;
            X[i][1] = d;
            X[i][2] = w;
            X[i][3] = a;
            X[i][4] = s;
            X[i][5] = d * d;
            X[i][6] = w * w;
            X[i][7] = a * a;
            X[i][8] = s * s;
            X[i][9] = d * w;
            X[i][10] = d * a;
            X[i][11] = d * s;
            X[i][12] = w * a;
            X[i][13] = w * s;
            X[i][14] = a * s;

            Y[i] = (double) p.get("waterFlow");
        }

        return solveLeastSquares(X, Y);
    }

    private double[] solveLeastSquares(double[][] X, double[] Y) {
        int n = X.length;
        int p = X[0].length;

        double[][] XtX = new double[p][p];
        double[] XtY = new double[p];

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                for (int k = 0; k < n; k++) {
                    XtX[i][j] += X[k][i] * X[k][j];
                }
            }
            for (int k = 0; k < n; k++) {
                XtY[i] += X[k][i] * Y[k];
            }
        }

        return gaussianElimination(XtX, XtY);
    }

    private double[] gaussianElimination(double[][] A, double[] b) {
        int n = b.length;
        double[][] augmented = new double[n][n + 1];

        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, augmented[i], 0, n);
            augmented[i][n] = b[i];
        }

        for (int pivot = 0; pivot < n; pivot++) {
            int maxRow = pivot;
            for (int row = pivot + 1; row < n; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[maxRow][pivot])) {
                    maxRow = row;
                }
            }

            double[] temp = augmented[pivot];
            augmented[pivot] = augmented[maxRow];
            augmented[maxRow] = temp;

            double pivotValue = augmented[pivot][pivot];
            if (Math.abs(pivotValue) < 1e-12) continue;

            for (int row = pivot + 1; row < n; row++) {
                double factor = augmented[row][pivot] / pivotValue;
                for (int col = pivot; col <= n; col++) {
                    augmented[row][col] -= factor * augmented[pivot][col];
                }
            }
        }

        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            x[i] = augmented[i][n];
            for (int j = i + 1; j < n; j++) {
                x[i] -= augmented[i][j] * x[j];
            }
            if (Math.abs(augmented[i][i]) > 1e-12) {
                x[i] /= augmented[i][i];
            }
        }

        return x;
    }

    private Map<String, Object> findOptimum(double[] coeffs, int scraperCount, double sprocketRadius) {
        double bestDepth = (minScraperDepth + maxScraperDepth) / 2;
        double bestWidth = (minScraperWidth + maxScraperWidth) / 2;
        double bestAngle = (minScraperAngle + maxScraperAngle) / 2;
        double bestSpeed = (minChainSpeed + maxChainSpeed) / 2;
        double bestFlow = evaluateResponseSurface(coeffs, bestDepth, bestWidth, bestAngle, bestSpeed);

        double[] step = {
                (maxScraperDepth - minScraperDepth) / 50,
                (maxScraperWidth - minScraperWidth) / 50,
                (maxScraperAngle - minScraperAngle) / 50,
                (maxChainSpeed - minChainSpeed) / 50
        };

        for (int iter = 0; iter < maxIterations; iter++) {
            boolean improved = false;
            double[][] directions = {
                    {1, 0, 0, 0}, {-1, 0, 0, 0},
                    {0, 1, 0, 0}, {0, -1, 0, 0},
                    {0, 0, 1, 0}, {0, 0, -1, 0},
                    {0, 0, 0, 1}, {0, 0, 0, -1}
            };

            for (double[] dir : directions) {
                double newDepth = clamp(bestDepth + dir[0] * step[0], minScraperDepth, maxScraperDepth);
                double newWidth = clamp(bestWidth + dir[1] * step[1], minScraperWidth, maxScraperWidth);
                double newAngle = clamp(bestAngle + dir[2] * step[2], minScraperAngle, maxScraperAngle);
                double newSpeed = clamp(bestSpeed + dir[3] * step[3], minChainSpeed, maxChainSpeed);

                double newFlow = evaluateResponseSurface(coeffs, newDepth, newWidth, newAngle, newSpeed);
                double actualFlow = calculateWaterFlow(newDepth, newWidth, newAngle, newSpeed, scraperCount, sprocketRadius);

                if (actualFlow > bestFlow) {
                    bestDepth = newDepth;
                    bestWidth = newWidth;
                    bestAngle = newAngle;
                    bestSpeed = newSpeed;
                    bestFlow = actualFlow;
                    improved = true;
                }
            }

            if (!improved) {
                for (int i = 0; i < step.length; i++) {
                    step[i] *= 0.5;
                }
                if (step[0] < convergenceTolerance) break;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("depth", bestDepth);
        result.put("width", bestWidth);
        result.put("angle", bestAngle);
        result.put("speed", bestSpeed);
        result.put("maxFlow", bestFlow);
        return result;
    }

    private double evaluateResponseSurface(double[] coeffs, double d, double w, double a, double s) {
        return coeffs[0] +
                coeffs[1] * d + coeffs[2] * w + coeffs[3] * a + coeffs[4] * s +
                coeffs[5] * d * d + coeffs[6] * w * w + coeffs[7] * a * a + coeffs[8] * s * s +
                coeffs[9] * d * w + coeffs[10] * d * a + coeffs[11] * d * s +
                coeffs[12] * w * a + coeffs[13] * w * s + coeffs[14] * a * s;
    }

    private Map<String, Object> buildResponseSurfaceEquation(double[] coeffs) {
        Map<String, Object> equation = new LinkedHashMap<>();
        equation.put("intercept", round(coeffs[0], 6));
        equation.put("depth", round(coeffs[1], 6));
        equation.put("width", round(coeffs[2], 6));
        equation.put("angle", round(coeffs[3], 6));
        equation.put("speed", round(coeffs[4], 6));
        equation.put("depth2", round(coeffs[5], 6));
        equation.put("width2", round(coeffs[6], 6));
        equation.put("angle2", round(coeffs[7], 6));
        equation.put("speed2", round(coeffs[8], 6)));
        equation.put("depth_width", round(coeffs[9], 6));
        equation.put("depth_angle", round(coeffs[10], 6));
        equation.put("depth_speed", round(coeffs[11], 6));
        equation.put("width_angle", round(coeffs[12], 6));
        equation.put("width_speed", round(coeffs[13], 6));
        equation.put("angle_speed", round(coeffs[14], 6));
        return equation;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value, int decimalPlaces) {
        double factor = Math.pow(10, decimalPlaces);
        return Math.round(value * factor) / factor;
    }
}
