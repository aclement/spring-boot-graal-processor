package com.example.demo2;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.example.demo2.GreetingAutoConfiguration.Foo;

@RunWith(SpringRunner.class)
@SpringBootTest
public class Demo2ApplicationTests {

	@Autowired 
	Foo foo;
	
	@Test
	public void contextLoads() {
		assertNotNull(foo);
	}

}
