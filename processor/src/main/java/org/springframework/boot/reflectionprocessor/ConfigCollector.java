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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.Diagnostic.Kind;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import org.springframework.boot.graal.reflectconfig.ClassDescriptor;
import org.springframework.boot.graal.reflectconfig.ClassDescriptor.Flag;
import org.springframework.boot.graal.reflectconfig.MethodDescriptor;
import org.springframework.boot.graal.reflectconfig.ReflectionDescriptor;

// TODO needs to handle deletion of files between calls (during incremental build)

/**
 * Used by {@link ReflectiveAccessAnnotationProcessor} to collect configuration data for passing to the native-image
 * command.
 *
 * @author Andy Clement
 */
public class ConfigCollector {

	private final List<ClassDescriptor> newClassDescriptors = new ArrayList<>();
	
	private final Set<String> newResourcePatterns = new LinkedHashSet<>();
	
	private final List<List<String>> newDynamicProxies = new ArrayList<>();

	private final ProcessingEnvironment processingEnvironment;

	private final ReflectionDescriptor existingReflectConfig;

	private final List<String> existingResourcePatterns;
	
	private final List<List<String>> existingDynamicProxies;

	private final TypeUtils typeUtils;

	private final Set<String> processedSourceTypes = new HashSet<>();

	private Messager messager;

	private ConfigFileStorageManager configFileStorageManager;


	public ConfigCollector(ProcessingEnvironment processingEnvironment) {
		this.processingEnvironment = processingEnvironment;
		this.messager = processingEnvironment.getMessager();
		this.typeUtils = new TypeUtils(processingEnvironment);
		this.configFileStorageManager = new ConfigFileStorageManager(processingEnvironment);
		this.existingReflectConfig = configFileStorageManager.readReflectConfig();
		this.existingResourcePatterns = configFileStorageManager.readResourceConfig();
		this.existingDynamicProxies = configFileStorageManager.readDynamicProxies();
	}
	

	public void processing(RoundEnvironment roundEnv) {
		for (Element element : roundEnv.getRootElements()) {
			markAsProcessed(element);
		}
	}

	private void markAsProcessed(Element element) {
		if (element instanceof TypeElement) {
			this.processedSourceTypes.add(this.typeUtils.getQualifiedName(element));
		}
	}

	public void outputData() throws IOException {
		configFileStorageManager.writeIfNecessary(getLatestReflectionDescriptor(),getLatestResourcePatterns(),getLatestDynamicProxies());
	}

	/**
	 * @return a complete descriptor that includes those discovered in this round of processing plus those loaded from the
	 * disk (produced during a previous set of processing).
	 */
	public ReflectionDescriptor getLatestReflectionDescriptor() {
		ReflectionDescriptor reflectionDescriptor = new ReflectionDescriptor();
		for (ClassDescriptor cd : this.newClassDescriptors) {
			reflectionDescriptor.add(cd);
		}
		if (this.existingReflectConfig != null) {
			List<ClassDescriptor> cds = this.existingReflectConfig.getClassDescriptors();
			for (ClassDescriptor cd : cds) {
				if (shouldBeAdded(cd)) {
					reflectionDescriptor.add(cd);
				}
			}
		}
		return reflectionDescriptor;
	}
	
	/**
	 * @return a complete list of patterns that includes those discovered in this round of processing plus those loaded from the
	 * disk (produced during a previous set of processing).
	 */
	public List<String> getLatestResourcePatterns() {
		List<String> rps = new ArrayList<>();
		rps.addAll(newResourcePatterns);
		rps.addAll(existingResourcePatterns);
		return rps;
	}
	
	public List<List<String>> getLatestDynamicProxies() {
		List<List<String>> dps = new ArrayList<>();
		dps.addAll(newDynamicProxies);
		dps.addAll(existingDynamicProxies);
		return dps;
	}

	private boolean shouldBeAdded(ClassDescriptor cd) {
		String sourceType = cd.getName(); // TODO map name to sourceType
		return (sourceType != null && !deletedInCurrentBuild(sourceType) && !processedInCurrentBuild(sourceType));
	}

	private boolean deletedInCurrentBuild(String sourceType) {
		return this.processingEnvironment.getElementUtils().getTypeElement(sourceType) == null;
	}

	private boolean processedInCurrentBuild(String sourceType) {
		return this.processedSourceTypes.contains(sourceType);
	}

	public ClassDescriptor findClassDescriptor(String typename) {
		for (ClassDescriptor cd : newClassDescriptors) {
			if (cd.getName().equals(typename)) {
				return cd;
			}
		}
		return null;
	}

	public ClassDescriptor findClassDescriptor(ClassDescriptor toFind) {
		return findClassDescriptor(toFind.getName());
	}

	public boolean addClassDescriptor(ClassDescriptor cd) {
		return this.newClassDescriptors.add(cd);
	}

	public void mergeClassDescriptor(ClassDescriptor cd) {
		ClassDescriptor exists = findClassDescriptor(cd);
		if (exists == null) {
			this.newClassDescriptors.add(cd);
		} else {
			exists.merge(cd);
		}

	}

	private boolean typeAvailable(String typename) {
		boolean b = processingEnvironment.getElementUtils().getTypeElement(typename) != null;
//		System.out.println("Looking for "+typename+" found = "+b);
		return b;
	}

	private Map<String, Object> getAnnotationElementValues(AnnotationMirror annotation) {
		Map<String, Object> values = new LinkedHashMap<>();
		annotation.getElementValues().forEach((name, value) -> {
			values.put(name.getSimpleName().toString(), value.getValue());
				});
		return values;
	}
	
	@SuppressWarnings("rawtypes")
	public void addConstructorDescriptor(String typename) {
		TypeElement typeElement = processingEnvironment.getElementUtils().getTypeElement(typename);
		TypeElement coc = processingEnvironment.getElementUtils().getTypeElement("org.springframework.boot.autoconfigure.condition.ConditionalOnClass");

		boolean cocCheckFailed= false;
		List<? extends AnnotationMirror> annotationMirrors = typeElement.getAnnotationMirrors();
		for (AnnotationMirror am: annotationMirrors) {
//			System.out.println("COC check on "+am);
			if (am.getAnnotationType().asElement().equals(coc)) {
				Map<String,Object> vals = getAnnotationElementValues(am);
//				System.out.println(vals.get("value"));
				Collection c = (Collection)vals.get("value");
				if (c != null) {
					for (Object o: c) {
						AnnotationValue av = (AnnotationValue)o;
						String s = av.toString(); // org.neo4j.ogm.session.Neo4jSession.class
						s = s.substring(0,s.length()-".class".length());
						if (!typeAvailable(s)) {
							cocCheckFailed=true;
							System.out.println("Rejecting "+typename+" because ConditionalOnClass for "+s+" not satisfied by classpath");
							break;
						}
					}
				}
			}
		}
		if (!cocCheckFailed) {
			System.out.println("Adding "+typename+" to reflect.json");
			ClassDescriptor cd = ClassDescriptor.of(typename);
			cd.setFlag(Flag.allDeclaredConstructors);
//			cd.setFlag(Flag.allDeclaredMethods);
			this.mergeClassDescriptor(cd);
		}
	}
	
	public void addResourcePatternForClass(String classname) {
		System.out.println("Adding resource pattern "+classname);
		this.newResourcePatterns.add(classname.replace(".","/").replace("$",".")+".class");
	}
		
	public void addResourcePattern(String pattern) {
		System.out.println("Adding resource pattern "+pattern);
		this.newResourcePatterns.add(pattern);
	}
	
	public void addDynamicProxy(List<String> proxyTypes) {
		for (List<String> existingProxy: newDynamicProxies) {
			if (existingProxy.equals(proxyTypes)) {
				note("+++ Skipping "+proxyTypes+" proxy, already there");
				return;
			}	
		}
		System.out.println("Adding dynamic proxy entry for these types "+proxyTypes);	
		this.newDynamicProxies.add(proxyTypes);
	}
	
	private void note(String msg) {
		log(Kind.NOTE, msg);
		System.out.println(msg); // because maven isn't outputting annotation processor logging...
	}
	
	private void log(Kind kind, String msg) {
		messager.printMessage(kind, msg);
	}
	
	public void addReflectionReference(String type, boolean allDeclaredConstructors, boolean allDeclaredMethods, boolean allDeclaredFields) {
		System.out.println("Adding type "+type);
		ClassDescriptor cd = ClassDescriptor.of(type);
		if (allDeclaredConstructors) {
			cd.setFlag(Flag.allDeclaredConstructors);
		}
		if (allDeclaredMethods) {
			cd.setFlag(Flag.allDeclaredMethods);
		}	
		if (allDeclaredFields) {
			cd.setFlag(Flag.allDeclaredFields);
		}
		this.mergeClassDescriptor(cd);
	}

	public void addNoArgConstructorDescriptor(String type) {
		ClassDescriptor cd = ClassDescriptor.of(type);
		cd.addMethodDescriptor(MethodDescriptor.of(MethodDescriptor.CONSTRUCTOR_NAME));
		this.mergeClassDescriptor(cd);
	}

}
