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

import java.util.HashSet;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RewriteReflectionAdaptor extends ClassVisitor implements Opcodes {

	private static final HashSet<String> intercept = new HashSet<String>();

	public boolean rewroteReflection = false;

	private ClassVisitor cw;

	static {
		intercept("java/lang/reflect/AccessibleObject", "getAnnotation");
		intercept("java/lang/reflect/AccessibleObject", "getAnnotations");
		intercept("java/lang/reflect/AccessibleObject", "getDeclaredAnnotations");
		intercept("java/lang/reflect/AccessibleObject", "isAnnotationPresent");

		intercept("java/lang/reflect/AnnotatedElement", "getAnnotation");
		intercept("java/lang/reflect/AnnotatedElement", "getAnnotations");
		intercept("java/lang/reflect/AnnotatedElement", "getDeclaredAnnotations");
		intercept("java/lang/reflect/AnnotatedElement", "isAnnotationPresent");

		intercept("java/lang/reflect/Method", "getAnnotation");
		intercept("java/lang/reflect/Method", "getAnnotations");
		intercept("java/lang/reflect/Method", "getDeclaredAnnotations");
		intercept("java/lang/reflect/Method", "getParameterAnnotations");
		intercept("java/lang/reflect/Method", "invoke");
		intercept("java/lang/reflect/Method", "isAnnotationPresent");

		intercept("java/lang/reflect/Constructor", "getAnnotation");
		intercept("java/lang/reflect/Constructor", "getAnnotations");
		intercept("java/lang/reflect/Constructor", "getDeclaredAnnotations");
		intercept("java/lang/reflect/Constructor", "getParameterAnnotations");
		intercept("java/lang/reflect/Constructor", "isAnnotationPresent");
		// interceptable("java/lang/reflect/Constructor", "newInstance");

		intercept("java/lang/reflect/Field", "getAnnotation");
		intercept("java/lang/reflect/Field", "getAnnotations");
		intercept("java/lang/reflect/Field", "getDeclaredAnnotations");
		intercept("java/lang/reflect/Field", "isAnnotationPresent");

		intercept("java/lang/reflect/Field", "get");

		intercept("java/lang/reflect/Field", "getBoolean");
		intercept("java/lang/reflect/Field", "getByte");
		intercept("java/lang/reflect/Field", "getShort");
		intercept("java/lang/reflect/Field", "getChar");
		intercept("java/lang/reflect/Field", "getInt");
		intercept("java/lang/reflect/Field", "getLong");
		intercept("java/lang/reflect/Field", "getFloat");
		intercept("java/lang/reflect/Field", "getDouble");
		
		// interceptable("java/lang/reflect/Field", "set");
		// interceptable("java/lang/reflect/Field", "setBoolean");
		// interceptable("java/lang/reflect/Field", "setByte");
		// interceptable("java/lang/reflect/Field", "setChar");
		// interceptable("java/lang/reflect/Field", "setDouble");
		// interceptable("java/lang/reflect/Field", "setFloat");
		// interceptable("java/lang/reflect/Field", "setInt");
		// interceptable("java/lang/reflect/Field", "setLong");
		// interceptable("java/lang/reflect/Field", "setShort");
		
		// interceptable("java/lang/Class", "getAnnotation");
		// interceptable("java/lang/Class", "getAnnotations");
		// interceptable("java/lang/Class", "getField");
		// interceptable("java/lang/Class", "getFields");
		// interceptable("java/lang/Class", "getDeclaredAnnotations");
		// interceptable("java/lang/Class", "getConstructors");
		// interceptable("java/lang/Class", "getConstructor");
		// interceptable("java/lang/Class", "getDeclaredConstructors");
		// interceptable("java/lang/Class", "getDeclaredConstructor");
		// interceptable("java/lang/Class", "getDeclaredField");
		// interceptable("java/lang/Class", "getDeclaredFields");
		// interceptable("java/lang/Class", "getDeclaredMethod");
		// interceptable("java/lang/Class", "getDeclaredMethods");
		// interceptable("java/lang/Class", "getMethod");
		// interceptable("java/lang/Class", "getMethods");
		// interceptable("java/lang/Class", "getModifiers");
		// interceptable("java/lang/Class", "isAnnotationPresent");
		// interceptable("java/lang/Class", "newInstance"); // TODO test
		// interceptable("java/lang/Class", "getEnumConstants");
	}

	// @formatter:on

	/**
	 * Call this method to declare that a certain method is 'interceptable'. An
	 * interceptable method should have a corresponding interceptor method in
	 * {@link RI}. The name and signature of the interceptor will
	 * be derived from the interceptable method.
	 *
	 * For example, java.lang.Class.getMethod(Class[] params) ==>
	 * ReflectiveInterceptor.jlClassGetMethod(Class thiz, Class[] params)
	 *
	 * @param owner      Slashed class name of the declaring type.
	 * @param methodName Name of the interceptable method.
	 */
	private static void intercept(String owner, String methodName) {
		String k = new StringBuilder(owner).append(".").append(methodName).toString();
		if (intercept.contains(k)) {
			throw new IllegalStateException("Attempt to add duplicate entry " + k);
		}
		intercept.add(k);
	}

	private static boolean isInterceptable(String owner, String methodName) {
		return intercept.contains(owner + "." + methodName);
	}

	public RewriteReflectionAdaptor(ClassVisitor classWriter) {
		super(ASM6, classWriter);
		cw = cv;
	}

	public RewriteReflectionAdaptor() {
		this(new ClassWriter(ClassWriter.COMPUTE_MAXS));
	}

	public byte[] getBytes() {
		byte[] bytes = ((ClassWriter) cw).toByteArray();
		return bytes;
	}

	public ClassVisitor getClassVisitor() {
		return cv;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
			final Object value) {
		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int flags, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(flags, name, descriptor, signature, exceptions);
		return new RewritingMethodAdapter(mv, name);
	}

	class RewritingMethodAdapter extends MethodVisitor implements Opcodes {

		public RewritingMethodAdapter(MethodVisitor mv, String methodname) {
			super(ASM6, mv);
		}

		private boolean interceptReflection(String owner, String name, String desc) {
			if (isInterceptable(owner, name)) {
				callReflectiveInterceptor(owner, name, desc, mv);
				return true;
			}
			return false;
		}

		@Override
		public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc,
				final boolean itf) {
			if (!rewriteReflectiveCall(opcode, owner, name, desc)) {
				super.visitMethodInsn(opcode, owner, name, desc, itf);
			}
		}

		/**
		 * Determine if a method call is a reflective call and an attempt should be made
		 * to rewrite it.
		 *
		 * @return true if the call was rewritten
		 */
		private boolean rewriteReflectiveCall(int opcode, String owner, String name, String desc) {
			if (owner.length() > 10 && owner.charAt(0) == 'j'
					&& (owner.startsWith("java/lang/reflect/") || owner.equals("java/lang/Class"))) {
				boolean rewritten = interceptReflection(owner, name, desc);
				if (rewritten) {
					return true;
				}
			}
			return false;
		}

		private void callReflectiveInterceptor(String owner, String name, String desc, MethodVisitor mv) {
			StringBuilder methodName = new StringBuilder();
			methodName.append(owner.charAt(0));
			int stop = owner.lastIndexOf("/");
			int index = owner.indexOf("/");
			while (index < stop) {
				methodName.append(owner.charAt(index + 1));
				index = owner.indexOf("/", index + 1);
			}
			methodName.append(owner, stop + 1, owner.length());
			methodName.append(Character.toUpperCase(name.charAt(0)));
			methodName.append(name, 1, name.length());
			StringBuilder newDescriptor = new StringBuilder("(L").append(owner).append(";").append(desc, 1,
					desc.length());
			mv.visitMethodInsn(INVOKESTATIC, "org/springframework/boot/agent/reflectionrecorder/RI",
					methodName.toString(), newDescriptor.toString(), false);
			rewroteReflection = true;
		}
	}
}
