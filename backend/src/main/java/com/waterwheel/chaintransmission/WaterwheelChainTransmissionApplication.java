package com.waterwheel.chaintransmission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WaterwheelChainTransmissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaterwheelChainTransmissionApplication.class, args);
        System.out.println("==============================================");
        System.out.println("  古代水转翻车链传动动力学仿真系统启动成功!");
        System.out.println("  Ancient Waterwheel Chain Transmission System");
        System.out.println("  API: http://localhost:8080/api");
        System.out.println("==============================================");
    }
}
