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

import java.io.File;
import java.io.FileOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.graal.reflectconfig.ClassDescriptor;
import org.springframework.boot.graal.reflectconfig.ClassDescriptor.Flag;
import org.springframework.boot.graal.reflectconfig.JsonMarshaller;
import org.springframework.boot.graal.reflectconfig.ReflectionDescriptor;

/**
 * When reflection is rewritten, the new calls invoke methods in this type.
 *
 * @author Andy Clement
 * @author Kris De Volder
 */
public class RI {

	private static Map<ReflectiveCall, List<Info>> reflectionInvokers = new ConcurrentHashMap<>();

	public static Map<String, Integer> reflectedClasses = new HashMap<>();

	static Thread activityThread;

	static InactivityDumper id;

	static List<String> cglibClasses = new ArrayList<>();

	static List<String> classes = new ArrayList<>();

	static {
		id = new InactivityDumper();
		activityThread = new Thread(id);
		activityThread.start();
	}

	static class InactivityDumper implements Runnable {

		long endTime;

		InactivityDumper() {
			endTime = System.currentTimeMillis() + 5000;
		}

		@Override
		public void run() {
			while (true) {
				long currenttime = System.currentTimeMillis();
				if (currenttime > endTime) {
					RI.dumpData();
					if (Configuration.exit) {
						System.exit(0);
					}
					break;
				}
				System.out.println("Time until dump... " + (endTime - currenttime));
				try {
					Thread.sleep(500);
				} catch (Exception e) {
				}
			}
		}

		public void ping() {
			endTime = System.currentTimeMillis() + 5000;
		}

	}

	enum ReflectiveCall {
		METHOD_GETANNOTATIONS, //
		CLASS_GETDECLAREDCONSTRUCTOR, AO_GETANNOTATION, CLASS_GETFIELD, CLASS_GETDECLAREDFIELD, CLASS_GETDECLAREDFIELDS, //
		CLASS_GETFIELDS, FIELD_GET, FIELD_GETINT, CLASS_GETDECLAREDMETHOD, CLASS_GETMETHOD, CLASS_GETDECLAREDETHODS,
		CLASS_GETMETHODS, //
		CLASS_GETDECLAREDANNOTATIONS, CLASS_GETANNOTATIONS, CLASS_GETANNOTATION, CLASS_GETMODIFIERS,
		METHOD_GETDECLAREDANNOTATIONS, //
		METHOD_GETPARAMETERANNOTATIONS, CLASS_ISANNOTATIONPRESENT, CLASS_GETDECLAREDCONSTRUCTORS, CLASS_GETCONSTRUCTORS,
		CLASS_GETCONSTRUCTOR, //
		METHOD_INVOKE, METHOD_ISANNOTATIONPRESENT, METHOD_GETANNOTATION, CONSTRUCTOR_GETANNOTATION, AE_GETANNOTATIONS, //
		AE_GETDECLAREDANNOTATIONS, AO_GETDECLAREDANNOTATIONS, //
		FIELD_GETDECLAREDANNOTATIONS, FIELD_GETCHAR, FIELD_GETBOOLEAN, FIELD_GETBYTE, FIELD_GETFLOAT, FIELD_GETLONG,
		FIELD_GETSHORT, FIELD_GETDOUBLE, FIELD_ISANNOTATIONPRESENT, //
		CONSTRUCTOR_GETDECLAREDANNOTATIONS, CONSTRUCTOR_NEWINSTANCE, FIELD_GETANNOTATION,
		CONSTRUCTOR_ISANNOTATIONPRESENT, FIELD_SETBYTE, FIELD_SETCHAR, CLASS_NEWINSTANCE,
	}

	public static void dumpData() {
		System.out.println("Reflected types: #" + reflectedClasses.size());
		ReflectionDescriptor rd = new ReflectionDescriptor();

		for (Map.Entry<String, Integer> e : reflectedClasses.entrySet()) {
			System.out.println("ReflectedType(Occurrences #" + e.getValue() + "): " + e.getKey());
			if (!e.getKey().contains("CGLIB") && !e.getKey().contains("$$Lambda")) {
				ClassDescriptor cd = ClassDescriptor.of(e.getKey());
				cd.setFlag(Flag.allDeclaredConstructors);
				cd.setFlag(Flag.allDeclaredMethods);
				rd.add(cd);
			}
		}
		
		try (FileOutputStream fos = new FileOutputStream(
				new File(Configuration.reflectFile == null ? "reflect.json" : Configuration.reflectFile))) {
			new JsonMarshaller().write(rd, fos);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (Configuration.reflectionSummary) {
			System.out.println("Reflection Summary");
			// What objects are being reflected on? Who is doing the reflection?
			int reflectiveCallCount = 0;
			Sortable<String> whoIsMakingTheCalls = new Sortable<>();
			for (Map.Entry<ReflectiveCall, List<Info>> entry : reflectionInvokers.entrySet()) {
				reflectiveCallCount += entry.getValue().size();
				for (Info info : entry.getValue()) {
					String source = info.callingClass + "." + info.callingMethod;
					whoIsMakingTheCalls.add(source);
				}
			}
			System.out.println("Number of reflective calls: #" + reflectiveCallCount);
			System.out.println("Top 20 sources of reflection: ");
			Map<String, Integer> sortedElements = whoIsMakingTheCalls.getSortedElements();
			sortedElements.keySet().stream().limit(20).forEach(k -> {
				System.out.println(k + " #" + sortedElements.get(k));
			});
		}
	}
	
	static class Sortable<T> {
		List<Thing> things = new ArrayList<>();
		class Thing {
			private T o;
			private int count;
			Thing(T o) {
				this.o = o;
				this.count = 1;
			}
			@Override
			public String toString() {
				return "("+o+":"+count+")";
			}
			public void incCount() {
				count++;
			}
			public boolean sameThing(T o2) {
				return o.equals(o2);
			}
		}
		public void add(T o) {
			boolean found = false;
			for (Thing thing: things) {
				if (thing.sameThing(o)) {
					// Already in there, inc count
					thing.incCount();
					found = true;
					break;
				}
			}
			if (!found) {
				things.add(new Thing(o));
			}
		}
		public Map<T,Integer> getSortedElements() {
			Collections.sort(things, (a,b) -> {
				return b.count - a.count;
			});
			Map<T,Integer> result = new LinkedHashMap<>();
			for (Thing thing: things) {
				result.put(thing.o, thing.count);
			}
			return result;
		}
	}

//	private static void tellMeAboutTarget(String string) {
//		for (Map.Entry<ReflectiveCall, List<Info>> entry : x.entrySet()) {
//			for (Info info : entry.getValue()) {
//				String source = info.callingClass + "." + info.callingMethod;
//				if (info.os != null && info.os.length > 0 && info.os[0] instanceof Class
//						&& ((Class) info.os[0]).getName().equals(string)) {
//					System.out.println(entry.getKey() + " " + info);
//				}
//			}
//		}
//	}
//
//	private static void tellMeAboutCaller(String string, String method) {
//		System.out.println("Reporting on caller " + string + "." + method);
//		for (Map.Entry<ReflectiveCall, List<Info>> entry : x.entrySet()) {
//			for (Info info : entry.getValue()) {
//				String source = info.callingClass + "." + info.callingMethod;
//				if (info.callingClass.equals(string) && (info.callingMethod.startsWith(method) || method == null)) {
//					System.out.println(entry.getKey() + " " + info);
//				}
//			}
//		}
//	}

	@SuppressWarnings("rawtypes")
	private static void record(ReflectiveCall type, Object... objs) {
		List<Info> existing = reflectionInvokers.get(type);
		try {
			Class c = null;
			if (objs[0] instanceof Method) {
				c = ((Method) objs[0]).getDeclaringClass();
			} else if (objs[0] instanceof Field) {
				c = ((Field) objs[0]).getDeclaringClass();
			} else if (objs[0] instanceof Constructor) {
				c = ((Constructor) objs[0]).getDeclaringClass();
			} else if (objs[0] instanceof Annotation) {
				c = ((Annotation) objs[0]).annotationType();
//			} else if (objs[0] instanceof AnnotatedElement) {
//				c = ((AnnotatedElement) objs[0]).getClass();// annotationType();
			} else {
				c = (Class) objs[0];
			}
			if (Configuration.whyType != null && (Configuration.whyType.equals("*") || Configuration.whyType.equals(c.getName()))) {
				StackTraceElement[] stes = Thread.currentThread().getStackTrace();
				StringBuilder s = new StringBuilder();
				s.append("================================\n");
				s.append("This stack is why type " + c + " is recorded:\n");
				s.append("Recording event "+type+": supplied parameters: "+Arrays.toString(objs)+"\n");
				for (int i=1;i<stes.length;i++) { // Skip 0 which is java.lang.Thread.getStackTrace()
					StackTraceElement ste = stes[i];
					if (ste.getClassName().contains("org.springframework.boot.agent.reflectionrecorder")) {
						if (Configuration.dontHideInfra) {
							s.append(ste+"\n");
						} else {
							s.append(".");
						}
					} else {
						s.append(ste+"\n");
					}
				}
				s.append("================================\n");
				System.out.println(s.toString());
			}
			String n = c.getName();
			Integer i = reflectedClasses.get(n);
			if (i == null) {
				reflectedClasses.put(n, 1);
			} else {
				reflectedClasses.put(n, i + 1);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		int traceLevel = 3;
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		StackTraceElement ste = stackTrace[traceLevel];
		while (ste.getClassName().contains("AnnotatedElementUtils")) {
			ste = stackTrace[++traceLevel];
		}
		String clazzName = ste.getClassName();
		if (clazzName.equals("org.springframework.boot.agent.reflectionrecorder.RI")) {
			return;
		}
		String methodName = ste.getMethodName() + ":" + ste.getLineNumber();
		if (existing == null) {
			existing = new ArrayList<>();
			reflectionInvokers.put(type, existing);
		}
		existing.add(new Info(objs, clazzName, methodName));
		id.ping();
	}

	// ---

	public static Method jlClassGetDeclaredMethod(Class<?> clazz, String name, Class<?>... params)
			throws SecurityException, NoSuchMethodException {
		record(ReflectiveCall.CLASS_GETDECLAREDMETHOD, clazz, name, params);
		return clazz.getDeclaredMethod(name, params);
	}

	public static Method jlClassGetMethod(Class<?> clazz, String name, Class<?>... params)
			throws SecurityException, NoSuchMethodException {
		record(ReflectiveCall.CLASS_GETMETHOD, clazz, name, params);
		return clazz.getDeclaredMethod(name, params);
	}

	public static Method[] jlClassGetDeclaredMethods(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETDECLAREDETHODS, clazz);
		return clazz.getDeclaredMethods();
	}

	public static Method[] jlClassGetMethods(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETMETHODS, clazz);
		return clazz.getMethods();
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	static String toParamString(Class<?>[] params) {
		if (params == null || params.length == 0) {
			return "()";
		}
		StringBuilder s = new StringBuilder();
		s.append('(');
		for (int i = 0, max = params.length; i < max; i++) {
			if (i > 0) {
				s.append(", ");
			}
			if (params[i] == null) {
				s.append("null");
			} else {
				s.append(params[i].getName());
			}
		}
		s.append(')');
		return s.toString();
	}

	private static int depth = 4;

	/*
	 * Get the Class that declares the method calling interceptor method that called
	 * this method.
	 */
	@SuppressWarnings({ "deprecation", "restriction" })
	public static Class<?> getCallerClass() {
		// TODO not sure about this right now, needs reviewing
		// 0 = sun.reflect.Reflection.getCallerClass
		// 1 = this method's frame
		// 2 = caller of 'getCallerClass' = asAccessibleMethod
		// 3 = caller of 'asAccessibleMethod' = jlrInvoke
		// 4 = caller we are interested in...

		// In jdk17u25 there is an extra frame inserted:
		// "This also fixes a regression introduced in 7u25 in which
		// getCallerClass(int) is now a Java method that adds an additional frame
		// that wasn't taken into account." in
		// http://permalink.gmane.org/gmane.comp.java.openjdk.jdk7u.devel/6573
		Class<?> caller = sun.reflect.Reflection.getCallerClass(depth);
		if (caller == RI.class) {
			// If this is true we have that extra frame on the stack
			depth = 5;
			caller = sun.reflect.Reflection.getCallerClass(depth);
		}

		return caller;
	}

	public static Annotation[] jlClassGetDeclaredAnnotations(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETDECLAREDANNOTATIONS, clazz);
		return clazz.getDeclaredAnnotations();
	}

	public static Annotation[] jlClassGetAnnotations(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETANNOTATIONS, clazz);
		return clazz.getAnnotations();
	}

	public static Annotation jlClassGetAnnotation(Class<?> clazz, Class<? extends Annotation> annoType) {
		record(ReflectiveCall.CLASS_GETANNOTATION, clazz, annoType);
		return clazz.getAnnotation(annoType);
	}

	public static boolean jlClassIsAnnotationPresent(Class<?> clazz, Class<? extends Annotation> annoType) {
		record(ReflectiveCall.CLASS_ISANNOTATIONPRESENT, clazz, annoType);
		return clazz.isAnnotationPresent(annoType);
	}

	public static Constructor<?>[] jlClassGetDeclaredConstructors(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETDECLAREDCONSTRUCTORS, clazz);
		return clazz.getDeclaredConstructors();
	}

	public static Constructor<?>[] jlClassGetConstructors(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETCONSTRUCTORS, clazz);
		return clazz.getConstructors();
	}

	public static Constructor<?> jlClassGetDeclaredConstructor(Class<?> clazz, Class<?>... params)
			throws SecurityException, NoSuchMethodException {
		record(ReflectiveCall.CLASS_GETDECLAREDCONSTRUCTOR, clazz, params);
		return clazz.getDeclaredConstructor(params);
	}

	public static Constructor<?> jlClassGetConstructor(Class<?> clazz, Class<?>... params)
			throws SecurityException, NoSuchMethodException {
		record(ReflectiveCall.CLASS_GETCONSTRUCTOR, clazz, params);
		return clazz.getConstructor(params);
	}

	public static int jlClassGetModifiers(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETMODIFIERS, clazz);
		return clazz.getModifiers();
	}

	public static Annotation[] jlrMethodGetDeclaredAnnotations(Method method) {
		record(ReflectiveCall.METHOD_GETDECLAREDANNOTATIONS, method);
		return method.getDeclaredAnnotations();
	}

	public static Annotation[][] jlrMethodGetParameterAnnotations(Method method) {
		record(ReflectiveCall.METHOD_GETPARAMETERANNOTATIONS, method);
		return method.getParameterAnnotations();
	}

	public static Object jlClassNewInstance(Class<?> clazz) throws SecurityException, NoSuchMethodException,
			IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {

		// TODO: This implementation doesn't check access modifiers on the class. So may
		// allow
		// instantiations that wouldn't be allowed by the JVM (e.g if constructor is
		// public, but class is private)

		// TODO: what about trying to instantiate an abstract class? should produce an
		// error, does it?

		Constructor<?> c;
		try {
			c = jlClassGetDeclaredConstructor(clazz);
		} catch (NoSuchMethodException e) {
			throw new InstantiationException(clazz.getName());
		}
		c = asAccessibleConstructor(c, true);
		record(ReflectiveCall.CLASS_NEWINSTANCE, clazz);
		return jlrConstructorNewInstance(c);
	}

	public static Object jlrConstructorNewInstance(Constructor<?> c, Object... params)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			SecurityException, NoSuchMethodException {
		record(ReflectiveCall.CONSTRUCTOR_NEWINSTANCE, c);
		c = asAccessibleConstructor(c, true);
		return c.newInstance(params);
	}

	// private static String toString(Object... params) {
	// if (params == null) {
	// return "null";
	// }
	// StringBuilder s = new StringBuilder();
	// for (Object param : params) {
	// s.append(param).append(" ");
	// }
	// return "[" + s.toString().trim() + "]";
	// }

	public static Object jlrMethodInvoke(Method method, Object target, Object... params)
			throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		record(ReflectiveCall.METHOD_INVOKE, method, target, params);
		return method.invoke(target, params);
	}

	public static boolean jlrMethodIsAnnotationPresent(Method method, Class<? extends Annotation> annotClass) {
		record(ReflectiveCall.METHOD_ISANNOTATIONPRESENT, method, annotClass);
		return method.isAnnotationPresent(annotClass);
	}

	public static Annotation jlrMethodGetAnnotation(Method method, Class<? extends Annotation> annotClass) {
		record(ReflectiveCall.METHOD_GETANNOTATION, method, annotClass);
		return method.getAnnotation(annotClass);
	}

	public static Annotation[] jlrAnnotatedElementGetAnnotations(AnnotatedElement elem) {
		record(ReflectiveCall.AE_GETANNOTATIONS, elem);
		return elem.getAnnotations();
	}

	public static Annotation[] jlrAnnotatedElementGetDeclaredAnnotations(AnnotatedElement elem) {
		record(ReflectiveCall.AE_GETDECLAREDANNOTATIONS, elem);
		return elem.getDeclaredAnnotations();
	}

	public static Annotation[] jlrAccessibleObjectGetDeclaredAnnotations(AccessibleObject obj) {
		record(ReflectiveCall.AO_GETDECLAREDANNOTATIONS, obj);
		return obj.getDeclaredAnnotations();
	}

	public static Annotation[] jlrFieldGetDeclaredAnnotations(Field field) {
		record(ReflectiveCall.FIELD_GETDECLAREDANNOTATIONS, field);
		return field.getDeclaredAnnotations();
	}

	public static boolean jlrFieldIsAnnotationPresent(Field field, Class<? extends Annotation> annotType) {
		record(ReflectiveCall.FIELD_ISANNOTATIONPRESENT, field, annotType);
		return field.isAnnotationPresent(annotType);
	}

	public static Annotation[] jlrFieldGetAnnotations(Field field) {
		// Fields do not inherit annotations so we can just call...
		return jlrFieldGetDeclaredAnnotations(field);
	}

	public static Annotation[] jlrAccessibleObjectGetAnnotations(AccessibleObject obj) {
		if (obj instanceof Method) {
			return jlrMethodGetAnnotations((Method) obj);
		} else if (obj instanceof Field) {
			return jlrFieldGetAnnotations((Field) obj);
		} else if (obj instanceof Constructor<?>) {
			return jlrConstructorGetAnnotations((Constructor<?>) obj);
		} else {
			// Some other type of member which we don't support reloading...
			// (actually there are really no other cases any more!)
			return obj.getAnnotations();
		}
	}

	public static Annotation[] jlrConstructorGetAnnotations(Constructor<?> c) {
		return jlrConstructorGetDeclaredAnnotations(c);
	}

	public static Annotation[] jlrConstructorGetDeclaredAnnotations(Constructor<?> c) {
		record(ReflectiveCall.CONSTRUCTOR_GETDECLAREDANNOTATIONS, c);
		return c.getDeclaredAnnotations();
	}

	public static Annotation jlrConstructorGetAnnotation(Constructor<?> c, Class<? extends Annotation> annotType) {
		record(ReflectiveCall.CONSTRUCTOR_GETANNOTATION, c, annotType);
		return c.getAnnotation(annotType);
	}

	public static boolean jlrConstructorIsAnnotationPresent(Constructor<?> c, Class<? extends Annotation> annotType) {
		record(ReflectiveCall.CONSTRUCTOR_ISANNOTATIONPRESENT, c, annotType);
		return c.isAnnotationPresent(annotType);
	}

	public static Annotation jlrFieldGetAnnotation(Field field, Class<? extends Annotation> annotType) {
		record(ReflectiveCall.FIELD_GETANNOTATION, field, annotType);
		return field.getAnnotation(annotType);
	}

	public static Annotation[] jlrMethodGetAnnotations(Method method) {
		record(ReflectiveCall.METHOD_GETANNOTATIONS, method);
		return method.getAnnotations();
	}

	public static boolean jlrAnnotatedElementIsAnnotationPresent(AnnotatedElement elem,
			Class<? extends Annotation> annotType) {
		if (elem instanceof Class<?>) {
			return jlClassIsAnnotationPresent((Class<?>) elem, annotType);
		} else if (elem instanceof AccessibleObject) {
			return jlrAccessibleObjectIsAnnotationPresent((AccessibleObject) elem, annotType);
		} else {
			// Don't know what it is... not something we handle anyway
			return elem.isAnnotationPresent(annotType);
		}
	}

	public static boolean jlrAccessibleObjectIsAnnotationPresent(AccessibleObject obj,
			Class<? extends Annotation> annotType) {
		if (obj instanceof Method) {
			return jlrMethodIsAnnotationPresent((Method) obj, annotType);
		} else if (obj instanceof Field) {
			return jlrFieldIsAnnotationPresent((Field) obj, annotType);
		} else if (obj instanceof Constructor) {
			return jlrConstructorIsAnnotationPresent((Constructor<?>) obj, annotType);
		} else {
			// Some other type of member which we don't support reloading...
			return obj.isAnnotationPresent(annotType);
		}
	}

	public static Annotation jlrAnnotatedElementGetAnnotation(AnnotatedElement elem,
			Class<? extends Annotation> annotType) {
		if (elem instanceof Class<?>) {
			return jlClassGetAnnotation((Class<?>) elem, annotType);
		} else if (elem instanceof AccessibleObject) {
			return jlrAccessibleObjectGetAnnotation((AccessibleObject) elem, annotType);
		} else {
			// Don't know what it is... not something we handle anyway
			// Note: only thing it can be is probably java.lang.Package
			return elem.getAnnotation(annotType);
		}
	}

	public static Annotation jlrAccessibleObjectGetAnnotation(AccessibleObject obj,
			Class<? extends Annotation> annotType) {
		record(ReflectiveCall.AO_GETANNOTATION, obj, annotType);
		return obj.getAnnotation(annotType);
	}

	public static Field jlClassGetField(Class<?> clazz, String name) throws SecurityException, NoSuchFieldException {
		record(ReflectiveCall.CLASS_GETFIELD, clazz, name);
		return clazz.getField(name);
	}

	public static Field jlClassGetDeclaredField(Class<?> clazz, String name)
			throws SecurityException, NoSuchFieldException {
		record(ReflectiveCall.CLASS_GETDECLAREDFIELD, clazz, name);
		return clazz.getDeclaredField(name);
	}

	public static Field[] jlClassGetDeclaredFields(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETDECLAREDFIELDS, clazz);
		return clazz.getDeclaredFields();
	}

	public static Field[] jlClassGetFields(Class<?> clazz) {
		record(ReflectiveCall.CLASS_GETFIELDS, clazz);
		return clazz.getFields();
	}

	public static Object jlrFieldGet(Field field, Object target)
			throws IllegalArgumentException, IllegalAccessException {
		record(ReflectiveCall.FIELD_GET, field, target);
		field = asAccessibleField(field, target, true);
		return field.get(target);
	}

	public static int jlrFieldGetInt(Field field, Object target) throws IllegalAccessException {
		record(ReflectiveCall.FIELD_GETINT, field, target);
		return field.getInt(target);
	}

	public static byte jlrFieldGetByte(Field field, Object target) throws IllegalAccessException {
		record(ReflectiveCall.FIELD_GETBYTE, field, target);
		field = asAccessibleField(field, target, true);
		return field.getByte(target);
	}

	public static char jlrFieldGetChar(Field field, Object target) throws IllegalAccessException {
		record(ReflectiveCall.FIELD_GETCHAR, field, target);
		field = asAccessibleField(field, target, true);
		return field.getChar(target);
	}

	public static short jlrFieldGetShort(Field field, Object target) throws IllegalAccessException {
		record(ReflectiveCall.FIELD_GETSHORT, field, target);
		field = asAccessibleField(field, target, true);
		return field.getShort(target);
	}

	public static double jlrFieldGetDouble(Field field, Object target) throws IllegalAccessException {
		record(ReflectiveCall.FIELD_GETDOUBLE, field, target);
		field = asAccessibleField(field, target, true);
		return field.getDouble(target);
	}

	public static float jlrFieldGetFloat(Field field, Object target) throws IllegalAccessException {
		record(ReflectiveCall.FIELD_GETFLOAT, field, target);
		field = asAccessibleField(field, target, true);
		return field.getFloat(target);
	}

	public static boolean jlrFieldGetBoolean(Field field, Object target) throws IllegalAccessException {
		record(ReflectiveCall.FIELD_GETBOOLEAN, field, target);
		field = asAccessibleField(field, target, true);
		return field.getBoolean(target);
	}

	public static long jlrFieldGetLong(Field field, Object target) throws IllegalAccessException {
		record(ReflectiveCall.FIELD_GETLONG, field, target);
		field = asAccessibleField(field, target, true);
		return field.getLong(target);
	}

//	public static void jlrFieldSet(Field field, Object target, Object value) throws IllegalAccessException {
//			field = asSetableField(field, target, valueType(value), value, true);
//			field.set(target, value);
//	}
//
//	public static void jlrFieldSetInt(Field field, Object target, int value) throws IllegalAccessException {
//			field = asSetableField(field, target, int.class, value, true);
//			field.setInt(target, value);
//	}
//
//	public static void jlrFieldSetByte(Field field, Object target, byte value) throws IllegalAccessException {
//		record(ReflectiveCall.FIELD_SETBYTE, field, target, value);
//		field = asSetableField(field, target, byte.class, value, true);
//		field.setByte(target, value);
//	}
//
//	public static void jlrFieldSetChar(Field field, Object target, char value) throws IllegalAccessException {
//		record(ReflectiveCall.FIELD_SETCHAR, field, target, value);
//		field = asSetableField(field, target, char.class, value, true);
//		field.setChar(target, value);
//	}
//
//	public static void jlrFieldSetShort(Field field, Object target, short value) throws IllegalAccessException {
//			field = asSetableField(field, target, short.class, value, true);
//			field.setShort(target, value);
//	}
//
//	public static void jlrFieldSetDouble(Field field, Object target, double value) throws IllegalAccessException {
//			field = asSetableField(field, target, double.class, value, true);
//			field.setDouble(target, value);
//	}
//
//	public static void jlrFieldSetFloat(Field field, Object target, float value) throws IllegalAccessException {
//			field = asSetableField(field, target, float.class, value, true);
//			field.setFloat(target, value);
//	}
//
//	public static void jlrFieldSetLong(Field field, Object target, long value) throws IllegalAccessException {
//			field = asSetableField(field, target, long.class, value, true);
//			field.setLong(target, value);
//	}
//
//	public static void jlrFieldSetBoolean(Field field, Object target, boolean value) throws IllegalAccessException {
//			field = asSetableField(field, target, boolean.class, value, true);
//			field.setBoolean(target, value);
//	}

//	private static Method asAccessibleMethod(ReloadableType methodDeclaringTypeReloadableType, Method method,
//			Object target,
//			boolean makeAccessibleCopy) throws IllegalAccessException {
//		if (method.isAccessible()) {
//			//More expensive check not required / copy not required
//		}
//		else {
//			Class<?> clazz = method.getDeclaringClass();
//			int mods = method.getModifiers();
//			int classmods;
//			classmods = clazz.getModifiers();
//			if (Modifier.isPublic(mods & classmods/*jlClassGetModifiers(clazz)*/)) {
//				//More expensive check not required / copy not required
//			}
//			else {
//				//More expensive check required
//				Class<?> callerClass = getCallerClass();
//				JVM.ensureMemberAccess(callerClass, clazz, target, mods);
//				if (makeAccessibleCopy) {
//					method = JVM.copyMethod(method); // copy: we must not change accessible flag on original method!
//					method.setAccessible(true);
//				}
//			}
//		}
//		return makeAccessibleCopy ? method : null;
//	}

	private static Constructor<?> asAccessibleConstructor(Constructor<?> c, boolean makeAccessibleCopy)
			throws NoSuchMethodException, IllegalAccessException {
		Class<?> clazz = c.getDeclaringClass();
		int mods = c.getModifiers();
		if (c.isAccessible() || Modifier.isPublic(mods & jlClassGetModifiers(clazz))) {
			// More expensive check not required / copy not required
		} else {
			// More expensive check required
			Class<?> callerClass = getCallerClass();
			JVM.ensureMemberAccess(callerClass, clazz, null, mods);
			if (makeAccessibleCopy) {
				c = JVM.copyConstructor(c); // copy: we must not change accessible flag on original method!
				c.setAccessible(true);
			}
		}
		return makeAccessibleCopy ? c : null;
	}

	/**
	 * Performs access checks and returns a (potential) copy of the field with
	 * accessibility flag set if this necessary for the acces operation to succeed.
	 * <p>
	 * If any checks fail, an appropriate exception is raised.
	 *
	 * Warning this method is sensitive to stack depth! Should expect to be called
	 * DIRECTLY from a jlr redirection method only!
	 */
	private static Field asAccessibleField(Field field, Object target, boolean makeAccessibleCopy)
			throws IllegalAccessException {
		Class<?> clazz = field.getDeclaringClass();
		int mods = field.getModifiers();
		if (field.isAccessible() || Modifier.isPublic(mods & jlClassGetModifiers(clazz))) {
			// More expensive check not required / copy not required
		} else {
			// More expensive check required
			Class<?> callerClass = getCallerClass();
			JVM.ensureMemberAccess(callerClass, clazz, target, mods);
			if (makeAccessibleCopy) {
				field = JVM.copyField(field); // copy: we must not change accessible flag on original method!
				field.setAccessible(true);
			}
		}
		return makeAccessibleCopy ? field : null;
	}
//
//	private static Field asSetableField(Field field, Object target, Class<?> valueType, Object value,
//			boolean makeAccessibleCopy)
//			throws IllegalAccessException {
//		// Must do the checks exactly in the same order as JVM if we want identical error messages.
//
//		Class<?> clazz = field.getDeclaringClass();
//		int mods = field.getModifiers();
//		if (field.isAccessible() || Modifier.isPublic(mods & jlClassGetModifiers(clazz))) {
//			//More expensive check not required / copy not required
//		}
//		else {
//			//More expensive check required
//			Class<?> callerClass = getCallerClass();
//			JVM.ensureMemberAccess(callerClass, clazz, target, mods);
//			if (makeAccessibleCopy) {
//				field = JVM.copyField(field); // copy: we must not change accessible flag on original field!
//				field.setAccessible(true);
//			}
//		}
//		if (isPrimitive(valueType)) {
//			//It seems for primitive types, the order of the checks (in Sun JVM) is different!
//			typeCheckFieldSet(field, valueType, value);
//			if (!field.isAccessible() && Modifier.isFinal(mods)) {
//				throw Exceptions.illegalSetFinalFieldException(field, field.getType(), coerce(value, field.getType()));
//			}
//		}
//		else {
//			if (!field.isAccessible() && Modifier.isFinal(mods)) {
//				throw Exceptions.illegalSetFinalFieldException(field, valueType, value);
//			}
//			typeCheckFieldSet(field, valueType, value);
//		}
//		return makeAccessibleCopy ? field : null;
//	}

	public static void recordCglibLoad(String slashedClassName) {
		cglibClasses.add(slashedClassName);
	}

	public static void recordLoad(String slashedClassName) {
		classes.add(slashedClassName);
	}

	static class Info {

		public Info(Object[] objs, String object, String object2) {
			this.os = objs;
			this.callingClass = object;
			this.callingMethod = object2;
		}

		Object[] os;

		String callingClass;

		String callingMethod;

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(callingClass).append(".").append(callingMethod).append(": ");
			for (Object o : os) {
				sb.append(o).append(" ");
			}
			return sb.toString().trim();
		}
	}

}
