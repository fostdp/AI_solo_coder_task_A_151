-- ============================================
-- 古代水转翻车链传动动力学仿真系统
-- TimescaleDB 初始化脚本
-- ============================================

-- 创建数据库
-- CREATE DATABASE waterwheel_simulation;
-- \c waterwheel_simulation;

-- 启用TimescaleDB扩展
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ============================================
-- 1. 翻车设备表
-- ============================================
CREATE TABLE IF NOT EXISTS waterwheel_device (
    device_id SERIAL PRIMARY KEY,
    device_name VARCHAR(100) NOT NULL,
    location VARCHAR(200),
    chain_length DECIMAL(10, 2),
    num_links INTEGER,
    sprocket_radius DECIMAL(10, 2),
    scraper_count INTEGER,
    scraper_shape VARCHAR(50),
    installation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- ============================================
-- 2. 传感器时序数据表 (TimescaleDB超表)
-- ============================================
CREATE TABLE IF NOT EXISTS sensor_data (
    time TIMESTAMPTZ NOT NULL,
    device_id INTEGER NOT NULL REFERENCES waterwheel_device(device_id),
    sprocket_speed DECIMAL(10, 4),
    sprocket_speed_unit VARCHAR(10) DEFAULT 'RPM',
    scraper_load DECIMAL(12, 4),
    scraper_load_unit VARCHAR(10) DEFAULT 'N',
    chain_tension DECIMAL(12, 4),
    chain_tension_unit VARCHAR(10) DEFAULT 'N',
    water_flow DECIMAL(12, 4),
    water_flow_unit VARCHAR(15) DEFAULT 'L/h',
    vibration_amplitude DECIMAL(10, 6),
    chain_elongation DECIMAL(10, 6),
    torque DECIMAL(12, 4),
    torque_unit VARCHAR(10) DEFAULT 'N·m'
);

-- 创建超表（按时间分区）
SELECT create_hypertable('sensor_data', 'time', if_not_exists => TRUE);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_sensor_data_device_time ON sensor_data (device_id, time DESC);
CREATE INDEX IF NOT EXISTS idx_sensor_data_time ON sensor_data (time DESC);

-- ============================================
-- 3. 链传动动力学仿真结果表
-- ============================================
CREATE TABLE IF NOT EXISTS chain_dynamics_simulation (
    simulation_id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES waterwheel_device(device_id),
    simulation_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    input_speed DECIMAL(10, 4),
    input_torque DECIMAL(12, 4),
    link_count INTEGER,
    tension_distribution JSONB,
    vibration_frequencies JSONB,
    collision_forces JSONB,
    max_tension DECIMAL(12, 4),
    min_tension DECIMAL(12, 4),
    avg_tension DECIMAL(12, 4),
    resonance_risk BOOLEAN DEFAULT FALSE,
    simulation_duration_ms INTEGER
);

-- ============================================
-- 4. 提水效率优化结果表
-- ============================================
CREATE TABLE IF NOT EXISTS efficiency_optimization (
    optimization_id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES waterwheel_device(device_id),
    optimization_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    method VARCHAR(50) DEFAULT 'ResponseSurface',
    scraper_shape_params JSONB,
    chain_speed_range JSONB,
    optimal_scraper_depth DECIMAL(10, 4),
    optimal_scraper_width DECIMAL(10, 4),
    optimal_scraper_angle DECIMAL(10, 4),
    optimal_chain_speed DECIMAL(10, 4),
    predicted_max_water_flow DECIMAL(12, 4),
    efficiency_improvement DECIMAL(8, 4),
    response_surface_data JSONB,
    iterations INTEGER,
    convergence BOOLEAN DEFAULT FALSE
);

-- ============================================
-- 5. 告警记录表
-- ============================================
CREATE TABLE IF NOT EXISTS alert_record (
    alert_id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES waterwheel_device(device_id),
    alert_time TIMESTAMPTZ NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    alert_message TEXT,
    sensor_data JSONB,
    mqtt_topic VARCHAR(100),
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_time TIMESTAMPTZ
);

-- 创建超表
SELECT create_hypertable('alert_record', 'alert_time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_alert_device_time ON alert_record (device_id, alert_time DESC);
CREATE INDEX IF NOT EXISTS idx_alert_level ON alert_record (alert_level);

-- ============================================
-- 6. 设备配置参数表
-- ============================================
CREATE TABLE IF NOT EXISTS device_config (
    config_id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES waterwheel_device(device_id),
    param_name VARCHAR(100) NOT NULL,
    param_value DECIMAL(14, 6),
    param_unit VARCHAR(20),
    description TEXT,
    updated_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, param_name)
);

-- ============================================
-- 7. 链节详细参数表
-- ============================================
CREATE TABLE IF NOT EXISTS chain_link_params (
    param_id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES waterwheel_device(device_id),
    link_mass DECIMAL(10, 6),
    link_length DECIMAL(10, 4),
    link_stiffness DECIMAL(14, 4),
    link_damping DECIMAL(10, 6),
    friction_coefficient DECIMAL(8, 6),
    allowable_tension DECIMAL(12, 4),
    material VARCHAR(50)
);

-- ============================================
-- 初始化示例数据
-- ============================================

-- 插入示例翻车设备
INSERT INTO waterwheel_device (device_name, location, chain_length, num_links, sprocket_radius, scraper_count, scraper_shape, status)
VALUES 
    ('宋代翻车一号', '河南开封考古现场', 15.5, 120, 0.35, 24, '弧形刮板', 'ACTIVE'),
    ('宋代翻车二号', '浙江宁波水利遗址', 18.2, 140, 0.40, 28, '平板刮板', 'ACTIVE'),
    ('宋代翻车三号', '江苏扬州博物馆', 12.8, 100, 0.30, 20, '梯形刮板', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 插入设备配置参数
INSERT INTO device_config (device_id, param_name, param_value, param_unit, description)
VALUES
    (1, 'tension_warning_threshold', 8500.0, 'N', '链条张力告警阈值'),
    (1, 'tension_critical_threshold', 12000.0, 'N', '链条张力临界阈值'),
    (1, 'water_flow_min_threshold', 500.0, 'L/h', '最低提水量阈值'),
    (1, 'vibration_warning_threshold', 2.5, 'mm', '振动告警阈值'),
    (2, 'tension_warning_threshold', 9500.0, 'N', '链条张力告警阈值'),
    (2, 'tension_critical_threshold', 13500.0, 'N', '链条张力临界阈值'),
    (2, 'water_flow_min_threshold', 600.0, 'L/h', '最低提水量阈值'),
    (3, 'tension_warning_threshold', 7500.0, 'N', '链条张力告警阈值'),
    (3, 'tension_critical_threshold', 10500.0, 'N', '链条张力临界阈值'),
    (3, 'water_flow_min_threshold', 450.0, 'L/h', '最低提水量阈值')
ON CONFLICT DO NOTHING;

-- 插入链节参数
INSERT INTO chain_link_params (device_id, link_mass, link_length, link_stiffness, link_damping, friction_coefficient, allowable_tension, material)
VALUES
    (1, 0.25, 0.125, 500000.0, 150.0, 0.15, 15000.0, '锻铁'),
    (2, 0.28, 0.130, 550000.0, 160.0, 0.14, 17000.0, '铸铁'),
    (3, 0.22, 0.120, 480000.0, 140.0, 0.16, 13000.0, '熟铁')
ON CONFLICT DO NOTHING;

-- ============================================
-- 视图：最新传感器数据
-- ============================================
CREATE OR REPLACE VIEW v_latest_sensor_data AS
SELECT DISTINCT ON (device_id)
    time,
    device_id,
    sprocket_speed,
    scraper_load,
    chain_tension,
    water_flow,
    vibration_amplitude,
    torque
FROM sensor_data
ORDER BY device_id, time DESC;

-- ============================================
-- 视图：告警统计
-- ============================================
CREATE OR REPLACE VIEW v_alert_summary AS
SELECT
    device_id,
    alert_type,
    alert_level,
    COUNT(*) as alert_count,
    MAX(alert_time) as last_alert_time
FROM alert_record
WHERE alert_time > NOW() - INTERVAL '24 hours'
GROUP BY device_id, alert_type, alert_level;

-- ============================================
-- 函数：计算小时平均提水量
-- ============================================
CREATE OR REPLACE FUNCTION get_hourly_water_flow(p_device_id INTEGER, p_hours INTEGER DEFAULT 24)
RETURNS TABLE (
    hour_bucket TIMESTAMPTZ,
    avg_water_flow DECIMAL,
    max_water_flow DECIMAL,
    min_water_flow DECIMAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        time_bucket('1 hour', time) as hour_bucket,
        AVG(water_flow) as avg_water_flow,
        MAX(water_flow) as max_water_flow,
        MIN(water_flow) as min_water_flow
    FROM sensor_data
    WHERE device_id = p_device_id
      AND time > NOW() - (p_hours || ' hours')::INTERVAL
    GROUP BY hour_bucket
    ORDER BY hour_bucket;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 函数：检查链条断裂风险
-- ============================================
CREATE OR REPLACE FUNCTION check_chain_failure_risk(p_device_id INTEGER)
RETURNS TABLE (
    risk_level VARCHAR(20),
    current_tension DECIMAL,
    allowable_tension DECIMAL,
    tension_ratio DECIMAL,
    recommendation TEXT
) AS $$
DECLARE
    v_current_tension DECIMAL;
    v_allowable_tension DECIMAL;
    v_ratio DECIMAL;
BEGIN
    SELECT sd.chain_tension, clp.allowable_tension
    INTO v_current_tension, v_allowable_tension
    FROM v_latest_sensor_data sd
    JOIN chain_link_params clp ON sd.device_id = clp.device_id
    WHERE sd.device_id = p_device_id
    LIMIT 1;

    IF v_current_tension IS NULL OR v_allowable_tension IS NULL THEN
        RETURN QUERY SELECT 'UNKNOWN', NULL::DECIMAL, NULL::DECIMAL, NULL::DECIMAL, '数据不足'::TEXT;
        RETURN;
    END IF;

    v_ratio := v_current_tension / v_allowable_tension;

    IF v_ratio >= 0.9 THEN
        RETURN QUERY SELECT 'CRITICAL', v_current_tension, v_allowable_tension, v_ratio, '立即停机检查，链条有断裂风险！'::TEXT;
    ELSIF v_ratio >= 0.75 THEN
        RETURN QUERY SELECT 'WARNING', v_current_tension, v_allowable_tension, v_ratio, '张力偏高，建议检查润滑和磨损情况'::TEXT;
    ELSIF v_ratio >= 0.5 THEN
        RETURN QUERY SELECT 'NORMAL', v_current_tension, v_allowable_tension, v_ratio, '运行正常'::TEXT;
    ELSE
        RETURN QUERY SELECT 'LOW', v_current_tension, v_allowable_tension, v_ratio, '张力偏低，建议张紧链条'::TEXT;
    END IF;
END;
$$ LANGUAGE plpgsql;
