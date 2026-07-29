package com.businesslogic;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients
@MapperScan("com.businesslogic.mapper")
@EnableDiscoveryClient
public class BusinessLogicEditorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessLogicEditorApplication.class, args);
    }
}
