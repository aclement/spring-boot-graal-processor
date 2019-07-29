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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
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

	static final String COMPILATIONHINT_ANNOTATION = "org.springframework.core.annotation.CompilationHint";

	static final String CLASSPATH = "org.springframework.boot.reflectiveaccessannotationprocessor.classpath";
	
	static final String OPTIONS_GROUPID = "org.springframework.graal.processor.groupid";
	static final String OPTIONS_ARTIFACTID = "org.springframework.graal.processor.artifactId";

	private static final Set<String> SUPPORTED_OPTIONS = 
		Collections.unmodifiableSet(new HashSet<>(Arrays.asList(new String[]{OPTIONS_GROUPID,OPTIONS_ARTIFACTID})));

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
		System.out.println(">>options are:"+env.getOptions());
		System.out.println("Spring Graal Annotation Processor running...");
		this.roundCounter = 1;
		this.typeUtils = new TypeUtils(env);
		this.configCollector = new ConfigCollector(env);
		this.messager = env.getMessager();
		insertDefaults();
		//String projectCompilationClasspath = env.getOptions().get(CLASSPATH);
		//if (projectCompilationClasspath == null) {
		//	messager.printMessage(Kind.WARNING,CLASSPATH+" option not set for processor");
		//}
	}
	
	public void insertDefaults() {
		// TODO where would this come from? Just from scanning all resouces in the project - is this one
		// created before/after our annotation processor runs?
		configCollector.addResourcePattern("META-INF/spring.components");
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
		note("PROCESSING #"+element.getSimpleName());
		try {		
			Map<AnnotationMirror,List<AnnotationMirror>> hints = findCompilationHints(element);
			// hints.keys are the @CompilationHints
			// hints.values are either null (if the hint is on a non annotation type) or
			// the annotation mirror that is meta annotated with the @CompilationHint 
			// (if it is being used as a meta annotation)

			// Example: 
			// Collected hints against Sample2 = {@org.springframework.CompilationHint(member={"foo"})=null}
			// Collected hints against Sample3 = {@org.springframework.CompilationHint(member={"foo"})=@com.example.demo2.Sample2(foo={"x", "y", "z"})}
			
			// HINTS ON IntegrationAutoConfiguration are
			//  {@org.springframework.stereotype.CompilationHint(member={"value"})=
			//   [@org.springframework.boot.context.properties.EnableConfigurationProperties({org.springframework.boot.autoconfigure.integration.IntegrationProperties.class}), 
			//    @org.springframework.context.annotation.Import({org.springframework.boot.context.properties.EnableConfigurationPropertiesImportSelector.class})], 
			//   @org.springframework.stereotype.CompilationHint(member={"value"})=
			//   [@org.springframework.boot.autoconfigure.condition.ConditionalOnClass({org.springframework.integration.config.EnableIntegration.class}), 
			//    @org.springframework.context.annotation.Conditional({org.springframework.boot.autoconfigure.condition.OnClassCondition.class})]}
			
			if (!hints.isEmpty()) {
				for (Map.Entry<AnnotationMirror,List<AnnotationMirror>> hint: hints.entrySet()) {
					List<String> typeStrings = new ArrayList<>();
					typeStrings.add(typeUtils.getType(element.asType()));
					AnnotationMirror compilationHint = hint.getKey();
					List<String> accessibilityNeeded = fetchAccess(compilationHint);
					List<AnnotationMirror> path = hint.getValue();
					
					
					// The last element in the path is the one the CompilationHint is on (so the one being pointed at by any refs in the CH)
					if (path.size()==1) {
						// this means we only care when the type being processed is directly annotated...
						AnnotationMirror lastInPath = ((path==null||path.size()==0)?null:path.get(path.size()-1));
						String s = walk(lastInPath.getAnnotationType().asElement());
						if (has(accessibilityNeeded,"proxy")) {
							// probably a better way to deal with this but where would the knowledge live? Where the call to create the proxy is rather than
							// on the thing that will be proxied, or is it when proxy hints are found in a hierarchy above something you know you need to proxy
							// that 'set' of interfaces - let's see when a dup comes up
							note("+++ Proxy this one? "+s);
							this.configCollector.addDynamicProxy(Collections.singletonList(s));
						}	
						typeStrings.addAll(getStrings(compilationHint,lastInPath));
					}
					Map<String, Object> vs = getAnnotationElementValues(compilationHint);
					List<AnnotationValue> valueFromHint = (List<AnnotationValue>)vs.get("value");
					List<String> classnames = new ArrayList<>();
					if (valueFromHint != null) {
						valueFromHint.stream().map(av -> walk(((DeclaredType)av.getValue()).asElement())).
							collect(Collectors.toCollection(()->classnames));
					}		
					if (!classnames.isEmpty()) {
						note("Pulled these classnames from hint "+compilationHint+" => "+classnames);
						typeStrings.addAll(classnames);
					}
				
					note("["+element.getSimpleName()+"] hints "+hints+" give "+typeStrings);
					for (String t: typeStrings) {
						note("AccessibilityNeeded is "+accessibilityNeeded+" class?"+(accessibilityNeeded==null?"":accessibilityNeeded.getClass().getName()));
						if (has(accessibilityNeeded,"resource")) {
							this.configCollector.addResourcePatternForClass(t);
						}
						boolean c = false;
						boolean m = false;
						boolean f = false;
						/*
						if (accessibilityNeeded.size()>0) {
							System.out.println("><><<> "+accessibilityNeeded.get(0).getClass().getName());
						}
						*/
						if (has(accessibilityNeeded,"reflection:constructors")) { 
							note("reflection:constructors matched");
							c = true;
						}
						note("Adding reflection reference for "+t+"? "+c+" "+m+" "+f);
						if (c || m || f) { 
							this.configCollector.addReflectionReference(t,c,m,f);
						}
					}
				}
			}
			
			List<? extends Element> enclosedElements = element.getEnclosedElements();
			for (Element enclosedElement: enclosedElements) {
				if (enclosedElement.getKind()==ElementKind.CLASS) {
					// TODO testcase!
					processElement(enclosedElement);		
				}
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException(
					"Error processing configuration meta-data on " + element, ex);
		}
	}
	
	private boolean has(List ls, String s2) {
		for (Object s: ls) {
			System.out.println("Comparing "+s.toString()+" with "+s2);
			if (s.toString().equals("\""+s2+"\"")) {
				return true;
			}
		}	
		return false;
	}
	
	/**
	 * Returns a collection of specified accesses for types involved:
	 * reflection:constructors, reflection:fields, reflection:methods, resource
	 */
	private List<String> fetchAccess(AnnotationMirror hint) {
		Map<String,Object> values = getAnnotationElementValues(hint);
		List<String> arrayString = (List<String>)values.get("access");
		return (arrayString == null? Collections.emptyList():arrayString);
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

	private Map<AnnotationMirror, List<AnnotationMirror>> findCompilationHints(Element element) {
		Map<AnnotationMirror, List<AnnotationMirror>> annotationsOnType = new HashMap<>();
		collectHints(element, null, annotationsOnType, new HashSet<>(), new Stack<>());
		if (!annotationsOnType.isEmpty()) {
			// System.out.println("Found @CompilationHint on "+element.toString()+" = "+prettyPrint(annotationsOnType));
			note("processing hint on "+element.toString()+" = "+prettyPrint(annotationsOnType));
		}
		return annotationsOnType;
	}
	
	
	/**
	 * Returns a map from the compilation hint to the annotation they are on (if
	 * used as meta annotation).
	 */
	private void collectHints(Element element, AnnotationMirror mirror, Map<AnnotationMirror, List<AnnotationMirror>> collector, Set<AnnotationMirror> visited, Stack<AnnotationMirror> route) {
		if (mirror !=null && !visited.add(mirror)) {
			return;
		}
		if (element != null) {
			for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
				if (compilationHint().equals(annotation.getAnnotationType().toString())) {
					collector.put(annotation, new ArrayList<>(route));
				} else { // is it meta annotated?
					route.push(annotation);
					collectHints(annotation.getAnnotationType().asElement(), annotation, collector, visited, route);
					route.pop();
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
		// {@org.springframework.stereotype.CompilationHint(members={"value"})=@org.springframework.context.annotation.Conditional({org.springframework.context.annotation.ProfileCondition.class})}
		List<AnnotationValue> memberHints = (List<AnnotationValue>)vs.get("member");
		if (annotationIfHintUsedAsMeta != null && memberHints!=null && !memberHints.isEmpty()) {
			note("+++ Found memberhints of "+memberHints+" looking at "+annotationIfHintUsedAsMeta);
			for (AnnotationValue memberRef: memberHints) {
					Map<String,Object> vs2 = getAnnotationElementValues(annotationIfHintUsedAsMeta);
					//AnnotationValue av = annotationIfHintUsedAsMeta.getElementValues().get(memberRef.getValue());
					 note("element values pulled from that "+vs2);
					List<AnnotationValue> l = (List<AnnotationValue>)vs2.get(memberRef.getValue());
					note("and l is "+l);

					if (l!=null && !l.isEmpty()) {
						AnnotationValue firstElement = l.get(0);
						if (firstElement.getValue() instanceof String) {
							l.stream().map(av -> (String)av.getValue()).collect(Collectors.toCollection(() -> names));
						} else if (firstElement.getValue() instanceof DeclaredType) {
							for (AnnotationValue av: l) {
									DeclaredType dt = (DeclaredType)av.getValue();
									String s = walk(dt.asElement());
									names.add(s);
									/*
									System.out.println("KEK? "+s);
									dt.asElement().getEnclosingElement();
									*/
									/*
									SimpleTypeVisitor tv = new SimpleTypeVisitor();
									dt.accept();

									List<Element> nestedTypes = new ArrayList<>();

									DeclaredType x = dt;
									while (x.getEnclosingType() != null) {
										x = (DeclaredType)x.getEnclosingType().;
										nestedTypes.add(0,x);
									}
									// At this point any nested types are in nestedTypes and we have dt.
									if (nestedTypes.isEmpty()) {
										*/
										//names.add(dt.asElement().toString());
										/*
									} else {
										StringBuilder s= new StringBuilder();
										s.append(nestedTypes.get(0).asElement().toString());
										int i=1;
										for (;i<nestedTypes.size();i++) {
											s.append("$").append(nestedTypes.get(i).asElement().getSimpleName());
										}
										s.append(dt.asElement().getSimpleName());
										names.add(s.toString());
									}
									*/
							}
//							System.out.println("INNERS IN HERE: "+names);
							//l.stream().map(av -> ((DeclaredType)av.getValue()).asElement().toString()).collect(Collectors.toCollection(() -> names));
						}
					}
					note("  collected types: "+names);
			}	
		}
		return names;
	}
	
	//private List<Object> retrieveMetaReferenced(List<String> refs, AnnotationMirror annoThing, String fieldname) {
		//Map<String,Object> vs = getAnnotationElemet
	//}

									/*
>walk org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration.RabbitTemplateConfiguration::CLASS
>walk org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration::CLASS
>walk org.springframework.boot.autoconfigure.amqp::PACKAGE
*/
	private String walk(Element e) {
		//System.out.println(">walk "+e.toString()+"::"+e.getSimpleName()+"::"+e.getKind().toString());
		if (e.getKind().equals(ElementKind.PACKAGE)) {
			return e.toString()+".";
		} else {
			// CLASS
			Element e2 = e.getEnclosingElement();
				String s = walk(e2);
				if (s.endsWith(".")) {
					return s+e.getSimpleName();
				} else {
					return s+"$"+e.getSimpleName();
				}
		}
	}

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

	private void note(String msg) {
		log(Kind.NOTE, msg);
		System.out.println(msg);// because maven isn't outputting annotation processor logging...
	}

	private void logWarning(String msg) {
		log(Kind.WARNING, msg);
	}

	private void log(Kind kind, String msg) {
		this.processingEnv.getMessager().printMessage(kind, msg);
	}


	// Input: {@org.springframework.stereotype.CompilationHint(member={"value"})=[@org.springframework.context.annotation.Import({org.springframework.context.annotation.MBeanExportConfiguration.class})]}
	// Output: @Import>@CompilationHint
	private String prettyPrint(Map<AnnotationMirror, List<AnnotationMirror>> data) {
		StringBuilder sb = new StringBuilder();
		int i=0;
		for (Map.Entry<AnnotationMirror, List<AnnotationMirror>> entry: data.entrySet()) {
			if (i>0) { sb.append(" ");}
			List<AnnotationMirror> annotationPath = entry.getValue();
			if (annotationPath != null) {
				for (AnnotationMirror am: annotationPath) {
					sb.append(prettyPrint(am)).append(">");
				}
			}
			sb.append(prettyPrint(entry.getKey()));
			i++;
		}
		return sb.toString();
	}
	
	private String prettyPrint(AnnotationMirror a) {
		return "@"+a.getAnnotationType().asElement().getSimpleName()+shortValues(a);
	}
	
	private String shortValues(AnnotationMirror a) {
		StringBuilder s = new StringBuilder();
		Map<? extends ExecutableElement, ? extends AnnotationValue> values = a.getElementValues();
		if (values != null && !values.isEmpty()) {
			s.append("(");
			int i=0;
			for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry: values.entrySet()) {
				if (i>0) {
					s.append(",");
				}
				s.append(entry.getKey().getSimpleName()).append("=").append(shortValue(entry.getValue()));
				i++;
			}
			s.append(")");
		}
		return s.toString();
	}

	private String shortValue(AnnotationValue value) {
		StringBuilder s = new StringBuilder();
		Object o = value.getValue();
		if (o instanceof List) {
			List<AnnotationValue> l = (List<AnnotationValue>)o;
			int i=0;
			s.append("{");
			for (AnnotationValue av: l) {
				if (i>0) {
					s.append(",");
				}
				Object o2 = av.getValue();
				if (o2 instanceof DeclaredType) {
					s.append(((DeclaredType)o2).asElement().getSimpleName());
				} else {
					s.append(o2);
				}
				i++;
			}
			s.append("}");
		} else {
			s.append(o);
		}
		return s.toString();
	}
}
