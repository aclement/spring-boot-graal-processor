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

import java.util.StringTokenizer;

/**
 *
 * @author Andy Clement
 */
public class Configuration {

	/**
	 * verbose mode can trigger extra messages. Enable with 'verbose=true'
	 */
	public static boolean verboseMode = false;
	
	public static boolean exit = false;
	
	public static String reflectFile;
	
	public static String whyType;

	public static boolean dontHideInfra;

	public static boolean reflectionSummary;
	

	private static void printUsage() {
		System.out.println("RecorderAgent");
		System.out.println("=============");
		System.out.println();
		System.out.println("Usage: java  -javaagent:<pathto>/spring-boot-graal-processor-XXX.jar");
		System.out.println("Optionally specify configuration through -Dspringbootgraal=<options>");
		System.out.println("<options> is a ';' separated list of directives or name=value options");
		System.out.println("Example: -Drecorderagent=ccc;aaa=bbb");
		System.out.println();
		System.out.println("Directives:");
		System.out.println("        ? - print this usage text");
		System.out.println("  verbose - more details reported as it runs");
		System.out.println(" dontHideInfra - if specified will produce more detail (for debugging the collector itself)");
		System.out.println("     exit - forces the process to finish once data output");
		System.out.println(" reflectionSummary - produce a report of who is calling reflection");
		System.out.println("Options:");
		System.out.println(" file=xxx - specify the name for the JSON file");
		System.out.println(" why=xxx - specify dotted type name and it will give you stack that led to it");
		System.exit(0);
	}

	static void init() {
		try {
			String value = System.getProperty("springbootgraal");
			// value is a ';' separated list of configuration options which either may be
			// name=value settings or directives (just a name)
			if (value != null) {
				StringTokenizer st = new StringTokenizer(value, ";");
				while (st.hasMoreTokens()) {
					String kv = st.nextToken();
					int equals = kv.indexOf('=');
					if (equals != -1) {
						// key=value
						String key = kv.substring(0, equals);
						if (key.equalsIgnoreCase("file")) { // global setting
							reflectFile = kv.substring(equals + 1);
							System.out.println("[sprinbootgraal config] reflect file = "+reflectFile);
						} else if (key.equalsIgnoreCase("why")) {
							whyType = kv.substring(equals + 1);
							System.out.println("[sprinbootgraal config] check on why this type is listed = "+whyType);							
						}
					} else {
						if (kv.equals("?")) {
							printUsage();
						} else if (kv.equalsIgnoreCase("verbose")) {
							System.out.println("[sprinbootgraal config] verbose mode on");
							verboseMode = true;
						} else if (kv.equalsIgnoreCase("dontHideInfra")) {
							System.out.println("[sprinbootgraal config] will include infrastructure details in output");
							dontHideInfra = true;
						} else if (kv.equalsIgnoreCase("reflectionSummary")) {
							System.out.println("[sprinbootgraal config] will produce reflection summary");
							reflectionSummary = true;
						} else if (kv.equalsIgnoreCase("exit")) {
							System.out.println("[sprinbootgraal config] will exit after data output");
							exit = true;
						}
					}
				}
			}
		} catch (Throwable t) {
			System.err.println("Unexpected problem reading global configuration setting:" + t.toString());
			t.printStackTrace();
		}
	}
}
