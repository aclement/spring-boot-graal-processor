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
package org.springframework.boot.agent.reflectionrecorder;

import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;

public class RecorderPreProcessor {

	public void initialize() {
		Configuration.init();
	}

	public byte[] preProcess(ClassLoader classLoader, String slashedClassName, ProtectionDomain protectionDomain,
			byte[] bytes) {
		
//		System.out.println("Considering "+slashedClassName);
		// TODO rewrite reflection in system classes?
		if (classLoader == null && slashedClassName != null) { // Indicates loading of a system class
			return bytes;
		}
		if (slashedClassName != null && slashedClassName.contains("CGLIB")) {
//			ReflectiveInterceptor.recordCglibLoad(slashedClassName);
		}
		
		if (slashedClassName != null && slashedClassName.startsWith("org/springframework/boot/agent/reflectionrecorder")) {
			// Ignore ourselves
			return bytes;
		}
		Object[] data =  rewrite(bytes);
		if (Configuration.verboseMode) {
			System.out.println("Rewrote "+data[1]);
		}
		return (byte[])data[0];
	}
	
	public static Object[] rewrite(byte[] bytes) {
		ClassReader fileReader = new ClassReader(bytes);
		RewriteReflectionAdaptor classAdaptor = new RewriteReflectionAdaptor();
		try {
			fileReader.accept(classAdaptor, 0);
		} catch (Exception ex) {
			ex.printStackTrace();
			return new Object[] {bytes,classAdaptor.name};
		}
		byte[] bs = classAdaptor.getBytes();
		return new Object[] {bs,classAdaptor.name};
	}
	
}
