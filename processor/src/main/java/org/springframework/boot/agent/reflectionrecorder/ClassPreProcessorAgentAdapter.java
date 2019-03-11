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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Class pre-processor.
 *
 * @author Andy Clement
 */
public class ClassPreProcessorAgentAdapter implements ClassFileTransformer {

	private static RecorderPreProcessor preProcessor;

	private static ClassPreProcessorAgentAdapter instance;

	public ClassPreProcessorAgentAdapter() {
		instance = this;
	}

	static {
		try {
			preProcessor = new RecorderPreProcessor();
			preProcessor.initialize();
		} catch (Exception e) {
			throw new ExceptionInInitializerError("could not initialize JSR163 preprocessor due to: " + e.toString());
		}
	}

	/**
	 * @param loader              the defining class loader
	 * @param className           the name of class being loaded
	 * @param classBeingRedefined when hotswap is called
	 * @param protectionDomain    the ProtectionDomain for the class represented by
	 *                            the bytes
	 * @param bytes               the bytecode before weaving
	 * @return the weaved bytecode
	 */
	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
		try {
			return preProcessor.preProcess(loader, className, protectionDomain, bytes);
		} catch (Throwable t) {
			new RuntimeException("Reloading agent exited via exception", t).printStackTrace();
			return bytes;
		}
	}

	public static void reload(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
		instance.transform(loader, className, classBeingRedefined, protectionDomain, bytes);
	}

}
