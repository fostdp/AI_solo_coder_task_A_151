#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
古代水转翻车传感器模拟器
Ancient Waterwheel Sensor Data Simulator

模拟宋代水转翻车传感器每小时上报数据：
- 链轮转速 (sprocket_speed)
- 刮板载荷 (scraper_load)
- 链条张力 (chain_tension)
- 提水量 (water_flow)
- 振动幅度 (vibration_amplitude)
- 扭矩 (torque)
"""

import json
import time
import random
import math
import argparse
import threading
from datetime import datetime, timezone, timedelta
from typing import Dict, Optional
import urllib.request
import urllib.error
import ssl

try:
    import paho.mqtt.client as mqtt
    MQTT_AVAILABLE = True
except ImportError:
    MQTT_AVAILABLE = False
    print("警告: paho-mqtt未安装，MQTT功能不可用。运行: pip install paho-mqtt")

CST = timezone(timedelta(hours=8))

DEVICE_CONFIGS = {
    1: {
        "name": "宋代翻车一号",
        "base_speed": 14.5,
        "speed_variance": 1.5,
        "base_load": 350.0,
        "base_tension": 5500.0,
        "tension_variance": 800.0,
        "base_flow": 750.0,
        "flow_variance": 100.0,
        "scraper_count": 24,
        "sprocket_radius": 0.35,
        "wear_rate": 0.0001,
        "degradation_rate": 0.00005,
        "allowable_tension": 15000.0
    },
    2: {
        "name": "宋代翻车二号",
        "base_speed": 16.0,
        "speed_variance": 1.8,
        "base_load": 420.0,
        "base_tension": 6200.0,
        "tension_variance": 900.0,
        "base_flow": 820.0,
        "flow_variance": 120.0,
        "scraper_count": 28,
        "sprocket_radius": 0.40,
        "wear_rate": 0.00008,
        "degradation_rate": 0.00004,
        "allowable_tension": 17000.0
    },
    3: {
        "name": "宋代翻车三号",
        "base_speed": 12.0,
        "speed_variance": 1.2,
        "base_load": 280.0,
        "base_tension": 4800.0,
        "tension_variance": 700.0,
        "base_flow": 580.0,
        "flow_variance": 80.0,
        "scraper_count": 20,
        "sprocket_radius": 0.30,
        "wear_rate": 0.00012,
        "degradation_rate": 0.00006,
        "allowable_tension": 13000.0
    }
}


class WaterwheelSensorSimulator:
    def __init__(
        self,
        api_base: str = "http://localhost:8080/api/v1",
        mqtt_broker: Optional[str] = None,
        mqtt_port: int = 1883,
        mqtt_topic: str = "waterwheel/sensor",
        interval_seconds: int = 5,
        accelerated: bool = False,
        enable_anomalies: bool = True
    ):
        self.api_base = api_base.rstrip("/")
        self.mqtt_broker = mqtt_broker
        self.mqtt_port = mqtt_port
        self.mqtt_topic = mqtt_topic
        self.interval = interval_seconds
        self.accelerated = accelerated
        self.enable_anomalies = enable_anomalies
        self.running = False
        self.device_states: Dict[int, Dict] = {}
        self.mqtt_client = None
        self._init_device_states()

        if mqtt_broker and MQTT_AVAILABLE:
            self._setup_mqtt()

    def _init_device_states(self):
        for device_id in DEVICE_CONFIGS:
            self.device_states[device_id] = {
                "operation_hours": 0.0,
                "wear_factor": 1.0,
                "water_level_factor": 1.0,
                "current_anomaly": None,
                "anomaly_duration": 0
            }

    def _setup_mqtt(self):
        try:
            self.mqtt_client = mqtt.Client(
                client_id=f"waterwheel_simulator_{random.randint(1000, 9999)")
            self.mqtt_client.connect(self.mqtt_broker, self.mqtt_port, 60)
            self.mqtt_client.loop_start()
            print(f"MQTT已连接到 {self.mqtt_broker}:{self.mqtt_port}")
        except Exception as e:
            print(f"MQTT连接失败: {e}")
            self.mqtt_client = None

    def _generate_sensor_data(self, device_id: int) -> Dict:
        cfg = DEVICE_CONFIGS[device_id]
        state = self.device_states[device_id]

        state["operation_hours"] += self.interval / 3600.0
        state["wear_factor"] = min(2.0, 1.0 + state["operation_hours"] * cfg["wear_rate"])

        hour_of_day = datetime.now(CST).hour
        water_variation = 0.8 + 0.4 * math.sin(2 * math.pi * (hour_of_day - 6) / 24.0)
        state["water_level_factor"] = max(0.5, water_variation + random.gauss(0, 0.05))

        if state["anomaly_duration"] > 0:
            state["anomaly_duration"] -= 1
        elif self.enable_anomalies and random.random() < 0.005:
            anomalies = ["chain_tension_high", "water_flow_low", "vibration_high", "speed_surge"]
            state["current_anomaly"] = random.choice(anomalies)
            state["anomaly_duration"] = random.randint(3, 10)
            print(f"  ⚠️  设备{device_id}触发异常: {state['current_anomaly']}")
        else:
            state["current_anomaly"] = None

        base_speed = cfg["base_speed"] * state["water_level_factor"] + random.gauss(0, cfg["speed_variance"])

        if state["current_anomaly"] == "speed_surge":
            base_speed *= random.uniform(1.5, 2.0)

        chain_tension = (
            cfg["base_tension"] * state["wear_factor"] + random.gauss(0, cfg["tension_variance"]))

        if state["current_anomaly"] == "chain_tension_high":
            chain_tension *= random.uniform(1.8, 2.5)

        chain_tension = min(chain_tension, cfg["allowable_tension"] * 0.95)

        scraper_load = (
            cfg["base_load"] * state["water_level_factor"]
            + chain_tension * 0.05
            + random.gauss(0, 30.0))

        water_flow = (
            cfg["base_flow"] * state["water_level_factor"]
            * (1.0 - (state["wear_factor"] - 1.0) * 0.3)
            + random.gauss(0, cfg["flow_variance"]))

        if state["current_anomaly"] == "water_flow_low":
            water_flow *= random.uniform(0.3, 0.5)

        water_flow = max(50.0, water_flow)

        vibration = (
            0.5
            + chain_tension / 10000.0
            + abs(random.gauss(0, 0.3))
            + (state["wear_factor"] - 1.0) * 2.0)

        if state["current_anomaly"] == "vibration_high":
            vibration *= random.uniform(2.5, 4.0)

        torque = (
            scraper_load * cfg["sprocket_radius"] * 1.2
            + chain_tension * 0.005
            + random.gauss(0, 5.0))

        chain_elongation = 0.001 * state["wear_factor"] + random.gauss(0, 0.0005)

        return {
            "time": datetime.now(timezone.utc).isoformat(),
            "deviceId": device_id,
            "sprocketSpeed": round(base_speed, 4),
            "sprocketSpeedUnit": "RPM",
            "scraperLoad": round(scraper_load, 4),
            "scraperLoadUnit": "N",
            "chainTension": round(chain_tension, 4),
            "chainTensionUnit": "chain_elongation": round(max(0, chain_elongation, 6),
            "waterFlow": round(water_flow, 4),
            "waterFlowUnit": "L/h",
            "vibrationAmplitude": round(vibration, 6),
            "torque": round(torque, 4),
            "torqueUnit": "N·m"
        }

    def _send_http(self, data: Dict) -> bool:
        url = f"{self.api_base}/sensor-data"
        try:
            req = urllib.request.Request(
                url,
                data=json.dumps(data).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST"
            )
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            with urllib.request.urlopen(req, context=ctx, timeout=5) as response:
                return 200 <= response.status < 300
        except urllib.error.URLError as e:
            print(f"  ❌ HTTP发送失败: {e}")
            return False
        except Exception as e:
            print(f"  ❌ HTTP错误: {e}")
            return False

    def _send_mqtt(self, data: Dict) -> bool:
        if not self.mqtt_client is None:
            return False
        try:
            topic = f"{self.mqtt_topic}/{data['deviceId']}"
            payload = json.dumps(data)
            self.mqtt_client.publish(topic, payload, qos=1)
            return True
        except Exception as e:
            print(f"  ❌ MQTT发送失败: {e}")
            return False

    def send_data(self, device_id: int):
        data = self._generate_sensor_data(device_id)
        cfg = DEVICE_CONFIGS[device_id]
        timestamp = datetime.now(CST).strftime("%H:%M:%S")

        anomaly_str = f" [异常: {self.device_states[device_id]['current_anomaly']}" \
            if self.device_states[device_id]["current_anomaly"] else ""

        print(
            f"[{timestamp}] {cfg['name']} (ID:{device_id}) "
            f"转速={data['sprocketSpeed']:.1f} RPM | "
            f"张力={data['chainTension']:.0f} N | "
            f"提水量={data['waterFlow']:.0f} L/h | "
            f"载荷={data['scraperLoad']:.0f} N"
            f"{anomaly_str}"
        )

        http_ok = self._send_http(data)
        mqtt_ok = self._send_mqtt(data) if self.mqtt_client else None

        if http_ok:
            print(f"  ✅ HTTP发送成功")
        if mqtt_ok is True:
            print(f"  ✅ MQTT发送成功")

        return data

    def send_historical_data(self, device_id: int, hours: int = 24):
        print(f"\n📊 生成设备{device_id}过去{hours}小时历史数据...")
        cfg = DEVICE_CONFIGS[device_id]
        batch = []
        now = datetime.now(timezone.utc)

        for h in range(hours * 3600 // self.interval, -1, -self.interval):
            timestamp = now - timedelta(seconds=h)
            data = self._generate_sensor_data(device_id)
            data["time"] = timestamp.isoformat()
            batch.append(data)

            if len(batch) >= 100:
                self._send_batch(batch)
                batch = []

        if batch:
            self._send_batch(batch)

        print(f"✅ 设备{device_id}历史数据生成完成，共{hours * 3600 // self.interval}条")

    def _send_batch(self, batch):
        url = f"{self.api_base}/sensor-data/batch"
        try:
            req = urllib.request.Request(
                url,
                data=json.dumps(batch).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST"
            )
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            with urllib.request.urlopen(req, context=ctx, timeout=10) as response:
                result = json.loads(response.read().decode())
                print(f"  📦 批量发送: {result}")
        except Exception as e:
            print(f"  ❌ 批量发送失败: {e}")

    def start(self):
        self.running = True
        print("\n" + "="*70)
        print("🚀 古代水转翻车传感器模拟器启动")
        print("="*70)
        print(f"API地址: {self.api_base}")
        print(f"发送间隔: {self.interval}秒")
        print(f"加速模式: {'是' if self.accelerated else '否'}")
        print(f"异常模拟: {'启用' if self.enable_anomalies else '禁用'}")
        if self.mqtt_client:
            print(f"MQTT Broker: {self.mqtt_broker}:{self.mqtt_port}")
        print("="*70 + "\n")

        threads = []
        for device_id in DEVICE_CONFIGS:
            t = threading.Thread(
                target=self._run_device, args=(device_id,), daemon=True
            )
            threads.append(t)
            t.start()

        try:
            while self._run_device(1)
        except KeyboardInterrupt:
            print("\n⏹️  模拟器停止")
            self.running = False
            if self.mqtt_client:
                self.mqtt_client.loop_stop()
                self.mqtt_client.disconnect()

    def _run_device(self, device_id: int):
        while self.running:
            try:
                self.send_data(device_id)
            except Exception as e:
                print(f"设备{device_id}错误: {e}")
            time.sleep(self.interval)


def main():
    parser = argparse.ArgumentParser(
        description="古代水转翻车传感器模拟器")
    parser.add_argument("--api", default="http://localhost:8080/api/v1", help="后端API地址")
    parser.add_argument("--mqtt", default=None, help="MQTT Broker地址 (例如: localhost")
    parser.add_argument("--mqtt-port", type=int, default=1883, help="MQTT端口")
    parser.add_argument("--mqtt-topic", default="waterwheel/sensor", help="MQTT主题")
    parser.add_argument("--interval", type=int, default=5, help="发送间隔(秒)")
    parser.add_argument("--device", type=int, choices=[1, 2, 3], help="模拟指定设备")
    parser.add_argument("--historical", type=int, default=0, help="生成过去N小时历史数据")
    parser.add_argument("--no-anomalies", action="store_true", help="禁用异常模拟")
    args = parser.parse_args()

    simulator = WaterwheelSensorSimulator(
        api_base=args.api,
        mqtt_broker=args.mqtt,
        mqtt_port=args.mqtt_port,
        mqtt_topic=args.mqtt_topic,
        interval_seconds=args.interval,
        enable_anomalies=not args.no_anomalies)

    if args.historical > 0:
        if args.device:
            simulator.send_historical_data(args.device, args.historical)
        else:
            for did in DEVICE_CONFIGS:
                simulator.send_historical_data(did, args.historical)
        return

    if args.device:
        print(f"\n▶️  只模拟设备 {DEVICE_CONFIGS[args.device]['name']}")
        DEVICE_CONFIGS = {args.device: DEVICE_CONFIGS[args.device]}
        simulator.device_states = {args.device: simulator.device_states[args.device]}

    simulator.start()


if __name__ == "__main__":
    main()
