# 古代水转翻车链传动动力学仿真与提水效率优化系统

Ancient Waterwheel Chain Transmission Dynamics Simulation & Water Lifting Efficiency Optimization System

用于水利史团队对宋代水转翻车的复原研究，结合多体动力学仿真、响应面法优化、GPU 实例化渲染等技术，实现对古代水转翻车的数字化复原与性能优化。

---

## 🏗️ 系统架构

### 整体架构图

```
                                 ┌─────────────────────┐
                                 │   前端 (Nginx)       │
                                 │  - Three.js 3D渲染   │
                                 │  - Gzip压缩         │
                                 │  - 效率面板         │
                                 └─────────┬───────────┘
                                           │
                                           │ 80
                                           ▼
                                 ┌─────────────────────┐
                                 │  后端 (SpringBoot)   │
                                 │  - Actuator监控      │
                                 │  - Prometheus指标   │
                                 │  - 4模块事件驱动     │
                                 └──────┬───────────────┘
                                        │
         ┌──────────────────────────────┼──────────────────────────────┐
         │                              │                              │
         ▼                              ▼                              ▼
┌──────────────────┐       ┌──────────────────────┐       ┌─────────────────────┐
│  TimescaleDB      │       │  Mosquitto (MQTT)    │       │  Prometheus         │
│  - 时序超表       │       │  - 告警推送          │       │  - 指标采集         │
│  - 连续聚合       │       │  - 传感器数据        │       │  - 告警规则         │
│  - 降采样保留策略 │       └──────────────────────┘       └─────────┬───────────┘
│  - 数据压缩       │                                                │
└──────────────────┘                                                ▼
                                                           ┌─────────────────────┐
                                                           │  Grafana             │
                                                           │  - 数据可视化        │
                                                           │  - 仪表盘            │
                                                           └─────────────────────┘
                                            ┌─────────────────────┐
                                            │  传感器模拟器        │
                                            │  - 链速可调          │
                                            │  - 刮板形状可选      │
                                            │  - 异常模拟          │
                                            └─────────────────────┘
```

### 后端模块架构（Spring Events 驱动）

```
┌─────────────────────────────────────────────────────────────────────┐
│                      WaterwheelController                           │
└─────────────────┬────────────────────────────────┬──────────────────┘
                  │                                │
       ┌──────────▼──────────┐           ┌────────▼──────────┐
       │  WaterwheelService  │           │  DtuReceiverService │
       │  (API 胶水层)       │           │  (数据采集+校验)    │
       └──────────┬──────────┘           └──────────┬──────────┘
                  │                                │
                  │                     ┌──────────▼──────────┐
                  │                     │ SensorDataReceived   │
                  │                     │   Event              │
                  │                     └──────────┬──────────┘
                  │                                │
       ┌──────────▼──────────┐           ┌────────▼──────────┐
       │ ChainSimulatorService│           │ EfficiencyOptimizer │
       │ (多体动力学仿真)     │           │  (响应面法优化)     │
       └──────────┬──────────┘           └──────────┬──────────┘
                  │                                │
                  │                     ┌──────────▼──────────┐
                  │                     │ AlertTriggeredEvent │
                  │                     └──────────┬──────────┘
                  │                                │
       ┌──────────▼──────────┐           ┌────────▼──────────┐
       │   AlarmService      │──────────▶│  MQTT Broker       │
       │ (告警去抖+推送)     │           │  (Mosquitto)        │
       └─────────────────────┘           └─────────────────────┘
```

### 前端模块架构

```
┌──────────────────────────────────────────────────────────┐
│                    index.html                           │
└────────────┬──────────────────────────────┬──────────────┘
             │                              │
    ┌────────▼──────────┐         ┌────────▼──────────┐
    │ chain_pump_3d.js   │         │ efficiency_panel.js │
    │ - Three.js场景      │         │ - 趋势图(Chart.js)  │
    │ - 120链节实例化    │         │ - 效率优化面板      │
    │ - 24刮板实例化     │         │ - API调用封装       │
    │ - 水流粒子系统     │         │ - 告警展示          │
    │ - 相机控制         │         │ - 设备列表          │
    └────────────────────┘         └────────────────────┘
```

---

## 🚀 快速开始

### 前置要求

- Docker >= 24.0
- Docker Compose >= 2.20
- 建议配置：4 核 CPU，8GB 内存

### 一键部署

```bash
# 1. 克隆项目
git clone <repository-url>
cd AI_solo_coder_task_A_151

# 2. 复制环境变量配置
cp .env.example .env

# 3. 根据需要修改 .env 配置
# vi .env

# 4. 启动所有服务（首次构建需要 5-10 分钟）
docker-compose up -d --build

# 5. 查看服务状态
docker-compose ps

# 6. 查看服务日志
docker-compose logs -f backend
docker-compose logs -f simulator
```

### 服务访问地址

| 服务 | 地址 | 用户名/密码 |
|------|------|-------------|
| 前端页面 | http://localhost | - |
| 后端 API | http://localhost:8080/api | - |
| 后端健康检查 | http://localhost:8080/api/actuator/health | - |
| Prometheus 指标 | http://localhost:8080/api/actuator/prometheus | - |
| Prometheus UI | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin / admin |
| MQTT Broker | tcp://localhost:1883 | admin / admin |

### 验证部署

```bash
# 检查所有容器健康状态
docker-compose ps

# 预期输出示例：
# NAME                      IMAGE                           COMMAND                  SERVICE             CREATED          STATUS                    PORTS
# waterwheel-backend        AI_solo_coder_task_A_151-backend   "sh -c 'java $JAVA_O..."   backend             10 minutes ago   Up 2 minutes (healthy)    0.0.0.0:8080->8080/tcp
# waterwheel-frontend       AI_solo_coder_task_A_151-frontend  "/docker-entrypoint...."   frontend            10 minutes ago   Up 2 minutes (healthy)    0.0.0.0:80->80/tcp
# waterwheel-timescaledb    timescale/timescaledb:2.15.3-pg16  "docker-entrypoint.s..."   timescale           10 minutes ago   Up 2 minutes (healthy)    0.0.0.0:5432->5432/tcp
# waterwheel-mqtt           eclipse-mosquitto:2.0.18          "sh -c 'echo 'listene..."   mosquitto           10 minutes ago   Up 2 minutes              0.0.0.0:1883->1883/tcp, 0.0.0.0:9001->9001/tcp
# waterwheel-simulator      AI_solo_coder_task_A_151-simulator "python waterwheel_s..."   simulator           10 minutes ago   Up 2 minutes
# waterwheel-prometheus     prom/prometheus:v2.52.0            "/bin/prometheus --c..."   prometheus          10 minutes ago   Up 2 minutes (healthy)    0.0.0.0:9090->9090/tcp
# waterwheel-grafana        grafana/grafana:10.4.2             "/run.sh"                  grafana             10 minutes ago   Up 2 minutes (healthy)    0.0.0.0:3000->3000/tcp

# 调用后端 API 测试
curl http://localhost:8080/api/v1/devices

# 预期输出：
# [{"deviceId":1,"deviceName":"宋代翻车一号",...},...]

# 验证 Prometheus 指标
curl http://localhost:8080/api/actuator/prometheus | head -20
```

### 停止服务

```bash
# 停止服务，保留数据
docker-compose down

# 停止服务并删除所有数据（谨慎使用）
docker-compose down -v
```

---

## 🎛️ 传感器模拟器用法

### Docker 环境中运行（推荐）

```bash
# 使用默认配置启动
docker-compose up -d simulator

# 使用环境变量自定义配置
CHAIN_SPEED=1.5 SCRAPER_SHAPE=arc SIMULATOR_INTERVAL=3 docker-compose up -d simulator

# 只模拟指定设备
docker-compose run --rm simulator --device 1

# 生成历史数据
docker-compose run --rm simulator --historical 24 --device 1

# 禁用异常模拟
docker-compose run --rm simulator --no-anomalies
```

### 本地直接运行

```bash
# 安装依赖
cd simulator
pip install -r requirements.txt

# 查看帮助
python waterwheel_sensor_simulator.py --help

# 基本用法
python waterwheel_sensor_simulator.py \
  --api http://localhost:8080/api/v1 \
  --mqtt localhost \
  --interval 5 \
  --chain-speed 1.0 \
  --scraper-shape flat

# 生成 72 小时历史数据
python waterwheel_sensor_simulator.py --historical 72
```

### 链速配置

通过 `--chain-speed` 参数或 `CHAIN_SPEED` 环境变量调整链速倍率：

| 链速倍率 | 典型工况 | 说明 |
|---------|----------|------|
| 0.5 | 枯水期 | 低流速，提水量减少 50% |
| 0.8 | 平水期 | 正常偏低流速 |
| 1.0 | 标准工况 | 默认配置 |
| 1.5 | 丰水期 | 高流速，张力增加 50% |
| 2.0 | 洪水期 | 极限工况，注意张力告警 |
| 3.0 | 极限测试 | 仅用于压力测试 |

### 刮板形状配置

通过 `--scraper-shape` 参数或 `SCRAPER_SHAPE` 环境变量选择刮板形状：

| 形状 | 参数值 | 载荷系数 | 流量系数 | 张力系数 | 效率 | 磨损系数 | 适用场景 |
|------|--------|---------|---------|---------|------|---------|----------|
| 弧形 | `arc` | 0.90 | 1.15 | 0.95 | 85% | 0.80 | 高流速，提水优先 |
| 平板 | `flat` | 1.00 | 1.00 | 1.00 | 75% | 1.00 | 通用，平衡型 |
| 梯形 | `trapezoid` | 1.10 | 1.08 | 1.05 | 82% | 0.90 | 重载，耐久性优先 |

**不同形状的物理意义**：
- **弧形刮板**：曲面设计减少入水阻力，提水效率最高，但制造难度大
- **平板刮板**：结构简单，制造成本低，宋代最常见的形制
- **梯形刮板**：底部宽顶部窄，入土阻力小，适合泥沙较多的河道

### 命令行参数完整列表

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| `--api` | `API_BASE` | `http://localhost:8080/api/v1` | 后端 API 地址 |
| `--mqtt` | `MQTT_HOST` | - | MQTT Broker 地址 |
| `--mqtt-port` | `MQTT_PORT` | `1883` | MQTT 端口 |
| `--mqtt-user` | `MQTT_USER` | `admin` | MQTT 用户名 |
| `--mqtt-pass` | `MQTT_PASSWORD` | `admin` | MQTT 密码 |
| `--mqtt-topic` | `MQTT_TOPIC` | `waterwheel/sensor` | MQTT 主题前缀 |
| `--interval` | `INTERVAL` | `5` | 数据发送间隔（秒） |
| `--device` | - | - | 只模拟指定设备（1/2/3） |
| `--historical` | - | `0` | 生成过去 N 小时历史数据 |
| `--no-anomalies` | - | - | 禁用异常模拟 |
| `--chain-speed` | `CHAIN_SPEED` | `1.0` | 链速倍率（0.5-3.0） |
| `--scraper-shape` | `SCRAPER_SHAPE` | `flat` | 刮板形状（arc/flat/trapezoid） |

### 模拟器输出示例

```
======================================================================
🚀 古代水转翻车传感器模拟器启动
======================================================================
API地址: http://backend:8080/api/v1
发送间隔: 5秒
异常模拟: 启用
链速倍率: 1.5x
刮板形状: 弧形刮板 (arc)
  - 载荷系数: 0.9
  - 流量系数: 1.15
  - 张力系数: 0.95
  - 效率: 0.85
模拟设备: ['宋代翻车一号', '宋代翻车二号', '宋代翻车三号']
MQTT Broker: mosquitto:1883
======================================================================

[14:30:00] 宋代翻车一号 (ID:1) 链速=0.79 m/s | 张力=5823 N | 提水量=985 L/h | 刮板=arc
  ✅ HTTP发送成功
  ✅ MQTT发送成功
[14:30:00] 宋代翻车二号 (ID:2) 链速=0.87 m/s | 张力=6512 N | 提水量=1085 L/h | 刮板=arc
  ✅ HTTP发送成功
  ✅ MQTT发送成功
  ⚠️  设备3触发异常: chain_tension_high
[14:30:00] 宋代翻车三号 (ID:3) 链速=0.65 m/s | 张力=10234 N | 提水量=762 L/h | 刮板=arc
  ✅ HTTP发送成功
  ✅ MQTT发送成功
```

---

## 📊 TimescaleDB 数据保留策略

系统自动配置了三级数据保留和降采样策略：

| 数据类型 | 聚合粒度 | 保留时长 | 说明 |
|---------|---------|---------|------|
| 原始数据 | 5秒 | 365天 | 高精度原始采样，7天后自动压缩 |
| 小时聚合 | 1小时 | 2年 | 每小时平均值/最大值/最小值 |
| 天聚合 | 1天 | 5年 | 每天统计指标 |
| 月聚合 | 1月 | 永久 | 长期趋势分析 |

### 压缩策略
- 原始数据写入 7 天后自动压缩
- 压缩比约 10:1，大幅节省存储空间
- 压缩后数据仍可查询，仅不支持修改和删除

### 自动执行的策略
1. **连续聚合刷新**：每小时刷新小时聚合，每天刷新天聚合，每月刷新月聚合
2. **数据保留清理**：每天自动清理超过保留期的数据
3. **数据压缩**：每天自动压缩超过 7 天的原始数据

### 查询聚合数据示例

```sql
-- 查询小时级聚合数据
SELECT * FROM sensor_data_hourly
WHERE device_id = 1
  AND bucket > NOW() - INTERVAL '7 days'
ORDER BY bucket DESC;

-- 查询日级聚合数据
SELECT * FROM sensor_data_daily
WHERE device_id = 1
  AND bucket > NOW() - INTERVAL '90 days'
ORDER BY bucket DESC;

-- 查询设备运行状态
SELECT * FROM v_device_status_summary;
```

---

## 📈 监控与告警

### Actuator 端点

Spring Boot Actuator 暴露以下监控端点：

| 端点 | 路径 | 说明 |
|------|------|------|
| 健康检查 | `/api/actuator/health` | 服务健康状态，包含数据库、MQTT 连接状态 |
| 信息 | `/api/actuator/info` | 应用版本、构建信息 |
| 指标 | `/api/actuator/metrics` | 可用指标列表 |
| Prometheus | `/api/actuator/prometheus` | Prometheus 格式指标 |

### Prometheus 监控

Prometheus 自动采集以下指标：
- JVM 内存、CPU、GC
- HTTP 请求延迟、吞吐量
- 数据库连接池状态
- MQTT 连接状态
- 自定义业务指标（仿真耗时、优化迭代次数等）

### Grafana 仪表盘

访问 http://localhost:3000，使用默认账号 `admin/admin` 登录。

Grafana 已预配置数据源：
- Prometheus：应用性能监控
- TimescaleDB：时序数据分析

可创建以下监控面板：
- 实时链速、张力趋势图
- 多设备提水量对比
- 告警统计
- JVM 性能监控
- 数据库连接池监控

---

## 📁 项目结构

```
AI_solo_coder_task_A_151/
├── backend/                    # SpringBoot 后端
│   ├── src/main/java/
│   │   └── com/waterwheel/chaintransmission/
│   │       ├── dtu_receiver/      # DTU 数据采集模块
│   │       ├── chain_simulator/   # 多体动力学仿真模块
│   │       ├── efficiency_optimizer/ # 效率优化模块
│   │       ├── alarm_mqtt/        # 告警推送模块
│   │       ├── events/            # Spring Events 事件
│   │       ├── simulation/        # 动力学仿真核心算法
│   │       ├── optimization/      # 响应面法优化核心算法
│   │       └── ...
│   ├── src/main/resources/
│   │   ├── config/                # 外置 YAML 配置
│   │   │   ├── dtu-receiver.yml
│   │   │   ├── chain-dynamics.yml
│   │   │   ├── efficiency-optimization.yml
│   │   │   └── alert-thresholds.yml
│   │   └── application.yml
│   ├── Dockerfile                 # 多阶段构建 Dockerfile
│   ├── .dockerignore
│   └── pom.xml
│
├── frontend/                   # 前端页面
│   ├── js/
│   │   ├── chain_pump_3d.js       # Three.js 3D 渲染模块
│   │   ├── efficiency_panel.js    # 效率面板/趋势图模块
│   │   └── data-handler.js
│   ├── index.html
│   ├── nginx.conf                 # Nginx 配置（含 Gzip）
│   └── Dockerfile
│
├── simulator/                  # 传感器模拟器
│   ├── waterwheel_sensor_simulator.py
│   ├── requirements.txt
│   └── Dockerfile
│
├── database/                   # 数据库脚本
│   ├── init.sql                   # 初始化脚本
│   └── timescaledb_policies.sql   # 降采样和保留策略
│
├── monitoring/                 # 监控配置
│   ├── prometheus.yml             # Prometheus 配置
│   └── grafana/                   # Grafana 配置
│       ├── datasources/
│       └── dashboards/
│
├── docker-compose.yml           # 服务编排
├── .env.example                 # 环境变量模板
└── README.md                    # 本文档
```

---

## 🔧 常见问题

### Q: 后端启动失败，报数据库连接错误

A: 确保 TimescaleDB 容器已完全启动并执行了初始化脚本。查看日志：
```bash
docker-compose logs timescale
```

### Q: 模拟器无法连接后端

A: 检查后端容器健康状态，确保已通过健康检查：
```bash
docker-compose ps backend
```

### Q: 前端页面无法加载 3D 模型

A: 检查浏览器控制台是否有 WebGL 相关错误，确保显卡支持 WebGL 2.0。

### Q: 如何修改链速和刮板形状而不重启容器？

A: 修改 `.env` 文件后重启 simulator 服务：
```bash
docker-compose up -d simulator
```

### Q: 数据占用空间太大怎么办？

A: 系统已配置自动压缩和保留策略。如需手动清理：
```sql
-- 手动压缩超过 7 天的数据
SELECT compress_chunk(i, if_not_exists => TRUE)
FROM show_chunks('sensor_data', older_than => INTERVAL '7 days') i;
```

---

## 📚 技术栈

### 后端
- **框架**: Spring Boot 3.2 / Spring Framework 6
- **数据库**: PostgreSQL 16 + TimescaleDB 2.15
- **消息队列**: Eclipse Mosquitto 2.0 (MQTT 3.1.1)
- **监控**: Spring Boot Actuator + Micrometer + Prometheus
- **数学计算**: Apache Commons Math 3.6
- **构建**: Maven 3.9 + Eclipse Temurin JDK 17

### 前端
- **3D 渲染**: Three.js r128 (GPU InstancedMesh)
- **图表**: Chart.js 4.4
- **服务器**: Nginx 1.27 (Gzip 压缩)

### DevOps
- **容器化**: Docker 多阶段构建
- **编排**: Docker Compose 2
- **监控**: Prometheus 2.52 + Grafana 10.4
- **时序优化**: TimescaleDB 连续聚合 + 自动压缩

---

## 📄 许可证

本项目用于水利史学术研究，非商业用途。

---

## 👥 团队

水利史研究团队 × 软件工程团队

*宋代水转翻车数字化复原项目*
