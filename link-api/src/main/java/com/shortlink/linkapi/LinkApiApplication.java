package com.shortlink.linkapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LinkApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinkApiApplication.class, args);
    }
}
