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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

/**
 * Annotation {@link Processor} that writes Graal reflect.json for Spring Boot
 * apps. See <a href=
 * "https://github.com/oracle/graal/blob/master/substratevm/REFLECTION.md">
 * https://github.com/oracle/graal/blob/master/substratevm/REFLECTION.md</a>
 *
 * @author Andy Clement
 */
@SupportedAnnotationTypes({ "*" })
public class ReflectiveAccessAnnotationProcessor extends AbstractProcessor {

	static final String COMPILATIONHINT_ANNOTATION = "org.springframework.CompilationHint";

	static final String CLASSPATH = "org.springframework.boot.reflectiveaccessannotationprocessor.classpath";

	private static final Set<String> SUPPORTED_OPTIONS = Collections.unmodifiableSet(Collections.singleton(CLASSPATH));

	private int roundCounter;

	private ConfigCollector configCollector;

	private TypeUtils typeUtils;

	private Messager messager;

	
	protected String compilationHint() {
		return COMPILATIONHINT_ANNOTATION;
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
		this.roundCounter = 1;
		this.typeUtils = new TypeUtils(env);
		this.configCollector = new ConfigCollector(env);
		this.messager = env.getMessager();
		//String projectCompilationClasspath = env.getOptions().get(CLASSPATH);
		//if (projectCompilationClasspath == null) {
		//	messager.printMessage(Kind.WARNING,CLASSPATH+" option not set for processor");
		//}
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		this.configCollector.processing(roundEnv);
		Elements elementUtils = this.processingEnv.getElementUtils();
		messager.printMessage(Kind.NOTE,"annotation processing round #"+(roundCounter++));
		TypeElement compilationHintType = elementUtils.getTypeElement(compilationHint());
		if (compilationHintType != null) {
			for (Element element: roundEnv.getRootElements()) {
				processElement(element);
			}
		}
		if (roundEnv.processingOver()) {
			try {
				this.configCollector.outputData();
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

	/**
	 * Determine if the element is annotated (or meta-annotated via another annotation) with a @CompilationHint. For any
	 * hints discovered add the correct types to the native-image config data.
	 */
	private void processElement(Element element) {
		try {
			Map<AnnotationMirror,AnnotationMirror> hints = findCompilationHints(element);
			// hints.keys are the @CompilationHints
			// hints.values are either null (if the hint is on a non annotation type) or
			// the annotation mirror that is meta annotated with the @CompilationHint 
			// (if it is being used as a meta annotation)

			// Example: 
			// Collected hints against Sample2 = {@org.springframework.CompilationHint(member={"foo"})=null}
			// Collected hints against Sample3 = {@org.springframework.CompilationHint(member={"foo"})=@com.example.demo2.Sample2(foo={"x", "y", "z"})}
			
			if (!hints.isEmpty()) {
				for (Map.Entry<AnnotationMirror,AnnotationMirror> hint: hints.entrySet()) {
					List<String> typeStrings = getStrings(hint.getKey(),hint.getValue());
					messager.printMessage(Kind.NOTE, 
						"for hint "+hint.getKey()+(hint.getValue()==null?"":" on "+hint.getValue())+" adding these types "+typeStrings);
					for (String t: typeStrings) {
						this.configCollector.addType(t);
					}
				}
			}
			
			List<? extends Element> enclosedElements = element.getEnclosedElements();
			for (Element enclosedElement: enclosedElements) {
				// TODO testcase!
				processElement(enclosedElement);		
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException(
					"Error processing configuration meta-data on " + element, ex);
		}
	}
	
	private void addConstructorDescriptor(String type) {
		configCollector.addNoArgConstructorDescriptor(type);
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

	private Map<AnnotationMirror, AnnotationMirror> findCompilationHints(Element element) {
		Map<AnnotationMirror, AnnotationMirror> annotationsOnType = new HashMap<>();
		collectHints(element, null, annotationsOnType, new HashSet<>());
		System.out.println("Collected hints against "+element.getSimpleName()+" = "+annotationsOnType);
		return annotationsOnType;
	}
	
	/**
	 * Returns a map from the compilation hint to the annotation they are on (if used as meta annotation).
	 */
	private void collectHints(Element element, AnnotationMirror mirror, Map<AnnotationMirror, AnnotationMirror> collector, Set<AnnotationMirror> visited) {
		if (mirror !=null && !visited.add(mirror)) {
			return;
		}
		if (element != null) {
			for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
				if (compilationHint().equals(annotation.getAnnotationType().toString())) {
					collector.put(annotation, mirror);
				} else { // is it meta annotated?
					collectHints(annotation.getAnnotationType().asElement(), annotation, collector, visited);
				}
			}
		}
	}

	private List<Object> getReferencedMembers(List<String> members,Element annotation) {
		try {
		List<Object> results = new ArrayList<>();
		Map<String, Object> elementValues = getAnnotationElementValues((AnnotationMirror)annotation);
		for (String member: members) {
			// TODO might not be a list, maybe just a reference to a single type, cope with that
			List l = (List)elementValues.get(member);
			if (l != null) {
				results.addAll(l);
			}
		}
		return results;
	} catch (Throwable t) {
		t.printStackTrace();
		return null;
	}
	}
	
	private List<String> getName(AnnotationMirror annotation) {
		Map<String, Object> elementValues = getAnnotationElementValues(annotation);
		Object prefix = elementValues.get("name");
		return (List<String>)prefix;
	}
	
	private List<String> getStrings(AnnotationMirror compilationHint, AnnotationMirror annotationIfHintUsedAsMeta) {
		List<String> names = new ArrayList<>();
		Map<String, Object> vs = getAnnotationElementValues(compilationHint);
		List<AnnotationValue> namesFromHint = (List<AnnotationValue>)vs.get("name");
		if (namesFromHint != null) {
			namesFromHint.stream().map(av -> (String)av.getValue()).collect(Collectors.toCollection(() -> names));
		}
		List<AnnotationValue> memberHints = (List<AnnotationValue>)vs.get("member");
		if (annotationIfHintUsedAsMeta != null && !memberHints.isEmpty()) {
			for (AnnotationValue memberRef: memberHints) {
					Map<String,Object> vs2 = getAnnotationElementValues(annotationIfHintUsedAsMeta);
					List<AnnotationValue> l = (List<AnnotationValue>)vs2.get(memberRef.getValue());
					if (!l.isEmpty()) {
						AnnotationValue firstElement = l.get(0);
						if (firstElement.getValue() instanceof String) {
							l.stream().map(av -> (String)av.getValue()).collect(Collectors.toCollection(() -> names));
						}
					}
			}	
		}
		return names;
	}
	
	//private List<Object> retrieveMetaReferenced(List<String> refs, AnnotationMirror annoThing, String fieldname) {
		//Map<String,Object> vs = getAnnotationElemet
	//}

	private List<Class> getValue(AnnotationMirror annotation) {
		Map<String, Object> elementValues = getAnnotationElementValues(annotation);
		Object prefix = elementValues.get("value");
		return (List<Class>)prefix;
	}
	
	private List<String> getMember(AnnotationMirror annotation) {
		Map<String, Object> elementValues = getAnnotationElementValues(annotation);
		Object prefix = elementValues.get("member");
		return (List<String>)(prefix==null?Collections.emptyList():prefix);
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

	private void logWarning(String msg) {
		log(Kind.WARNING, msg);
	}

	private void log(Kind kind, String msg) {
		this.processingEnv.getMessager().printMessage(kind, msg);
	}

}
