-- ============================================
-- TimescaleDB 降采样和保留策略配置
-- ============================================

-- ============================================
-- 1. 创建连续聚合视图 - 小时级别聚合
-- ============================================
CREATE MATERIALIZED VIEW sensor_data_hourly
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    device_id,
    AVG(sprocket_speed) AS avg_sprocket_speed,
    MAX(sprocket_speed) AS max_sprocket_speed,
    MIN(sprocket_speed) AS min_sprocket_speed,
    AVG(scraper_load) AS avg_scraper_load,
    MAX(scraper_load) AS max_scraper_load,
    AVG(chain_tension) AS avg_chain_tension,
    MAX(chain_tension) AS max_chain_tension,
    MIN(chain_tension) AS min_chain_tension,
    AVG(water_flow) AS avg_water_flow,
    MAX(water_flow) AS max_water_flow,
    MIN(water_flow) AS min_water_flow,
    AVG(vibration_amplitude) AS avg_vibration,
    MAX(vibration_amplitude) AS max_vibration,
    AVG(torque) AS avg_torque,
    MAX(torque) AS max_torque,
    COUNT(*) AS sample_count
FROM sensor_data
GROUP BY bucket, device_id
WITH NO DATA;

-- ============================================
-- 2. 创建连续聚合视图 - 天级别聚合
-- ============================================
CREATE MATERIALIZED VIEW sensor_data_daily
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', time) AS bucket,
    device_id,
    AVG(sprocket_speed) AS avg_sprocket_speed,
    MAX(sprocket_speed) AS max_sprocket_speed,
    MIN(sprocket_speed) AS min_sprocket_speed,
    AVG(scraper_load) AS avg_scraper_load,
    MAX(scraper_load) AS max_scraper_load,
    AVG(chain_tension) AS avg_chain_tension,
    MAX(chain_tension) AS max_chain_tension,
    MIN(chain_tension) AS min_chain_tension,
    AVG(water_flow) AS avg_water_flow,
    MAX(water_flow) AS max_water_flow,
    MIN(water_flow) AS min_water_flow,
    AVG(vibration_amplitude) AS avg_vibration,
    MAX(vibration_amplitude) AS max_vibration,
    AVG(torque) AS avg_torque,
    MAX(torque) AS max_torque,
    COUNT(*) AS sample_count
FROM sensor_data
GROUP BY bucket, device_id
WITH NO DATA;

-- ============================================
-- 3. 创建连续聚合视图 - 月级别聚合
-- ============================================
CREATE MATERIALIZED VIEW sensor_data_monthly
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 month', time) AS bucket,
    device_id,
    AVG(sprocket_speed) AS avg_sprocket_speed,
    MAX(sprocket_speed) AS max_sprocket_speed,
    MIN(sprocket_speed) AS min_sprocket_speed,
    AVG(scraper_load) AS avg_scraper_load,
    MAX(scraper_load) AS max_scraper_load,
    AVG(chain_tension) AS avg_chain_tension,
    MAX(chain_tension) AS max_chain_tension,
    MIN(chain_tension) AS min_chain_tension,
    AVG(water_flow) AS avg_water_flow,
    MAX(water_flow) AS max_water_flow,
    MIN(water_flow) AS min_water_flow,
    AVG(vibration_amplitude) AS avg_vibration,
    MAX(vibration_amplitude) AS max_vibration,
    AVG(torque) AS avg_torque,
    MAX(torque) AS max_torque,
    COUNT(*) AS sample_count
FROM sensor_data
GROUP BY bucket, device_id
WITH NO DATA;

-- ============================================
-- 4. 告警记录连续聚合 - 日报表
-- ============================================
CREATE MATERIALIZED VIEW alert_record_daily
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', alert_time) AS bucket,
    device_id,
    alert_type,
    alert_level,
    COUNT(*) AS alert_count
FROM alert_record
GROUP BY bucket, device_id, alert_type, alert_level
WITH NO DATA;

-- ============================================
-- 5. 设置连续聚合刷新策略
-- ============================================
SELECT add_continuous_aggregate_policy('sensor_data_hourly',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE);

SELECT add_continuous_aggregate_policy('sensor_data_daily',
    start_offset => INTERVAL '2 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE);

SELECT add_continuous_aggregate_policy('sensor_data_monthly',
    start_offset => INTERVAL '2 months',
    end_offset => INTERVAL '1 month',
    schedule_interval => INTERVAL '1 month',
    if_not_exists => TRUE);

SELECT add_continuous_aggregate_policy('alert_record_daily',
    start_offset => INTERVAL '2 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE);

-- ============================================
-- 6. 设置原始数据保留策略 - 365 天后自动删除
-- ============================================
SELECT add_retention_policy('sensor_data',
    drop_after => INTERVAL '365 days',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE);

-- ============================================
-- 7. 设置告警记录保留策略 - 730 天后自动删除
-- ============================================
SELECT add_retention_policy('alert_record',
    drop_after => INTERVAL '730 days',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE);

-- ============================================
-- 8. 设置小时聚合保留策略 - 2 年
-- ============================================
SELECT add_retention_policy('sensor_data_hourly',
    drop_after => INTERVAL '2 years',
    schedule_interval => INTERVAL '1 month',
    if_not_exists => TRUE);

-- ============================================
-- 9. 设置天聚合保留策略 - 5 年
-- ============================================
SELECT add_retention_policy('sensor_data_daily',
    drop_after => INTERVAL '5 years',
    schedule_interval => INTERVAL '1 month',
    if_not_exists => TRUE);

-- ============================================
-- 10. 启用压缩策略
-- ============================================
ALTER TABLE sensor_data SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'device_id',
    timescaledb.compress_orderby = 'time DESC'
);

SELECT add_compression_policy('sensor_data',
    compress_after => INTERVAL '7 days',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE);

ALTER TABLE alert_record SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'device_id',
    timescaledb.compress_orderby = 'alert_time DESC'
);

SELECT add_compression_policy('alert_record',
    compress_after => INTERVAL '30 days',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE);

-- ============================================
-- 11. 创建索引优化查询性能
-- ============================================
CREATE INDEX IF NOT EXISTS idx_sensor_data_hourly_bucket_device ON sensor_data_hourly (bucket DESC, device_id);
CREATE INDEX IF NOT EXISTS idx_sensor_data_daily_bucket_device ON sensor_data_daily (bucket DESC, device_id);
CREATE INDEX IF NOT EXISTS idx_sensor_data_monthly_bucket_device ON sensor_data_monthly (bucket DESC, device_id);
CREATE INDEX IF NOT EXISTS idx_alert_daily_bucket_device ON alert_record_daily (bucket DESC, device_id);

-- ============================================
-- 12. 视图：设备运行状态汇总
-- ============================================
CREATE OR REPLACE VIEW v_device_status_summary AS
SELECT
    wd.device_id,
    wd.device_name,
    wd.status,
    wd.location,
    sd.time AS last_data_time,
    sd.sprocket_speed AS last_speed,
    sd.chain_tension AS last_tension,
    sd.water_flow AS last_flow,
    sd.vibration_amplitude AS last_vibration,
    CASE
        WHEN sd.chain_tension > clp.allowable_tension * 0.9 THEN 'CRITICAL'
        WHEN sd.chain_tension > clp.allowable_tension * 0.75 THEN 'WARNING'
        ELSE 'NORMAL'
    END AS tension_status,
    EXTRACT(EPOCH FROM (NOW() - sd.time))::INTEGER AS seconds_since_last_data
FROM waterwheel_device wd
LEFT JOIN LATERAL (
    SELECT * FROM sensor_data
    WHERE device_id = wd.device_id
    ORDER BY time DESC
    LIMIT 1
) sd ON true
LEFT JOIN chain_link_params clp ON wd.device_id = clp.device_id
WHERE wd.status = 'ACTIVE';

-- ============================================
-- 配置完成提示
-- ============================================
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE 'TimescaleDB 降采样和保留策略配置完成';
    RAISE NOTICE '========================================';
    RAISE NOTICE '原始数据保留: 365 天';
    RAISE NOTICE '小时聚合保留: 2 年';
    RAISE NOTICE '天聚合保留: 5 年';
    RAISE NOTICE '月聚合保留: 永久';
    RAISE NOTICE '压缩策略: 7 天后自动压缩';
    RAISE NOTICE '告警保留: 730 天';
    RAISE NOTICE '========================================';
END $$;
