FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY waterwheel_sensor_simulator.py .

ENV API_BASE=http://backend:8080/api/v1
ENV MQTT_HOST=mosquitto
ENV MQTT_PORT=1883
ENV MQTT_USER=admin
ENV MQTT_PASSWORD=admin
ENV MQTT_TOPIC=waterwheel/sensor
ENV INTERVAL=5
ENV CHAIN_SPEED=1.0
ENV SCRAPER_SHAPE=flat
ENV TZ=Asia/Shanghai

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import sys; sys.exit(0 if __import__('os').path.exists('/app/waterwheel_sensor_simulator.py') else 1)" || exit 1

ENTRYPOINT ["python", "waterwheel_sensor_simulator.py"]
CMD ["--mqtt", "mosquitto"]
