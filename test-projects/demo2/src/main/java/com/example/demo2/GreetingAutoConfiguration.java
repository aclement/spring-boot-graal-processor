package com.example.demo2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@ConditionalOnClass(Greeting.class)
//@EnableConfigurationProperties(GreetingProperties.class)
public class GreetingAutoConfiguration {
    
	@Bean
	public Foo foo() {
		return new Foo();
	}
	
    static class Foo{}
	
}
