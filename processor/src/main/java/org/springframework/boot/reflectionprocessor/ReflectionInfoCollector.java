/*
 * Copyright 2012-2018 the original author or authors.
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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import org.springframework.boot.graal.reflectconfig.ClassDescriptor;
import org.springframework.boot.graal.reflectconfig.MethodDescriptor;
import org.springframework.boot.graal.reflectconfig.ReflectionDescriptor;

/**
 * Used by {@link ReflectiveAccessAnnotationProcessor} to collect
 * {@link ReflectionDescriptor}.
 *
 * @author Andy Clement
 */
public class ReflectionInfoCollector {

	private final static String DEFAULTS = "reflect.defaults.json";

	private final List<ClassDescriptor> classDescriptors = new ArrayList<>();

	private final ProcessingEnvironment processingEnvironment;

	private final ReflectionDescriptor previousMetadata;

	private final TypeUtils typeUtils;

	private final Set<String> processedSourceTypes = new HashSet<>();

	private Messager messager;

	public ReflectionInfoCollector(ProcessingEnvironment processingEnvironment, ReflectionDescriptor previousMetadata) {
		this.processingEnvironment = processingEnvironment;
		messager = processingEnvironment.getMessager();
		this.previousMetadata = previousMetadata;
		this.typeUtils = new TypeUtils(processingEnvironment);
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

	public ReflectionDescriptor getMetadata() {
		ReflectionDescriptor metadata = new ReflectionDescriptor();
		for (ClassDescriptor cd : this.classDescriptors) {
			metadata.add(cd);
		}
//		if (this.previousMetadata != null) {
//			List<ClassDescriptor> cds = this.previousMetadata.getClassDescriptors();
//			for (ClassDescriptor cd : cds) {
//				if (shouldBeMerged(cd)) {
//					metadata.add(cd);
//				}
//			}
//		}
		return metadata;
	}

	private boolean shouldBeMerged(ClassDescriptor cd) {
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
		for (ClassDescriptor cd : classDescriptors) {
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
		return this.classDescriptors.add(cd);
	}

	public void mergeClassDescriptor(ClassDescriptor cd) {
		ClassDescriptor exists = findClassDescriptor(cd);
		if (exists == null) {
			this.classDescriptors.add(cd);
		} else {
			exists.merge(cd);
		}

	}

	public void init(String projectCompilationClasspath) {
		// Merge the 'defaults' for a boot app from the defaults json file into the
		// results being collected
		mergeDefaults();
		processSpringFactories(projectCompilationClasspath);
	}

	private void mergeDefaults() {
		try (InputStream defaults = ReflectionInfoCollector.class.getClassLoader().getResourceAsStream(DEFAULTS)) {
			ReflectionDescriptor defaultReflectEntries = ReflectStore.readMetadata(defaults);
			for (ClassDescriptor cd : defaultReflectEntries.getClassDescriptors()) {
				if (typeAvailable(cd.getName())) {
					if (findClassDescriptor(cd) != null) {
						addClassDescriptor(cd);
					} else {
						mergeClassDescriptor(cd);
					}
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Unable to load defaults", e);
		}
	}

	private void processSpringFactories(String projectCompilationClasspath) {
		if (projectCompilationClasspath == null) {
			messager.printMessage(Kind.WARNING, "project compilation classpath is not set for processor");
			return;
		}
		StringTokenizer st = new StringTokenizer(projectCompilationClasspath, ":");
		List<URL> urls = new ArrayList<>();
		while (st.hasMoreElements()) {
			try {
				urls.add(new File(st.nextToken()).toURI().toURL());
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}

		try (URLClassLoader ucl = new URLClassLoader(urls.toArray(new URL[] {}), null)) {
			Enumeration<URL> resources = ucl.getResources("META-INF/spring.factories");
			while (resources.hasMoreElements()) {
				URL nextElement = resources.nextElement();
				Properties p = new Properties();
				try (InputStream is = nextElement.openStream()) {
					p.load(is);
				}
				String typesMaybeNeedingReflectiveAccess = (String) p
						.get("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private boolean typeAvailable(String typename) {
		boolean b = processingEnvironment.getElementUtils().getTypeElement(typename) != null;
		System.out.println("Looking for "+typename+" found = "+b);
		return b;
	}

	public void addNoArgConstructorDescriptor(String type) {
		ClassDescriptor cd = ClassDescriptor.of(type);
		cd.addMethodDescriptor(MethodDescriptor.of(MethodDescriptor.CONSTRUCTOR_NAME));
		this.mergeClassDescriptor(cd);
	}

}
