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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

import org.springframework.boot.graal.reflectconfig.ReflectionDescriptor;

/**
 * Annotation {@link Processor} that writes Graal reflect.json for Spring Boot apps.
 * See <a href="https://github.com/oracle/graal/blob/master/substratevm/REFLECTION.md">
 * https://github.com/oracle/graal/blob/master/substratevm/REFLECTION.md</a>
 *
 * @author Andy Clement
 */
@SupportedAnnotationTypes({ "*" })
public class ReflectiveAccessAnnotationProcessor extends AbstractProcessor {

	static final String CONFIGURATION_ANNOTATION = "org.springframework.context."
			+ "annotation.Configuration";
	
	static final String RESTCONTROLLER_ANNOTATION = "org.springframework.web.bind.annotation.RestController";

	static final String CLASSPATH = "org.springframework.boot.reflectiveaccessannotationprocessor.classpath";

	private static final Set<String> SUPPORTED_OPTIONS = Collections
			.unmodifiableSet(Collections.singleton(CLASSPATH));

	private ReflectStore metadataStore;

	private ReflectionInfoCollector metadataCollector;

	private TypeUtils typeUtils;

	protected String configurationAnnotation() {
		return CONFIGURATION_ANNOTATION;
	}

	protected String restControllerAnnotation() {
		return RESTCONTROLLER_ANNOTATION;
	}
	
	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public Set<String> getSupportedOptions() {
		return SUPPORTED_OPTIONS;
	}

	@Override
	public synchronized void init(ProcessingEnvironment env) {
		super.init(env);
		this.typeUtils = new TypeUtils(env);
		this.metadataStore = new ReflectStore(env);
		this.metadataCollector = new ReflectionInfoCollector(env, this.metadataStore.readMetadata());
		String projectCompilationClasspath = env.getOptions().get(CLASSPATH);
		if (projectCompilationClasspath == null) {
			env.getMessager().printMessage(Kind.WARNING,CLASSPATH+" option not set for processor");
		}
		this.metadataCollector.init(projectCompilationClasspath);
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations,
			RoundEnvironment roundEnv) {
		this.metadataCollector.processing(roundEnv);
		Elements elementUtils = this.processingEnv.getElementUtils();

		// TODO very simplistic, not even looking for meta annotated yet

		TypeElement annotationType = elementUtils.getTypeElement(configurationAnnotation());
		if (annotationType != null) {
			for (Element element: roundEnv.getElementsAnnotatedWith(annotationType)) {
				processElement(element);
			}
		}

		annotationType = elementUtils.getTypeElement(restControllerAnnotation());
		if (annotationType != null) {
			for (Element element: roundEnv.getElementsAnnotatedWith(annotationType)) {
				processElement(element);
			}
		}

		if (roundEnv.processingOver()) {
			try {
				writeReflectJson();
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to write metadata", ex);
			}
		}
		return false;
	}

	private Map<Element, List<Element>> getElementsAnnotatedOrMetaAnnotatedWith(
			RoundEnvironment roundEnv, TypeElement annotation) {
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		Map<Element, List<Element>> result = new LinkedHashMap<>();
		for (Element element : roundEnv.getRootElements()) {
			LinkedList<Element> stack = new LinkedList<>();
			stack.push(element);
			collectElementsAnnotatedOrMetaAnnotatedWith(annotationType, stack);
			stack.removeFirst();
			if (!stack.isEmpty()) {
				result.put(element, Collections.unmodifiableList(stack));
			}
		}
		return result;
	}

	private boolean collectElementsAnnotatedOrMetaAnnotatedWith(
			DeclaredType annotationType, LinkedList<Element> stack) {
		Element element = stack.peekLast();
		for (AnnotationMirror annotation : this.processingEnv.getElementUtils()
				.getAllAnnotationMirrors(element)) {
			Element annotationElement = annotation.getAnnotationType().asElement();
			if (!stack.contains(annotationElement)) {
				stack.addLast(annotationElement);
				if (annotationElement.equals(annotationType.asElement())) {
					return true;
				}
				if (!collectElementsAnnotatedOrMetaAnnotatedWith(annotationType, stack)) {
					stack.removeLast();
				}
			}
		}
		return false;
	}

	private void processElement(Element element) {
		try {
			AnnotationMirror annotation = getAnnotation(element, configurationAnnotation());
			if (annotation != null) {
				String prefix = getPrefix(annotation);
				if (element instanceof TypeElement) {
					String type = this.typeUtils.getQualifiedName(element);
					addConstructorDescriptor(type);
//					processAnnotatedTypeElement(prefix, (TypeElement) element);
				}
				else if (element instanceof ExecutableElement) {
					processExecutableElement(prefix, (ExecutableElement) element);
				}
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException(
					"Error processing configuration meta-data on " + element, ex);
		}
		
		try {
			AnnotationMirror annotation = getAnnotation(element, restControllerAnnotation());
			if (annotation != null) {
				String prefix = getPrefix(annotation);
				if (element instanceof TypeElement) {
					String type = this.typeUtils.getQualifiedName(element);
					addConstructorDescriptor(type);
//					processAnnotatedTypeElement(prefix, (TypeElement) element);
				}
				else if (element instanceof ExecutableElement) {
					processExecutableElement(prefix, (ExecutableElement) element);
				}
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException(
					"Error processing configuration meta-data on " + element, ex);
		}
	}
	
	private void addConstructorDescriptor(String type) {
		System.out.println(">>> Adding ctor descriptor "+type);
		metadataCollector.addNoArgConstructorDescriptor(type);
	}

//	private void processAnnotatedTypeElement(String prefix, TypeElement element) {
//		String type = this.typeUtils.getQualifiedName(element);
//		this.metadataCollector.add(ItemMetadata.newGroup(prefix, type, type, null));
//		processTypeElement(prefix, element, null);
//	}

	private void processExecutableElement(String prefix, ExecutableElement element) {
		if (element.getModifiers().contains(Modifier.PUBLIC)
				&& (TypeKind.VOID != element.getReturnType().getKind())) {
			Element returns = this.processingEnv.getTypeUtils()
					.asElement(element.getReturnType());
			if (returns instanceof TypeElement) {
//				ItemMetadata group = ItemMetadata.newGroup(prefix,
//						this.typeUtils.getQualifiedName(returns),
//						this.typeUtils.getQualifiedName(element.getEnclosingElement()),
//						element.toString());
//				if (this.metadataCollector.hasSimilarGroup(group)) {
//					this.processingEnv.getMessager().printMessage(Kind.ERROR,
//							"Duplicate `@ConfigurationProperties` definition for prefix '"
//									+ prefix + "'",
//							element);
//				}
//				else {
//					this.metadataCollector.add(group);
//					processTypeElement(prefix, (TypeElement) returns, element);
//				}
			}
		}
	}

	private void processTypeElement(String prefix, TypeElement element,
			ExecutableElement source) {
//		TypeElementMembers members = new TypeElementMembers(this.processingEnv,
//				this.fieldValuesParser, element);
//		Map<String, Object> fieldValues = members.getFieldValues();
//		processSimpleTypes(prefix, element, source, members, fieldValues);
//		processSimpleLombokTypes(prefix, element, source, members, fieldValues);
//		processNestedTypes(prefix, element, source, members);
//		processNestedLombokTypes(prefix, element, source, members);
	}

//	private void processSimpleTypes(String prefix, TypeElement element,
//			ExecutableElement source, TypeElementMembers members,
//			Map<String, Object> fieldValues) {
//		members.getPublicGetters().forEach((name, getter) -> {
//			TypeMirror returnType = getter.getReturnType();
//			ExecutableElement setter = members.getPublicSetter(name, returnType);
//			VariableElement field = members.getFields().get(name);
//			Element returnTypeElement = this.processingEnv.getTypeUtils()
//					.asElement(returnType);
//			boolean isExcluded = this.typeExcludeFilter.isExcluded(returnType);
//			boolean isNested = isNested(returnTypeElement, field, element);
//			boolean isCollection = this.typeUtils.isCollectionOrMap(returnType);
//			if (!isExcluded && !isNested && (setter != null || isCollection)) {
//				String dataType = this.typeUtils.getType(returnType);
//				String sourceType = this.typeUtils.getQualifiedName(element);
//				String description = this.typeUtils.getJavaDoc(field);
//				Object defaultValue = fieldValues.get(name);
//				boolean deprecated = isDeprecated(getter) || isDeprecated(setter)
//						|| isDeprecated(source);
//				this.metadataCollector.add(ItemMetadata.newProperty(prefix, name,
//						dataType, sourceType, null, description, defaultValue,
//						deprecated ? getItemDeprecation(getter) : null));
//			}
//		});
//	}

//	private void processNestedTypes(String prefix, TypeElement element,
//			ExecutableElement source, TypeElementMembers members) {
//		members.getPublicGetters().forEach((name, getter) -> {
//			VariableElement field = members.getFields().get(name);
//			processNestedType(prefix, element, source, name, getter, field,
//					getter.getReturnType());
//		});
//	}

//	private void processEndpoint(Element element, List<Element> annotations) {
//		try {
//			String annotationName = this.typeUtils.getQualifiedName(annotations.get(0));
//			AnnotationMirror annotation = getAnnotation(element, annotationName);
//			if (element instanceof TypeElement) {
////				processEndpoint(annotation, (TypeElement) element);
//			}
//		}
//		catch (Exception ex) {
//			throw new IllegalStateException(
//					"Error processing configuration meta-data on " + element, ex);
//		}
//	}
//
//	private boolean hasMainReadOperation(TypeElement element) {
//		for (ExecutableElement method : ElementFilter
//				.methodsIn(element.getEnclosedElements())) {
//			if (hasAnnotation(method, readOperationAnnotation())
//					&& (TypeKind.VOID != method.getReturnType().getKind())
//					&& hasNoOrOptionalParameters(method)) {
//				return true;
//			}
//		}
//		return false;
//	}
//
//	private boolean hasNoOrOptionalParameters(ExecutableElement method) {
//		for (VariableElement parameter : method.getParameters()) {
//			if (!hasAnnotation(parameter, NULLABLE_ANNOTATION)) {
//				return false;
//			}
//		}
//		return true;
//	}
//
//	private boolean isNested(Element returnType, VariableElement field,
//			TypeElement element) {
//		if (hasAnnotation(field, nestedConfigurationPropertyAnnotation())) {
//			return true;
//		}
//		if (isCyclePresent(returnType, element)) {
//			return false;
//		}
//		return (isParentTheSame(returnType, element))
//				&& returnType.getKind() != ElementKind.ENUM;
//	}
//
//	private boolean isCyclePresent(Element returnType, Element element) {
//		if (!(element.getEnclosingElement() instanceof TypeElement)) {
//			return false;
//		}
//		if (element.getEnclosingElement().equals(returnType)) {
//			return true;
//		}
//		return isCyclePresent(returnType, element.getEnclosingElement());
//	}
//
//	private boolean isParentTheSame(Element returnType, TypeElement element) {
//		if (returnType == null || element == null) {
//			return false;
//		}
//		return getTopLevelType(returnType).equals(getTopLevelType(element));
//	}
//
//	private Element getTopLevelType(Element element) {
//		if (!(element.getEnclosingElement() instanceof TypeElement)) {
//			return element;
//		}
//		return getTopLevelType(element.getEnclosingElement());
//	}
//
//	private boolean isDeprecated(Element element) {
//		if (isElementDeprecated(element)) {
//			return true;
//		}
//		if (element instanceof VariableElement || element instanceof ExecutableElement) {
//			return isElementDeprecated(element.getEnclosingElement());
//		}
//		return false;
//	}
//
//	private boolean isElementDeprecated(Element element) {
//		return hasAnnotation(element, "java.lang.Deprecated")
//				|| hasAnnotation(element, deprecatedConfigurationPropertyAnnotation());
//	}
//
//	private boolean hasAnnotation(Element element, String type) {
//		return getAnnotation(element, type) != null;
//	}

	private AnnotationMirror getAnnotation(Element element, String type) {
		if (element != null) {
			for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
				if (type.equals(annotation.getAnnotationType().toString())) {
					return annotation;
				}
			}
		}
		return null;
	}

	private String getPrefix(AnnotationMirror annotation) {
		Map<String, Object> elementValues = getAnnotationElementValues(annotation);
		Object prefix = elementValues.get("prefix");
		if (prefix != null && !"".equals(prefix)) {
			return (String) prefix;
		}
		Object value = elementValues.get("value");
		if (value != null && !"".equals(value)) {
			return (String) value;
		}
		return null;
	}

	private Map<String, Object> getAnnotationElementValues(AnnotationMirror annotation) {
		Map<String, Object> values = new LinkedHashMap<>();
		annotation.getElementValues().forEach((name, value) -> values
				.put(name.getSimpleName().toString(), value.getValue()));
		return values;
	}

	protected ReflectionDescriptor writeReflectJson() throws Exception {
		ReflectionDescriptor metadata = this.metadataCollector.getMetadata();
		if (!metadata.isEmpty()) {
//		metadata = mergeAdditionalMetadata(metadata);
//		if (!metadata.getItems().isEmpty()) {
			this.metadataStore.writeMetadata(metadata);
			return metadata;
		}
		return null;
	}


	private void logWarning(String msg) {
		log(Kind.WARNING, msg);
	}

	private void log(Kind kind, String msg) {
		this.processingEnv.getMessager().printMessage(kind, msg);
	}

}
