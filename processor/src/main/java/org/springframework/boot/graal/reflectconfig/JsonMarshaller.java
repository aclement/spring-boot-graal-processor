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

package org.springframework.boot.graal.reflectconfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.boot.graal.reflectconfig.ClassDescriptor.Flag;

/**
 * Marshaller to write {@link ReflectionDescriptor} as JSON.
 *
 * @author Andy Clement
 */
public class JsonMarshaller {

	private static final int BUFFER_SIZE = 4098;

	public void readReflectConfig(List<String> metadata, OutputStream outputStream)
			throws IOException {
		try {
			JsonConverter converter = new JsonConverter();
			JSONObject jsonObject = converter.resourceConfigToJsonObject(metadata);
			System.out.println("Writing resource config"+jsonObject);
			outputStream.write(jsonObject.toString(2).getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			if (ex instanceof IOException) {
				throw (IOException) ex;
			}
			if (ex instanceof RuntimeException) {
				throw (RuntimeException) ex;
			}
			throw new IllegalStateException(ex);
		}
	}

	public void writeReflectConfig(ReflectionDescriptor metadata, OutputStream outputStream)
			throws IOException {
		try {
			JsonConverter converter = new JsonConverter();
			JSONArray jsonArray = converter.reflectConfigToJsonArray(metadata);
			System.out.println("Writing reflect config"+jsonArray);
			outputStream.write(jsonArray.toString(2).getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			if (ex instanceof IOException) {
				throw (IOException) ex;
			}
			if (ex instanceof RuntimeException) {
				throw (RuntimeException) ex;
			}
			throw new IllegalStateException(ex);
		}
	}
	
	public void writeResourceConfig(List<String> patterns, OutputStream outputStream)
			throws IOException {
		try {
			JsonConverter converter = new JsonConverter();
			JSONObject jsonObject = converter.resourceConfigToJsonObject(patterns);
			outputStream.write(jsonObject.toString(2).getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			if (ex instanceof IOException) {
				throw (IOException) ex;
			}
			if (ex instanceof RuntimeException) {
				throw (RuntimeException) ex;
			}
			throw new IllegalStateException(ex);
		}
	}
	
		
	public void writeDynamicProxyList(List<List<String>> dynamicProxies, OutputStream outputStream)
			throws IOException {
		try {
			JsonConverter converter = new JsonConverter();
			JSONArray jsonObject = converter.dynamicProxyListToJsonArray(dynamicProxies);
			outputStream.write(jsonObject.toString(2).getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			if (ex instanceof IOException) {
				throw (IOException) ex;
			}
			if (ex instanceof RuntimeException) {
				throw (RuntimeException) ex;
			}
			throw new IllegalStateException(ex);
		}
	}
	
	public static ReflectionDescriptor readReflectConfig(String input) throws Exception {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))) {
			return readReflectConfig(bais);
		}
	}

	public static List<String> readResourceConfig(String input) throws Exception {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))) {
			return readResourceConfig(bais);
		}
	}

	public static ReflectionDescriptor readReflectConfig(InputStream inputStream) throws Exception {
		ReflectionDescriptor metadata = toReflectionDescriptor(new JSONArray(toString(inputStream)));
		return metadata;
	}

	public static List<List<String>> readDynamicProxyList(InputStream inputStream) throws Exception {
		List<List<String>> dynamicProxies = new ArrayList<>();
		JSONArray array = new JSONArray(toString(inputStream));
		for (int i=0;i<array.length();i++) {
		  JSONArray oneProxy = array.getJSONArray(i);	
		  List<String> oneProxyTypes = new ArrayList<>();
		  for (int j=0;j<oneProxy.length();j++) {
			  oneProxyTypes.add(oneProxy.getString(j));
		  }
		  dynamicProxies.add(oneProxyTypes);
		}
		return dynamicProxies;
	}
	
	public static List<String> readResourceConfig(InputStream inputStream) throws Exception {
		List<String> patterns = new ArrayList<>();
		JSONObject obj = new JSONObject(toString(inputStream));
		JSONArray resourcesArray = obj.getJSONArray("resources");
		for (int i=0;i<resourcesArray.length();i++) {
			patterns.add(resourcesArray.getJSONObject(i).getString("pattern"));
		}
		return patterns;
	}
	
	private static ReflectionDescriptor toReflectionDescriptor(JSONArray array) throws Exception {
		ReflectionDescriptor rd = new ReflectionDescriptor();
		for (int i=0;i<array.length();i++) {
			rd.add(toClassDescriptor((JSONObject)array.get(i)));
		}
		return rd;
	}
	
	private static ClassDescriptor toClassDescriptor(JSONObject object) throws Exception {
		ClassDescriptor cd = new ClassDescriptor();
		cd.setName(object.getString("name"));
		for (Flag f: Flag.values()) {
			if (object.optBoolean(f.name())) {
				cd.setFlag(f);
			}
		}
		JSONArray fields = object.optJSONArray("fields");
		if (fields != null) {
			for (int i=0;i<fields.length();i++) {
				cd.addFieldDescriptor(toFieldDescriptor(fields.getJSONObject(i)));
			}
		}
		JSONArray methods = object.optJSONArray("methods");
		if (methods != null) {
			for (int i=0;i<methods.length();i++) {
				cd.addMethodDescriptor(toMethodDescriptor(methods.getJSONObject(i)));
			}
		}
		return cd;
	}
	
	private static FieldDescriptor toFieldDescriptor(JSONObject object) throws Exception {
		String name = object.getString("name");
		boolean allowWrite = object.optBoolean("allowWrite");
		return new FieldDescriptor(name,allowWrite);
	}

	private static MethodDescriptor toMethodDescriptor(JSONObject object) throws Exception {
		String name = object.getString("name");
		JSONArray parameterTypes = object.optJSONArray("parameterTypes");
		List<String> listOfParameterTypes = null;
		if (parameterTypes != null) {
			listOfParameterTypes = new ArrayList<>();
			for (int i=0;i<parameterTypes.length();i++) {
				listOfParameterTypes.add(parameterTypes.getString(i));
			}
		}
		return new MethodDescriptor(name, listOfParameterTypes);
	}

	private static String toString(InputStream inputStream) throws IOException {
		StringBuilder out = new StringBuilder();
		InputStreamReader reader = new InputStreamReader(inputStream,
				StandardCharsets.UTF_8);
		char[] buffer = new char[BUFFER_SIZE];
		int bytesRead;
		while ((bytesRead = reader.read(buffer)) != -1) {
			out.append(buffer, 0, bytesRead);
		}
		return out.toString();
	}

}
