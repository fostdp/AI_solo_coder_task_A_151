const API_BASE = '/api/v1';
let currentDeviceId = null;
let tensionChart, waterFlowChart;

const EfficiencyPanel = {
    dataHandler: null,
    sensorData: {
        tension: [],
        flow: [],
        timestamps: []
    },
    latestOptimization: null,
    pollingEnabled: true,

    init: function() {
        this.initCharts();
        this.bindEvents();
        this.loadDevices();
        this.startPolling();
        this.updateTime();
        setInterval(() => this.updateTime(), 1000);
    },

    bindEvents: function() {
        document.getElementById('runSimBtn').addEventListener('click', () => this.runChainSimulation());
        document.getElementById('runOptimBtn').addEventListener('click', () => this.runOptimization());
        document.getElementById('refreshAlertsBtn').addEventListener('click', () => this.loadAlerts());
    },

    updateTime: function() {
        const el = document.getElementById('currentTime');
        if (el) el.textContent = new Date().toLocaleTimeString('zh-CN');
    },

    initCharts: function() {
        const chartOptions = {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { display: false },
                y: {
                    grid: { color: 'rgba(255,255,255,0.05)' },
                    ticks: { color: '#888', font: { size: 10 } }
                }
            },
            elements: {
                point: { radius: 0 },
                line: { tension: 0.4, borderWidth: 2 }
            },
            animation: { duration: 300 }
        };

        const tensionCtx = document.getElementById('tensionChart');
        if (tensionCtx) {
            tensionChart = new Chart(tensionCtx, {
                type: 'line',
                data: {
                    labels: Array(30).fill(''),
                    datasets: [{
                        data: Array(30).fill(0),
                        borderColor: '#e94560',
                        backgroundColor: 'rgba(233,69,96,0.1)',
                        fill: true
                    }]
                },
                options: chartOptions
            });
        }

        const flowCtx = document.getElementById('waterFlowChart');
        if (flowCtx) {
            waterFlowChart = new Chart(flowCtx, {
                type: 'line',
                data: {
                    labels: Array(30).fill(''),
                    datasets: [{
                        data: Array(30).fill(0),
                        borderColor: '#3498db',
                        backgroundColor: 'rgba(52,152,219,0.1)',
                        fill: true
                    }]
                },
                options: chartOptions
            });
        }
    },

    addTensionDataPoint: function(value) {
        if (!tensionChart || value == null) return;
        tensionChart.data.datasets[0].data.shift();
        tensionChart.data.datasets[0].data.push(value);
        tensionChart.update('none');
        this.sensorData.tension.push(value);
        if (this.sensorData.tension.length > 100) this.sensorData.tension.shift();
    },

    addWaterFlowDataPoint: function(value) {
        if (!waterFlowChart || value == null) return;
        waterFlowChart.data.datasets[0].data.shift();
        waterFlowChart.data.datasets[0].data.push(value);
        waterFlowChart.update('none');
        this.sensorData.flow.push(value);
        if (this.sensorData.flow.length > 100) this.sensorData.flow.shift();
    },

    async loadDevices() {
        try {
            const res = await fetch(API_BASE + '/devices');
            const devices = await res.json();
            const list = document.getElementById('deviceList');
            if (!list) return;
            list.innerHTML = devices.map(d => `
                <div class="device-item ${d.deviceId === currentDeviceId ? 'active' : ''}"
                     onclick="EfficiencyPanel.selectDevice(${d.deviceId})">
                    <div class="device-name">${d.deviceName}</div>
                    <div class="device-location">📍 ${d.location || '未知位置'}</div>
                </div>
            `).join('');
            if (devices.length > 0 && !currentDeviceId) {
                this.selectDevice(devices[0].deviceId);
            }
        } catch (e) {
            console.error('加载设备失败:', e);
        }
    },

    selectDevice: function(deviceId) {
        currentDeviceId = deviceId;
        document.querySelectorAll('.device-item').forEach(el => el.classList.remove('active'));
        const active = document.querySelector(`.device-item[onclick*="${deviceId}"]`);
        if (active) active.classList.add('active');
        this.loadLatestSensorData();
        this.loadAlerts();
    },

    async loadLatestSensorData() {
        if (!currentDeviceId) return;
        try {
            const res = await fetch(API_BASE + `/sensor-data/${currentDeviceId}/latest`);
            if (!res.ok) return;
            const data = await res.json();

            const speedEl = document.getElementById('statSpeed');
            if (speedEl) speedEl.textContent = data.sprocketSpeed?.toFixed(2) || '--';

            const tensionEl = document.getElementById('statTension');
            if (tensionEl) {
                tensionEl.textContent = data.chainTension?.toFixed(1) || '--';
                tensionEl.className = 'stat-card-value ' +
                    (data.chainTension > 10000 ? 'danger' : data.chainTension > 7000 ? 'warning' : '');
            }

            const flowEl = document.getElementById('statFlow');
            if (flowEl) flowEl.textContent = data.waterFlow?.toFixed(1) || '--';

            const loadEl = document.getElementById('statLoad');
            if (loadEl) loadEl.textContent = data.scraperLoad?.toFixed(1) || '--';

            this.addTensionDataPoint(data.chainTension);
            this.addWaterFlowDataPoint(data.waterFlow);
        } catch (e) {
            console.error('加载传感器数据失败:', e);
        }
    },

    async loadAlerts() {
        if (!currentDeviceId) return;
        try {
            const res = await fetch(API_BASE + '/alerts/recent?hours=1');
            const alerts = await res.json();
            const list = document.getElementById('alertList');
            if (!list) return;
            if (alerts.length === 0) {
                list.innerHTML = '<div style="color:#666;font-size:12px;text-align:center;padding:10px">暂无告警</div>';
                return;
            }
            list.innerHTML = alerts.slice(0, 10).map(a => `
                <div class="alert-item ${a.alertLevel}">
                    <div class="alert-type">${a.alertLevel} - ${a.alertType}</div>
                    <div class="alert-msg">${a.alertMessage}</div>
                    <div class="alert-time">${new Date(a.alertTime).toLocaleString('zh-CN')}</div>
                </div>
            `).join('');
        } catch (e) {
            console.error('加载告警失败:', e);
        }
    },

    async runChainSimulation() {
        if (!currentDeviceId) return;
        try {
            const btn = document.getElementById('runSimBtn');
            btn.textContent = '⏳ 仿真计算中...';
            btn.disabled = true;

            const res = await fetch(API_BASE +
                `/simulation/chain-dynamics/${currentDeviceId}?inputSpeedRPM=15.0&inputTorque=50.0`,
                { method: 'POST' });
            const result = await res.json();

            const maxT = document.getElementById('simMaxTension');
            if (maxT) maxT.textContent = result.maxTension + ' N';
            const minT = document.getElementById('simMinTension');
            if (minT) minT.textContent = result.minTension + ' N';
            const avgT = document.getElementById('simAvgTension');
            if (avgT) avgT.textContent = result.avgTension + ' N';

            const resEl = document.getElementById('simResonance');
            if (resEl) {
                resEl.textContent = result.resonanceRisk ? '⚠️ 有风险' : '✓ 正常';
                resEl.className = 'data-value ' + (result.resonanceRisk ? 'warning' : 'success');
            }
            const freqEl = document.getElementById('simFreq');
            if (freqEl) freqEl.textContent = (result.vibrationFrequencies?.[0] || '--') + ' Hz';
            const durEl = document.getElementById('simDuration');
            if (durEl) durEl.textContent = result.simulationDurationMs + ' ms';

            if (result.linkPositions && typeof window.ChainPump3D !== 'undefined') {
                window.ChainPump3D.updateChainLinkPositions(result.linkPositions);
            }

            btn.textContent = '🔬 运行链传动动力学仿真';
            btn.disabled = false;
        } catch (e) {
            console.error('仿真运行失败:', e);
            const btn = document.getElementById('runSimBtn');
            if (btn) {
                btn.textContent = '🔬 运行链传动动力学仿真';
                btn.disabled = false;
            }
        }
    },

    async runOptimization() {
        if (!currentDeviceId) return;
        try {
            const btn = document.getElementById('runOptimBtn');
            btn.textContent = '⏳ 优化计算中...';
            btn.disabled = true;

            const res = await fetch(API_BASE + `/optimization/efficiency/${currentDeviceId}`, { method: 'POST' });
            const result = await res.json();
            this.latestOptimization = result;

            const container = document.getElementById('optimizationResult');
            if (container) {
                container.innerHTML = `
                    <div class="data-row"><span class="data-label">最优刮板深度</span><span class="data-value">${result.optimalScraperDepth} m</span></div>
                    <div class="data-row"><span class="data-label">最优刮板宽度</span><span class="data-value">${result.optimalScraperWidth} m</span></div>
                    <div class="data-row"><span class="data-label">最优刮板角度</span><span class="data-value">${result.optimalScraperAngle} °</span></div>
                    <div class="data-row"><span class="data-label">最优链速</span><span class="data-value">${result.optimalChainSpeed} m/s</span></div>
                    <div class="data-row"><span class="data-label">预测最大提水量</span><span class="data-value success">${result.predictedMaxWaterFlow} L/h</span></div>
                    <div class="data-row"><span class="data-label">效率提升</span><span class="data-value success">${result.efficiencyImprovement} %</span></div>
                    <div class="data-row"><span class="data-label">迭代收敛</span><span class="data-value">${result.convergence ? '✓ 是' : '✗ 否'}</span></div>
                    <div class="data-row"><span class="data-label">迭代次数</span><span class="data-value">${result.iterations}</span></div>
                `;
            }

            if (result.responseSurfaceEquation) {
                this.displayResponseSurface(result.responseSurfaceEquation);
            }

            btn.textContent = '📈 执行提水效率优化';
            btn.disabled = false;
        } catch (e) {
            console.error('优化运行失败:', e);
            const btn = document.getElementById('runOptimBtn');
            if (btn) {
                btn.textContent = '📈 执行提水效率优化';
                btn.disabled = false;
            }
        }
    },

    displayResponseSurface: function(equation) {
        const container = document.getElementById('responseSurfaceDisplay');
        if (!container) return;

        const terms = [];
        const labels = {
            intercept: '常数项',
            depth: '刮板深度',
            width: '刮板宽度',
            angle: '刮板角度',
            speed: '链速',
            depth2: '深度²',
            width2: '宽度²',
            angle2: '角度²',
            speed2: '链速²',
            depth_width: '深度×宽度',
            depth_angle: '深度×角度',
            depth_speed: '深度×链速',
            width_angle: '宽度×角度',
            width_speed: '宽度×链速',
            angle_speed: '角度×链速'
        };

        Object.keys(equation).forEach(key => {
            if (Math.abs(equation[key]) > 1e-10) {
                const label = labels[key] || key;
                terms.push(`<div class="data-row"><span class="data-label">${label}</span><span class="data-value">${equation[key].toFixed(6)}</span></div>`);
            }
        });

        container.innerHTML = `
            <h4 style="margin:10px 0;color:#e94560;font-size:14px">响应面回归系数</h4>
            ${terms.join('')}
        `;
    },

    startPolling: function() {
        setInterval(() => {
            if (this.pollingEnabled) {
                this.loadLatestSensorData();
            }
        }, 5000);
        setInterval(() => {
            if (this.pollingEnabled) {
                this.loadAlerts();
            }
        }, 10000);
    }
};

window.EfficiencyPanel = EfficiencyPanel;

window.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('optimizationResult') || document.getElementById('deviceList')) {
        EfficiencyPanel.init();
    }
});
