package com.github.accessreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Application entry point for the GitHub organization access report service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GithubAccessReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(GithubAccessReportApplication.class, args);
    }
}
