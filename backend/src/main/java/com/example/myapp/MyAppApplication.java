package com.example.myapp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
@SpringBootApplication
public class MyAppApplication extends SpringBootServletInitializer {
  @Override protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    return builder.sources(MyAppApplication.class);
  }
  public static void main(String[] args) { SpringApplication.run(MyAppApplication.class, args); }
}
