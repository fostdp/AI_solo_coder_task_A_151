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

支持配置：
- 链速范围：通过 --chain-speed 或 CHAIN_SPEED 环境变量
- 刮板形状：弧形(arc)、平板(flat)、梯形(trapezoid)，通过 --scraper-shape 或 SCRAPER_SHAPE 环境变量
"""

import json
import time
import random
import math
import argparse
import threading
import os
from datetime import datetime, timezone, timedelta
from typing import Dict, Optional, Tuple
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

SCRAPER_SHAPE_FACTORS = {
    "arc": {
        "name": "弧形刮板",
        "load_factor": 0.9,
        "flow_factor": 1.15,
        "tension_factor": 0.95,
        "efficiency": 0.85,
        "wear_factor": 0.8
    },
    "flat": {
        "name": "平板刮板",
        "load_factor": 1.0,
        "flow_factor": 1.0,
        "tension_factor": 1.0,
        "efficiency": 0.75,
        "wear_factor": 1.0
    },
    "trapezoid": {
        "name": "梯形刮板",
        "load_factor": 1.1,
        "flow_factor": 1.08,
        "tension_factor": 1.05,
        "efficiency": 0.82,
        "wear_factor": 0.9
    }
}

def get_device_configs(chain_speed_override: Optional[float] = None,
                       scraper_shape: Optional[str] = None) -> Dict[int, Dict]:
    """
    获取设备配置，支持链速和刮板形状覆盖
    """
    shape = scraper_shape or os.environ.get("SCRAPER_SHAPE", "flat").lower()
    if shape not in SCRAPER_SHAPE_FACTORS:
        print(f"⚠️  未知刮板形状 '{shape}'，使用默认 'flat'")
        shape = "flat"

    shape_factor = SCRAPER_SHAPE_FACTORS[shape]
    chain_speed_mult = chain_speed_override or float(os.environ.get("CHAIN_SPEED", "1.0"))

    base_configs = {
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
            "allowable_tension": 15000.0,
            "scraper_shape": shape,
            "scraper_depth": 0.12,
            "scraper_width": 0.30,
            "scraper_angle": 45.0
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
            "allowable_tension": 17000.0,
            "scraper_shape": shape,
            "scraper_depth": 0.14,
            "scraper_width": 0.35,
            "scraper_angle": 50.0
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
            "allowable_tension": 13000.0,
            "scraper_shape": shape,
            "scraper_depth": 0.10,
            "scraper_width": 0.28,
            "scraper_angle": 40.0
        }
    }

    for device_id in base_configs:
        cfg = base_configs[device_id]
        cfg["base_speed"] *= chain_speed_mult
        cfg["base_tension"] *= shape_factor["tension_factor"]
        cfg["base_load"] *= shape_factor["load_factor"]
        cfg["base_flow"] *= shape_factor["flow_factor"]
        cfg["wear_rate"] *= shape_factor["wear_factor"]

    return base_configs, shape_factor, chain_speed_mult


class WaterwheelSensorSimulator:
    def __init__(
        self,
        api_base: str = "http://localhost:8080/api/v1",
        mqtt_broker: Optional[str] = None,
        mqtt_port: int = 1883,
        mqtt_topic: str = "waterwheel/sensor",
        mqtt_username: Optional[str] = None,
        mqtt_password: Optional[str] = None,
        interval_seconds: int = 5,
        enable_anomalies: bool = True,
        chain_speed: Optional[float] = None,
        scraper_shape: Optional[str] = None,
        device_id: Optional[int] = None
    ):
        self.api_base = api_base.rstrip("/")
        self.mqtt_broker = mqtt_broker
        self.mqtt_port = mqtt_port
        self.mqtt_topic = mqtt_topic
        self.mqtt_username = mqtt_username
        self.mqtt_password = mqtt_password
        self.interval = interval_seconds
        self.enable_anomalies = enable_anomalies
        self.running = False
        self.device_states: Dict[int, Dict] = {}
        self.mqtt_client = None

        self.device_configs, self.shape_factor, self.chain_speed_mult = get_device_configs(
            chain_speed, scraper_shape
        )

        if device_id and device_id in self.device_configs:
            self.device_configs = {device_id: self.device_configs[device_id]}

        self._init_device_states()

        if mqtt_broker and MQTT_AVAILABLE:
            self._setup_mqtt()

    def _init_device_states(self):
        for device_id in self.device_configs:
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
                client_id=f"waterwheel_simulator_{random.randint(1000, 9999)}"
            )
            if self.mqtt_username and self.mqtt_password:
                self.mqtt_client.username_pw_set(
                    self.mqtt_username, self.mqtt_password
                )
            self.mqtt_client.connect(self.mqtt_broker, self.mqtt_port, 60)
            self.mqtt_client.loop_start()
            print(f"MQTT已连接到 {self.mqtt_broker}:{self.mqtt_port}")
        except Exception as e:
            print(f"MQTT连接失败: {e}")
            self.mqtt_client = None

    def _calculate_chain_velocity(self, sprocket_speed_rpm: float, sprocket_radius: float) -> float:
        """计算链速 (m/s)"""
        angular_velocity = 2 * math.pi * sprocket_speed_rpm / 60.0
        return angular_velocity * sprocket_radius

    def _generate_sensor_data(self, device_id: int) -> Dict:
        cfg = self.device_configs[device_id]
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

        chain_elongation = max(0.0, 0.001 * state["wear_factor"] + random.gauss(0, 0.0005))
        chain_velocity = self._calculate_chain_velocity(base_speed, cfg["sprocket_radius"])

        return {
            "time": datetime.now(timezone.utc).isoformat(),
            "deviceId": device_id,
            "sprocketSpeed": round(base_speed, 4),
            "sprocketSpeedUnit": "RPM",
            "chainVelocity": round(chain_velocity, 4),
            "chainVelocityUnit": "m/s",
            "scraperLoad": round(scraper_load, 4),
            "scraperLoadUnit": "N",
            "chainTension": round(chain_tension, 4),
            "chainTensionUnit": "N",
            "chainElongation": round(chain_elongation, 6),
            "waterFlow": round(water_flow, 4),
            "waterFlowUnit": "L/h",
            "vibrationAmplitude": round(vibration, 6),
            "torque": round(torque, 4),
            "torqueUnit": "N·m",
            "scraperShape": cfg["scraper_shape"],
            "scraperDepth": cfg["scraper_depth"],
            "scraperWidth": cfg["scraper_width"],
            "scraperAngle": cfg["scraper_angle"]
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
        if self.mqtt_client is None:
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
        cfg = self.device_configs[device_id]
        timestamp = datetime.now(CST).strftime("%H:%M:%S")

        anomaly_str = f" [异常: {self.device_states[device_id]['current_anomaly']}]" \
            if self.device_states[device_id]["current_anomaly"] else ""

        chain_velocity = data.get("chainVelocity", 0)
        print(
            f"[{timestamp}] {cfg['name']} (ID:{device_id}) "
            f"链速={chain_velocity:.2f} m/s | "
            f"张力={data['chainTension']:.0f} N | "
            f"提水量={data['waterFlow']:.0f} L/h | "
            f"刮板={cfg['scraper_shape']}"
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
        batch = []
        now = datetime.now(timezone.utc)

        interval_count = hours * 3600 // self.interval
        for h in range(interval_count, -1, -1):
            timestamp = now - timedelta(seconds=h * self.interval)
            data = self._generate_sensor_data(device_id)
            data["time"] = timestamp.isoformat()
            batch.append(data)

            if len(batch) >= 100:
                self._send_batch(batch)
                batch = []

        if batch:
            self._send_batch(batch)

        print(f"✅ 设备{device_id}历史数据生成完成，共{interval_count}条")

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
        print(f"异常模拟: {'启用' if self.enable_anomalies else '禁用'}")
        print(f"链速倍率: {self.chain_speed_mult}x")
        print(f"刮板形状: {self.shape_factor['name']} ({self.device_configs[next(iter(self.device_configs))]['scraper_shape']})")
        print(f"  - 载荷系数: {self.shape_factor['load_factor']}")
        print(f"  - 流量系数: {self.shape_factor['flow_factor']}")
        print(f"  - 张力系数: {self.shape_factor['tension_factor']}")
        print(f"  - 效率: {self.shape_factor['efficiency']}")
        print(f"模拟设备: {[cfg['name'] for cfg in self.device_configs.values()]}")
        if self.mqtt_client:
            print(f"MQTT Broker: {self.mqtt_broker}:{self.mqtt_port}")
        print("="*70 + "\n")

        threads = []
        for device_id in self.device_configs:
            t = threading.Thread(
                target=self._run_device, args=(device_id,), daemon=True
            )
            threads.append(t)
            t.start()

        try:
            while self.running:
                time.sleep(1)
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
    parser.add_argument("--api", default=os.environ.get("API_BASE", "http://localhost:8080/api/v1"),
                        help="后端API地址 (默认: http://localhost:8080/api/v1)")
    parser.add_argument("--mqtt", default=os.environ.get("MQTT_HOST"),
                        help="MQTT Broker地址 (例如: localhost)")
    parser.add_argument("--mqtt-port", type=int, default=int(os.environ.get("MQTT_PORT", "1883")),
                        help="MQTT端口 (默认: 1883)")
    parser.add_argument("--mqtt-user", default=os.environ.get("MQTT_USER", "admin"),
                        help="MQTT用户名")
    parser.add_argument("--mqtt-pass", default=os.environ.get("MQTT_PASSWORD", "admin"),
                        help="MQTT密码")
    parser.add_argument("--mqtt-topic", default=os.environ.get("MQTT_TOPIC", "waterwheel/sensor"),
                        help="MQTT主题 (默认: waterwheel/sensor)")
    parser.add_argument("--interval", type=int, default=int(os.environ.get("INTERVAL", "5")),
                        help="发送间隔(秒) (默认: 5)")
    parser.add_argument("--device", type=int, choices=[1, 2, 3],
                        help="模拟指定设备")
    parser.add_argument("--historical", type=int, default=0,
                        help="生成过去N小时历史数据")
    parser.add_argument("--no-anomalies", action="store_true",
                        help="禁用异常模拟")
    parser.add_argument("--chain-speed", type=float,
                        default=float(os.environ.get("CHAIN_SPEED", "1.0")),
                        help="链速倍率 (默认: 1.0, 范围: 0.5-3.0)")
    parser.add_argument("--scraper-shape", choices=["arc", "flat", "trapezoid"],
                        default=os.environ.get("SCRAPER_SHAPE", "flat"),
                        help="刮板形状: arc(弧形), flat(平板), trapezoid(梯形) (默认: flat)")
    args = parser.parse_args()

    if args.chain_speed < 0.5 or args.chain_speed > 3.0:
        print("⚠️  链速倍率应在 0.5-3.0 之间，已自动限制")
        args.chain_speed = max(0.5, min(3.0, args.chain_speed))

    simulator = WaterwheelSensorSimulator(
        api_base=args.api,
        mqtt_broker=args.mqtt,
        mqtt_port=args.mqtt_port,
        mqtt_topic=args.mqtt_topic,
        mqtt_username=args.mqtt_user,
        mqtt_password=args.mqtt_pass,
        interval_seconds=args.interval,
        enable_anomalies=not args.no_anomalies,
        chain_speed=args.chain_speed,
        scraper_shape=args.scraper_shape,
        device_id=args.device
    )

    if args.historical > 0:
        if args.device:
            simulator.send_historical_data(args.device, args.historical)
        else:
            for did in simulator.device_configs:
                simulator.send_historical_data(did, args.historical)
        return

    simulator.start()


if __name__ == "__main__":
    main()
