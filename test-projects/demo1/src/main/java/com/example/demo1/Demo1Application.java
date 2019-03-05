package com.example.demo1;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class Demo1Application {

	public static void main(String[] args) {
		SpringApplication.run(Demo1Application.class, args);
	}

}

@Component
class Two implements CommandLineRunner {

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Demo1() running!");
	}
	
}