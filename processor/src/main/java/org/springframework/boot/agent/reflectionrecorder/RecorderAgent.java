/*
 * Copyright 2019
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
import java.lang.instrument.Instrumentation;

/**
 * Basic agent implementation. This agent is declared in the META-INF/MANIFEST.MF file - that is how it is 'plugged in'
 * to the JVM when '-javaagent:*.jar' is used.
 *
 * @author Andy Clement
 */
public class RecorderAgent {

	private static ClassFileTransformer transformer = new ClassPreProcessorAgentAdapter();

	private static Instrumentation instrumentation;

	public static void premain(String options, Instrumentation inst) {
		System.out.println("Recording reflection");
		// Handle duplicate agents
		if (instrumentation != null) {
			return;
		}
		instrumentation = inst;
		instrumentation.addTransformer(transformer);
	}

	public static void agentmain(String options, Instrumentation inst) {
		if (instrumentation != null) {
			return;
		}
		instrumentation = inst;
		instrumentation.addTransformer(transformer);
	}

	public static Instrumentation getInstrumentation() {
		if (instrumentation == null) {
			throw new UnsupportedOperationException("Java 5 was not started with preMain -javaagent for RecorderAgent");
		}
		return instrumentation;
	}

}
