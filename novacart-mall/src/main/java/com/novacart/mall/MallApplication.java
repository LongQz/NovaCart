package com.novacart.mall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/** NovaCart 用户业务网关，同时托管统一的 AI 能力模块。 */
@SpringBootApplication(
        scanBasePackages = {"com.novacart.mall", "com.novacart.ai"},
        excludeName = "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
)
@EnableCaching
@EnableScheduling
@EnableFeignClients(basePackages = "com.novacart.api")
@MapperScan("com.novacart.mall.mapper")
public class MallApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallApplication.class, args);
    }
}
