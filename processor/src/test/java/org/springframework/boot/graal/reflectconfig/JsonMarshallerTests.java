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

package org.springframework.boot.graal.reflectconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.springframework.boot.graal.reflectconfig.ClassDescriptor.Flag;

/**
 * Tests for {@link JsonMarshaller}.
 *
 * @author Andy Clement
 */
public class JsonMarshallerTests {

	@Test
	public void marshallAndUnmarshal() throws Exception {
		ReflectionDescriptor metadata = new ReflectionDescriptor();
		ClassDescriptor cd = new ClassDescriptor();
		cd.setFlag(Flag.allDeclaredConstructors);
		cd.setName("java.lang.String");
		cd.addFieldDescriptor(new FieldDescriptor("length",true));
		List<String> parameterTypes =new ArrayList<>();
		parameterTypes.add("java.util.List");
		parameterTypes.add("java.net.URL");
		cd.addMethodDescriptor(new MethodDescriptor("foo",parameterTypes));
		metadata.add(cd);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		JsonMarshaller marshaller = new JsonMarshaller();
		marshaller.write(metadata, outputStream);
		byte[] bs = outputStream.toByteArray();
		System.out.println(new String(bs));
		ReflectionDescriptor read = marshaller
				.read(new ByteArrayInputStream(outputStream.toByteArray()));
		List<ClassDescriptor> cds = read.getClassDescriptors();
		assertEquals(1,cds.size());
		ClassDescriptor cd1 = cds.get(0);
		assertEquals("java.lang.String",cd1.getName());
		List<FieldDescriptor> fds = cd1.getFields();
		assertEquals(1,fds.size());
		FieldDescriptor fd1 = fds.get(0);
		assertEquals("length",fd1.getName());
		assertEquals(true, fd1.isAllowWrite());
		List<MethodDescriptor> mds = cd1.getMethods();
		assertEquals(1,mds.size());
		MethodDescriptor md1 = mds.get(0);
		assertEquals("foo",md1.getName());
		List<String> parameterTypes2 = md1.getParameterTypes();
		assertEquals(2, parameterTypes2.size());
		assertEquals("java.util.List",parameterTypes2.get(0));
		assertEquals("java.net.URL",parameterTypes2.get(1));
		assertTrue(cd1.getFlags().contains(Flag.allDeclaredConstructors));
	}

}
