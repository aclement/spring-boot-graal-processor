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
package org.springframework.boot.reflectionprocessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import javax.tools.Diagnostic.Kind;

import org.springframework.boot.graal.reflectconfig.JsonMarshaller;
import org.springframework.boot.graal.reflectconfig.ReflectionDescriptor;

/**
 * A {@code ConfigFileStorageManager} is responsible for the reading/writing of native-image configuration information from/to
 * the filesystem. The data needs to be stored under the <tt>META-INF/native-image</tt> folder.
 *
 * @author Andy Clement
 */
public class ConfigFileStorageManager {

	// TODO doesn't include the groupid/artifactid in the path (native-image/group/artifact/...)
	private static final String REFLECT_CONFIG_PATH = "reflection-config.json";

	private static final String RESOURCE_CONFIG_PATH = "resource-config.json";
	
	private static final String DYNAMIC_PROXY_PATH = "dynamic-proxies.json";
	
	private static final String NATIVE_IMAGE_PROPERTIES_FILE = "native-image.properties";
	
	private String groupId = null;
	
	private String artifactId = null;

	private final ProcessingEnvironment environment;

	public ConfigFileStorageManager(ProcessingEnvironment environment) {
		this.environment = environment;
		artifactId = this.environment.getOptions().get(ReflectiveAccessAnnotationProcessor.OPTIONS_ARTIFACTID);
		groupId = this.environment.getOptions().get(ReflectiveAccessAnnotationProcessor.OPTIONS_GROUPID);
	}
	
	public String toNativeImagePath(String file) {
		StringBuilder path = new StringBuilder("META-INF/native-image/");
		if (groupId != null) {
			path.append(groupId).append("/");
		}
		if (artifactId != null) {
			path.append(artifactId).append("/");
		}
		path.append(file);
		return path.toString();
	}
	
	public List<List<String>> readDynamicProxies() {
		String file = toNativeImagePath(DYNAMIC_PROXY_PATH);
		try (InputStream in = getConfigResource(file).openInputStream()) {
			if (in != null) {
				return JsonMarshaller.readDynamicProxyList(in);
			}
		} catch (IOException ex) {
			// ok
		} catch (Exception ex) {
			throw new RuntimeException("Invalid dynamic proxy list in '" + file + "': " + ex.getMessage());
		}
		return Collections.emptyList();
	}

	public ReflectionDescriptor readReflectConfig() {
		String file = toNativeImagePath(REFLECT_CONFIG_PATH);
		try (InputStream in = getConfigResource(file).openInputStream()) {
			if (in != null) {
				return JsonMarshaller.readReflectConfig(in);
			}
		} catch (IOException ex) {
			// ok
		} catch (Exception ex) {
			throw new RuntimeException("Invalid config in '" + file + "': " + ex.getMessage());
		}
		return null;
	}

	public List<String> readResourceConfig() {
		String file = toNativeImagePath(RESOURCE_CONFIG_PATH);
		try (InputStream in = getConfigResource(file).openInputStream()) {
			if (in != null) {
				return JsonMarshaller.readResourceConfig(in);
			}
		} catch (IOException ex) {
			// ok
		} catch (Exception ex) {
			throw new RuntimeException("Invalid config in '" + file + "': " + ex.getMessage());
		}
		return Collections.emptyList();
	}

	public void writeResourceConfig(List<String> resourcePatterns) throws IOException {
		String file = toNativeImagePath(RESOURCE_CONFIG_PATH);
		if (!resourcePatterns.isEmpty()) {
			try (OutputStream outputStream = createConfigResource(file).openOutputStream()) {
				new JsonMarshaller().writeResourceConfig(resourcePatterns, outputStream);
			}
		}
	}

	public void writeDynamicProxyList(List<List<String>> dynamicProxyList) throws IOException {
		String file = toNativeImagePath(DYNAMIC_PROXY_PATH);
		if (!dynamicProxyList.isEmpty()) {
			try (OutputStream outputStream = createConfigResource(file).openOutputStream()) {
				new JsonMarshaller().writeDynamicProxyList(dynamicProxyList, outputStream);
			}
		}
	}
	
	public void writeReflectionConfig(ReflectionDescriptor metadata) throws IOException {
		String file = toNativeImagePath(REFLECT_CONFIG_PATH);
		if (!metadata.isEmpty()) {
			try (OutputStream outputStream = createConfigResource(file).openOutputStream()) {
				new JsonMarshaller().writeReflectConfig(metadata, outputStream);
			}
		}
	}

	private FileObject getConfigResource(String location) throws IOException {
		return this.environment.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "",
				location);
	}

	private FileObject createConfigResource(String location) throws IOException {
		return this.environment.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
				"", location);
	}

	public void writeIfNecessary(ReflectionDescriptor reflectionDescriptor, List<String> resourcePatterns,List<List<String>> dynamicProxies)
			throws IOException {
		System.out.println("Outputting data: cds?"+!reflectionDescriptor.isEmpty()+"  rps?"+!resourcePatterns.isEmpty()+"  dps?"+!dynamicProxies.isEmpty());
		boolean outputNativeImageProperties = false;
		if (!reflectionDescriptor.isEmpty()) {
			writeReflectionConfig(reflectionDescriptor);
			outputNativeImageProperties=true;
		}
		// Write out the resources.json file
		if (!resourcePatterns.isEmpty()) {
			note("ResourcePatterns:#"+resourcePatterns.size()+": "+resourcePatterns);
			writeResourceConfig(resourcePatterns);
			//outputNativeImageProperties=true;
		}
		if (!dynamicProxies.isEmpty()) {
			writeDynamicProxyList(dynamicProxies);	
			outputNativeImageProperties=true;
		}
		// Write out the native-image.properties
		// For example: Args=-H:ReflectionConfigurationResources=${.}/reflection-config.json
		if (outputNativeImageProperties) {
			String file = toNativeImagePath(NATIVE_IMAGE_PROPERTIES_FILE);
		note("Generating "+file);
		try (OutputStream outputStream = createConfigResource(file).openOutputStream()) {
			PrintWriter pw = new PrintWriter(outputStream);
			pw.println("Generated by Spring Graal annotation processor at "+new Date().toString());
			StringBuilder argsValue = new StringBuilder();
			if (!reflectionDescriptor.isEmpty()) {
				argsValue.append(" -H:ReflectionConfigurationResources=${.}/reflection-config.json");
			}
			if (!dynamicProxies.isEmpty()) {
				argsValue.append(" -H:DynamicProxyConfigurationResources=${.}/dynamic-proxies.json");
			}
			/*
			if (!resourcePatterns.isEmpty()) {
				argsValue.append(" -H:ResourceConfigurationFiles=${.}/resource-config.json");
			}
			*/
			pw.println("Args="+argsValue.toString().trim());
			pw.close();
		}
		}
	}
	
	private void note(String msg) {
		log(Kind.NOTE, msg);
		System.out.println(msg); // because maven isn't outputting annotation processor logging...
	}
	
	private void log(Kind kind, String msg) {
		environment.getMessager().printMessage(kind, msg);
	}

}
