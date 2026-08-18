package com.ticketbooking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Ticket Booking System API")
                .description("""
                    Production-grade concurrent ticket booking backend.
                    
                    Demonstrates: Java ReentrantLock, Pessimistic DB Locking, Optimistic Locking (@Version),
                    Redis Distributed Locking (Redisson), Idempotency, Deadlock Prevention, Flash Sales.
                    """)
                .version("1.0.0")
                .contact(new Contact().name("Ticket Booking Team"))
                .license(new License().name("MIT")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local"),
                new Server().url("http://app:8080").description("Docker")
            ));
    }
}
