class WaterwheelDataHandler {
    constructor(apiBase) {
        this.apiBase = apiBase;
        this.currentDeviceId = null;
        this.subscribers = {};
    }

    async fetchDevices() {
        return this._request('/devices');
    }

    async fetchLatestSensorData(deviceId) {
        return this._request(`/sensor-data/${deviceId}/latest`);
    }

    async fetchSensorData(deviceId, startTime, endTime) {
        const params = new URLSearchParams({
            startTime: startTime.toISOString(),
            endTime: endTime.toISOString()
        });
        return this._request(`/sensor-data/${deviceId}?${params}`);
    }

    async fetchRecentSensorData(deviceId, limit = 100) {
        return this._request(`/sensor-data/${deviceId}/recent?limit=${limit}`);
    }

    async fetchSensorStatistics(deviceId, hours = 24) {
        return this._request(`/sensor-data/${deviceId}/statistics?hours=${hours}`);
    }

    async runChainSimulation(deviceId, inputSpeedRPM = 15, inputTorque = 50) {
        return this._request(
            `/simulation/chain-dynamics/${deviceId}?inputSpeedRPM=${inputSpeedRPM}&inputTorque=${inputTorque}`,
            { method: 'POST' }
        );
    }

    async fetchSimulationHistory(deviceId) {
        return this._request(`/simulation/chain-dynamics/${deviceId}/history`);
    }

    async runOptimization(deviceId) {
        return this._request(`/optimization/efficiency/${deviceId}`, { method: 'POST' });
    }

    async fetchOptimizationHistory(deviceId) {
        return this._request(`/optimization/efficiency/${deviceId}/history`);
    }

    async fetchAlerts(deviceId) {
        return this._request(`/alerts/device/${deviceId}`);
    }

    async fetchRecentAlerts(hours = 24) {
        return this._request(`/alerts/recent?hours=${hours}`);
    }

    async fetchUnacknowledgedAlerts(limit = 50) {
        return this._request(`/alerts/unacknowledged?limit=${limit}`);
    }

    async acknowledgeAlert(alertId) {
        return this._request(`/alerts/${alertId}/acknowledge`, { method: 'PUT' });
    }

    async fetchDeviceConfigs(deviceId) {
        return this._request(`/configs/${deviceId}`);
    }

    async fetchChainParams(deviceId) {
        return this._request(`/chain-params/${deviceId}`);
    }

    async checkHealth() {
        return this._request('/health');
    }

    on(event, callback) {
        if (!this.subscribers[event]) {
            this.subscribers[event] = [];
        }
        this.subscribers[event].push(callback);
    }

    emit(event, data) {
        if (this.subscribers[event]) {
            this.subscribers[event].forEach(cb => cb(data));
        }
    }

    async _request(endpoint, options = {}) {
        try {
            const response = await fetch(this.apiBase + endpoint, {
                headers: { 'Content-Type': 'application/json' },
                ...options
            });
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return await response.json();
        } catch (error) {
            console.error(`API请求失败 [${endpoint}]:`, error);
            throw error;
        }
    }

    startDataPolling(deviceId, interval = 5000) {
        this.currentDeviceId = deviceId;
        if (this.pollingInterval) clearInterval(this.pollingInterval);

        this.pollingInterval = setInterval(async () => {
            try {
                const data = await this.fetchLatestSensorData(deviceId);
                this.emit('sensorData', data);
            } catch (e) {
                this.emit('error', e);
            }
        }, interval);
    }

    stopPolling() {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
        }
    }
}

if (typeof window !== 'undefined') {
    window.WaterwheelDataHandler = WaterwheelDataHandler;
}
