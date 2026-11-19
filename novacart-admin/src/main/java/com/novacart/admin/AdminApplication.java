package com.novacart.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** Separate NovaCart developer and customer-service console. */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.novacart.api")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
