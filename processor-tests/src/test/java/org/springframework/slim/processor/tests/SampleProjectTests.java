/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.slim.processor.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.graal.reflectconfig.ClassDescriptor;
import org.springframework.boot.graal.reflectconfig.MethodDescriptor;
import org.springframework.boot.graal.reflectconfig.ReflectionDescriptor;
import org.springframework.cloud.function.compiler.java.CompilationResult;

/**
 * Compile test-projects and review the created reflect.json files.
 * 
 * @author Andy Clement
 */
public class SampleProjectTests extends TestInfrastructure {

	private static File testProjectsFolder = new File("test-projects");

	// Vanilla dumb project, verify defaults in place
	@Test
	public void demo1() {
		File project = new File(testProjectsFolder, "demo1");
		CompilationResult result = compileProject(project);
		String generatedReflectJson = result.getGeneratedFile("reflect.json");
		assertNotNull(generatedReflectJson);
		ReflectionDescriptor rd = ReflectionDescriptor.of(generatedReflectJson);
		assertTrue(rd.hasClassDescriptor("org.springframework.context.annotation.AnnotationConfigApplicationContext"));
	}

	// Project has @Configuration, verify ctor for that type added to reflect.json
	@Test
	public void demo2() throws Exception {
		File project = new File(testProjectsFolder, "demo2");
		CompilationResult result = compileProject(project);
		System.out.println(result.getCompilationMessages());
		String generatedReflectJson = result.getGeneratedFile("META-INF/native-image/reflection-config.json");
		System.out.println(generatedReflectJson);
		ReflectionDescriptor rd = ReflectionDescriptor.of(generatedReflectJson);
		List<ClassDescriptor> cds = rd.getClassDescriptors();
		for (ClassDescriptor cd: cds) {
			System.out.println("cd:"+cd.getName());
		}

		String generatedResourceJson = result.getGeneratedFile("META-INF/native-image/resource-config.json");
		System.out.println(generatedResourceJson);
		String nativeImageProperties = result.getGeneratedFile("META-INF/native-image/native-image.properties");
		System.out.println(nativeImageProperties);
		//ReflectionDescriptor rd = ReflectionDescriptor.of(generatedReflectJson);
		//ClassDescriptor classDescriptor = rd.getClassDescriptor("com.example.demo2.GreetingAutoConfiguration");
		//assertNotNull(classDescriptor);
		//MethodDescriptor methodDescriptor = classDescriptor.getMethodDescriptor(MethodDescriptor.CONSTRUCTOR_NAME);
		//assertNotNull(methodDescriptor);
	}

	// Project has @RestController, verify ctor for that type added to reflect.json
	@Test
	public void demo3() {
		File project = new File(testProjectsFolder, "demo3");
		CompilationResult result = compileProject(project);
		String generatedReflectJson = result.getGeneratedFile("reflect.json");
		assertNotNull(generatedReflectJson);
		ReflectionDescriptor rd = ReflectionDescriptor.of(generatedReflectJson);
		ClassDescriptor classDescriptor = rd.getClassDescriptor("com.example.demo1.Foo");
		assertNotNull(classDescriptor);
		MethodDescriptor methodDescriptor = classDescriptor.getMethodDescriptor(MethodDescriptor.CONSTRUCTOR_NAME);
		assertNotNull(methodDescriptor);
	}

}