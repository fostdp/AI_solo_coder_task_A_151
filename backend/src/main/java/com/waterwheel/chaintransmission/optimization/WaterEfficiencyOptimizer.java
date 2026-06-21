package com.waterwheel.chaintransmission.optimization;

import com.waterwheel.chaintransmission.efficiency_optimizer.config.ChainSpeedOptimizationProperties;
import com.waterwheel.chaintransmission.efficiency_optimizer.config.ResponseSurfaceProperties;
import com.waterwheel.chaintransmission.efficiency_optimizer.config.ScraperOptimizationProperties;
import com.waterwheel.chaintransmission.dto.OptimizationResultDTO;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class WaterEfficiencyOptimizer {

    @Autowired
    private ResponseSurfaceProperties responseSurfaceProperties;

    @Autowired
    private ScraperOptimizationProperties scraperProps;

    @Autowired
    private ChainSpeedOptimizationProperties chainSpeedProps;

    private static final int NUM_DESIGN_VARS = 4;
    private static final double GRAVITY = 9.81;
    private static final double WATER_DENSITY = 1000.0;

    public OptimizationResultDTO optimize(WaterwheelDevice device, double currentWaterFlow) {
        log.info("开始提水效率优化, 设备: {}, 当前提水量: {} L/h", device.getDeviceName(), currentWaterFlow);

        int designPoints = responseSurfaceProperties.getDesignPoints();
        int maxIterations = responseSurfaceProperties.getMaxIterations();
        double convergenceTolerance = responseSurfaceProperties.getConvergenceTolerance();
        double minScraperDepth = scraperProps.getMinDepth();
        double maxScraperDepth = scraperProps.getMaxDepth();
        double minScraperWidth = scraperProps.getMinWidth();
        double maxScraperWidth = scraperProps.getMaxWidth();
        double minScraperAngle = scraperProps.getMinAngle();
        double maxScraperAngle = scraperProps.getMaxAngle();
        double minChainSpeed = chainSpeedProps.getMin();
        double maxChainSpeed = chainSpeedProps.getMax();

        int scraperCount = device.getScraperCount() != null ? device.getScraperCount() : 24;
        double sprocketRadius = device.getSprocketRadius() != null ?
                device.getSprocketRadius().doubleValue() : 0.35;

        List<Map<String, Object>> evaluatedPoints = new ArrayList<>();

        List<Map<String, Object>> designPointsData = generateBoundaryAwareCCD();
        addBoundaryAugmentedPoints(designPointsData);

        for (Map<String, Object> point : designPointsData) {
            double depth = (double) point.get("depth");
            double width = (double) point.get("width");
            double angle = (double) point.get("angle");
            double speed = (double) point.get("speed");

            double waterFlow = calculateWaterFlow(depth, width, angle, speed, scraperCount, sprocketRadius);
            point.put("waterFlow", waterFlow);
            computePointWeight(point);
            evaluatedPoints.add(point);
        }

        double[] coefficients = fitWeightedResponseSurface(evaluatedPoints);

        List<Map<String, Object>> boundaryRefinement = adaptiveBoundaryRefinement(
                coefficients, evaluatedPoints, scraperCount, sprocketRadius);
        if (!boundaryRefinement.isEmpty()) {
            evaluatedPoints.addAll(boundaryRefinement);
            coefficients = fitWeightedResponseSurface(evaluatedPoints);
        }

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
        result.setMethod("ResponseSurfaceMethod_AdaptiveBoundaryWeighted");
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

    private List<Map<String, Object>> generateBoundaryAwareCCD() {
        List<Map<String, Object>> points = new ArrayList<>();

        double minScraperDepth = scraperProps.getMinDepth();
        double maxScraperDepth = scraperProps.getMaxDepth();
        double minScraperWidth = scraperProps.getMinWidth();
        double maxScraperWidth = scraperProps.getMaxWidth();
        double minScraperAngle = scraperProps.getMinAngle();
        double maxScraperAngle = scraperProps.getMaxAngle();
        double minChainSpeed = chainSpeedProps.getMin();
        double maxChainSpeed = chainSpeedProps.getMax();

        double midDepth = (minScraperDepth + maxScraperDepth) / 2;
        double midWidth = (minScraperWidth + maxScraperWidth) / 2;
        double midAngle = (minScraperAngle + maxScraperAngle) / 2;
        double midSpeed = (minChainSpeed + maxChainSpeed) / 2;

        double rangeDepth = maxScraperDepth - minScraperDepth;
        double rangeWidth = maxScraperWidth - minScraperWidth;
        double rangeAngle = maxScraperAngle - minScraperAngle;
        double rangeSpeed = maxChainSpeed - minChainSpeed;

        double alpha = Math.pow(2, 4.0 / 4);
        double clampedAlpha = Math.min(alpha, 1.0);

        int[][] factorialSigns = {
                {-1, -1, -1, -1}, {1, -1, -1, -1}, {-1, 1, -1, -1}, {1, 1, -1, -1},
                {-1, -1, 1, -1}, {1, -1, 1, -1}, {-1, 1, 1, -1}, {1, 1, 1, -1},
                {-1, -1, -1, 1}, {1, -1, -1, 1}, {-1, 1, -1, 1}, {1, 1, -1, 1},
                {-1, -1, 1, 1}, {1, -1, 1, 1}, {-1, 1, 1, 1}, {1, 1, 1, 1}
        };
        for (int[] s : factorialSigns) {
            points.add(createPoint(
                    midDepth + s[0] * rangeDepth * 0.5,
                    midWidth + s[1] * rangeWidth * 0.5,
                    midAngle + s[2] * rangeAngle * 0.5,
                    midSpeed + s[3] * rangeSpeed * 0.5
            ));
        }

        double[][] axialOffsets = {
                {1, 0, 0, 0}, {-1, 0, 0, 0},
                {0, 1, 0, 0}, {0, -1, 0, 0},
                {0, 0, 1, 0}, {0, 0, -1, 0},
                {0, 0, 0, 1}, {0, 0, 0, -1}
        };
        for (double[] o : axialOffsets) {
            points.add(createPoint(
                    midDepth + o[0] * rangeDepth * 0.5 * clampedAlpha,
                    midWidth + o[1] * rangeWidth * 0.5 * clampedAlpha,
                    midAngle + o[2] * rangeAngle * 0.5 * clampedAlpha,
                    midSpeed + o[3] * rangeSpeed * 0.5 * clampedAlpha
            ));
        }

        for (int i = 0; i < 5; i++) {
            points.add(createPoint(midDepth, midWidth, midAngle, midSpeed));
        }

        return points;
    }

    private void addBoundaryAugmentedPoints(List<Map<String, Object>> points) {
        double minScraperDepth = scraperProps.getMinDepth();
        double maxScraperDepth = scraperProps.getMaxDepth();
        double minScraperWidth = scraperProps.getMinWidth();
        double maxScraperWidth = scraperProps.getMaxWidth();
        double minScraperAngle = scraperProps.getMinAngle();
        double maxScraperAngle = scraperProps.getMaxAngle();
        double minChainSpeed = chainSpeedProps.getMin();
        double maxChainSpeed = chainSpeedProps.getMax();

        double[][] boundaryAnchors = {
                {minScraperDepth, (minScraperWidth + maxScraperWidth) / 2, (minScraperAngle + maxScraperAngle) / 2, (minChainSpeed + maxChainSpeed) / 2},
                {maxScraperDepth, (minScraperWidth + maxScraperWidth) / 2, (minScraperAngle + maxScraperAngle) / 2, (minChainSpeed + maxChainSpeed) / 2},
                {(minScraperDepth + maxScraperDepth) / 2, minScraperWidth, (minScraperAngle + maxScraperAngle) / 2, (minChainSpeed + maxChainSpeed) / 2},
                {(minScraperDepth + maxScraperDepth) / 2, maxScraperWidth, (minScraperAngle + maxScraperAngle) / 2, (minChainSpeed + maxChainSpeed) / 2},
                {(minScraperDepth + maxScraperDepth) / 2, (minScraperWidth + maxScraperWidth) / 2, minScraperAngle, (minChainSpeed + maxChainSpeed) / 2},
                {(minScraperDepth + maxScraperDepth) / 2, (minScraperWidth + maxScraperWidth) / 2, maxScraperAngle, (minChainSpeed + maxChainSpeed) / 2},
                {(minScraperDepth + maxScraperDepth) / 2, (minScraperWidth + maxScraperWidth) / 2, (minScraperAngle + maxScraperAngle) / 2, minChainSpeed},
                {(minScraperDepth + maxScraperDepth) / 2, (minScraperWidth + maxScraperWidth) / 2, (minScraperAngle + maxScraperAngle) / 2, maxChainSpeed}
        };
        for (double[] a : boundaryAnchors) {
            points.add(createPoint(a[0], a[1], a[2], a[3]));
        }

        double[][] cornerFaces = {
                {minScraperDepth, minScraperWidth, minScraperAngle, (minChainSpeed + maxChainSpeed) / 2},
                {maxScraperDepth, maxScraperWidth, maxScraperAngle, (minChainSpeed + maxChainSpeed) / 2},
                {minScraperDepth, maxScraperWidth, (minScraperAngle + maxScraperAngle) / 2, minChainSpeed},
                {maxScraperDepth, minScraperWidth, (minScraperAngle + maxScraperAngle) / 2, maxChainSpeed}
        };
        for (double[] c : cornerFaces) {
            points.add(createPoint(c[0], c[1], c[2], c[3]));
        }
    }

    private void computePointWeight(Map<String, Object> point) {
        double minScraperDepth = scraperProps.getMinDepth();
        double maxScraperDepth = scraperProps.getMaxDepth();
        double minScraperWidth = scraperProps.getMinWidth();
        double maxScraperWidth = scraperProps.getMaxWidth();
        double minScraperAngle = scraperProps.getMinAngle();
        double maxScraperAngle = scraperProps.getMaxAngle();
        double minChainSpeed = chainSpeedProps.getMin();
        double maxChainSpeed = chainSpeedProps.getMax();

        double d = (double) point.get("depth");
        double w = (double) point.get("width");
        double a = (double) point.get("angle");
        double s = (double) point.get("speed");

        double midDepth = (minScraperDepth + maxScraperDepth) / 2;
        double midWidth = (minScraperWidth + maxScraperWidth) / 2;
        double midAngle = (minScraperAngle + maxScraperAngle) / 2;
        double midSpeed = (minChainSpeed + maxChainSpeed) / 2;

        double rangeDepth = maxScraperDepth - minScraperDepth;
        double rangeWidth = maxScraperWidth - minScraperWidth;
        double rangeAngle = maxScraperAngle - minScraperAngle;
        double rangeSpeed = maxChainSpeed - minChainSpeed;

        double normDist = Math.sqrt(
                Math.pow((d - midDepth) / (rangeDepth / 2), 2) +
                Math.pow((w - midWidth) / (rangeWidth / 2), 2) +
                Math.pow((a - midAngle) / (rangeAngle / 2), 2) +
                Math.pow((s - midSpeed) / (rangeSpeed / 2), 2)
        );

        double boundaryProximity = Math.max(0, normDist - 0.9);
        double weight = 1.0 + boundaryProximity * responseSurfaceProperties.getBoundaryWeightBoost();
        point.put("weight", weight);
    }

    private List<Map<String, Object>> adaptiveBoundaryRefinement(
            double[] coefficients, List<Map<String, Object>> existingPoints,
            int scraperCount, double sprocketRadius) {

        double minScraperDepth = scraperProps.getMinDepth();
        double maxScraperDepth = scraperProps.getMaxDepth();
        double minScraperWidth = scraperProps.getMinWidth();
        double maxScraperWidth = scraperProps.getMaxWidth();
        double minScraperAngle = scraperProps.getMinAngle();
        double maxScraperAngle = scraperProps.getMaxAngle();
        double minChainSpeed = chainSpeedProps.getMin();
        double maxChainSpeed = chainSpeedProps.getMax();
        double residualThreshold = responseSurfaceProperties.getAdaptiveResidualThreshold();
        int refinementDirections = responseSurfaceProperties.getAdaptiveRefinementDirections();

        List<Map<String, Object>> newPoints = new ArrayList<>();
        double maxResidual = 0;
        Map<String, Object> worstPoint = null;
        double predictedWorst = 0;
        double actualWorst = 0;

        for (Map<String, Object> p : existingPoints) {
            double d = (double) p.get("depth");
            double w = (double) p.get("width");
            double a = (double) p.get("angle");
            double s = (double) p.get("speed");
            double actual = (double) p.get("waterFlow");
            double predicted = evaluateResponseSurface(coefficients, d, w, a, s);
            double residual = Math.abs(actual - predicted) / (actual + 1e-6);

            double midDepth = (minScraperDepth + maxScraperDepth) / 2;
            double midWidth = (minScraperWidth + maxScraperWidth) / 2;
            double midAngle = (minScraperAngle + maxScraperAngle) / 2;
            double midSpeed = (minChainSpeed + maxChainSpeed) / 2;
            double boundaryFactor =
                    Math.abs(d - midDepth) / (maxScraperDepth - minScraperDepth) +
                    Math.abs(w - midWidth) / (maxScraperWidth - minScraperWidth) +
                    Math.abs(a - midAngle) / (maxScraperAngle - minScraperAngle) +
                    Math.abs(s - midSpeed) / (maxChainSpeed - minChainSpeed);
            boundaryFactor /= 2.0;

            double weightedResidual = residual * (0.5 + boundaryFactor);
            if (weightedResidual > maxResidual) {
                maxResidual = weightedResidual;
                worstPoint = p;
                predictedWorst = predicted;
                actualWorst = actual;
            }
        }

        if (maxResidual > residualThreshold && worstPoint != null) {
            log.info("检测到边界高残差点 (残差={}%), 执行自适应补采样...", round(maxResidual * 100, 2));
            double d = (double) worstPoint.get("depth");
            double w = (double) worstPoint.get("width");
            double a = (double) worstPoint.get("angle");
            double s = (double) worstPoint.get("speed");

            double[] stepSize = {
                    (maxScraperDepth - minScraperDepth) / 15,
                    (maxScraperWidth - minScraperWidth) / 15,
                    (maxScraperAngle - minScraperAngle) / 15,
                    (maxChainSpeed - minChainSpeed) / 15
            };

            double[][] perturbDirs = buildPerturbationDirections(refinementDirections);

            for (double[] dir : perturbDirs) {
                double nd = clamp(d + dir[0] * stepSize[0], minScraperDepth, maxScraperDepth);
                double nw = clamp(w + dir[1] * stepSize[1], minScraperWidth, maxScraperWidth);
                double na = clamp(a + dir[2] * stepSize[2], minScraperAngle, maxScraperAngle);
                double ns = clamp(s + dir[3] * stepSize[3], minChainSpeed, maxChainSpeed);

                boolean duplicate = existingPoints.stream().anyMatch(p2 ->
                        Math.abs((double) p2.get("depth") - nd) < 1e-5 &&
                        Math.abs((double) p2.get("width") - nw) < 1e-5 &&
                        Math.abs((double) p2.get("angle") - na) < 1e-5 &&
                        Math.abs((double) p2.get("speed") - ns) < 1e-5);
                if (duplicate) continue;

                Map<String, Object> np = createPoint(nd, nw, na, ns);
                double flow = calculateWaterFlow(nd, nw, na, ns, scraperCount, sprocketRadius);
                np.put("waterFlow", flow);
                np.put("weight", 2.0);
                np.put("adaptiveRefinement", true);
                newPoints.add(np);
            }
        }

        return newPoints;
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

    private double[] fitWeightedResponseSurface(List<Map<String, Object>> points) {
        int n = points.size();
        int numCoeffs = 15;

        double[][] X = new double[n][numCoeffs];
        double[] Y = new double[n];
        double[] W = new double[n];

        for (int i = 0; i < n; i++) {
            Map<String, Object> p = points.get(i);
            double d = (double) p.get("depth");
            double w = (double) p.get("width");
            double a = (double) p.get("angle");
            double s = (double) p.get("speed");
            double weight = p.containsKey("weight") ? ((Number) p.get("weight")).doubleValue() : 1.0;
            double sqrtW = Math.sqrt(weight);

            X[i][0] = sqrtW;
            X[i][1] = d * sqrtW;
            X[i][2] = w * sqrtW;
            X[i][3] = a * sqrtW;
            X[i][4] = s * sqrtW;
            X[i][5] = d * d * sqrtW;
            X[i][6] = w * w * sqrtW;
            X[i][7] = a * a * sqrtW;
            X[i][8] = s * s * sqrtW;
            X[i][9] = d * w * sqrtW;
            X[i][10] = d * a * sqrtW;
            X[i][11] = d * s * sqrtW;
            X[i][12] = w * a * sqrtW;
            X[i][13] = w * s * sqrtW;
            X[i][14] = a * s * sqrtW;

            Y[i] = (double) p.get("waterFlow") * sqrtW;
        }

        return solveLeastSquares(X, Y);
    }

    private double[] fitResponseSurface(List<Map<String, Object>> points) {
        return fitWeightedResponseSurface(points);
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

    private double[][] buildPerturbationDirections(int count) {
        List<double[]> directions = new ArrayList<>();
        double[][] baseAxis = {
                {1, 0, 0, 0}, {-1, 0, 0, 0},
                {0, 1, 0, 0}, {0, -1, 0, 0},
                {0, 0, 1, 0}, {0, 0, -1, 0},
                {0, 0, 0, 1}, {0, 0, 0, -1}
        };
        double[][] pairedAxis = {
                {1, 1, 0, 0}, {-1, -1, 0, 0},
                {0, 0, 1, 1}, {0, 0, -1, -1}
        };
        for (double[] d : baseAxis) directions.add(d);
        for (double[] d : pairedAxis) {
            if (directions.size() >= count) break;
            directions.add(d);
        }
        return directions.toArray(new double[0][]);
    }
}
