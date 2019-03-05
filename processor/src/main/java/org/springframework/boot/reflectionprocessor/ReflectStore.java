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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.springframework.boot.graal.reflectconfig.JsonMarshaller;
import org.springframework.boot.graal.reflectconfig.ReflectionDescriptor;

/**
 * A {@code ReflectStore} is responsible for the storage of reflection information on the filesystem.
 *
 * @author Andy Clement
 */
public class ReflectStore {

	static final String METADATA_PATH = "META-INF/reflect.json";

	private static final String RESOURCES_FOLDER = "resources";

	private static final String CLASSES_FOLDER = "classes";

	private final ProcessingEnvironment environment;

	public ReflectStore(ProcessingEnvironment environment) {
		this.environment = environment;
	}

	public ReflectionDescriptor readMetadata() {
		try {
			return readMetadata(getMetadataResource().openInputStream());
		}
		catch (IOException ex) {
			return null;
		}
	}

	public void writeMetadata(ReflectionDescriptor metadata) throws IOException {
		if (!metadata.isEmpty()) {
			try (OutputStream outputStream = createMetadataResource().openOutputStream()) {
				new JsonMarshaller().write(metadata, outputStream);
			}
		}
	}

	public static ReflectionDescriptor readMetadata(InputStream in) throws IOException {
		if (in == null) {
			return null;
		}
		try {
			return JsonMarshaller.read(in);
		}
		catch (IOException ex) {
			return null;
		}
		catch (Exception ex) {
			// InvalidConfigurationMetadataException
			throw new RuntimeException(
					"Invalid additional meta-data in '" + METADATA_PATH + "': "
							+ ex.getMessage());//,
//					Diagnostic.Kind.ERROR);
		}
		finally {
			in.close();
		}
	}

	private FileObject getMetadataResource() throws IOException {
		return this.environment.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "",
				METADATA_PATH);
	}

	private FileObject createMetadataResource() throws IOException {
		return this.environment.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
				"", METADATA_PATH);
	}

}
