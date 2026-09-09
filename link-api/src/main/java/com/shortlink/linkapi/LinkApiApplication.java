package com.shortlink.linkapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Auth ở đây là API key, không có username/password: tắt in-memory user mặc định
// của Boot để nó không sinh password ngẫu nhiên và log ra mỗi lần khởi động.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LinkApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinkApiApplication.class, args);
    }
}
