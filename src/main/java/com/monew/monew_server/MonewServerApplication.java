package com.monew.monew_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MonewServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MonewServerApplication.class, args);
		System.out.println("home : http://localhost:8080/");
		System.out.println("actuator : http://localhost:8081/actuator");
	}
}
