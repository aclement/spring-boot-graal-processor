#spring-boot-graal-processor

Annotation processor that produces a JSON file detailing types needing reflective access that can be passed to a graal native image build step.

### Operation

The processor looks at the current app to discern new types that may need reflective access and merges that information with data from a `reflect.defaults.json` resource file that covers the common cases, producing a `META-INF\reflects.json` file in the project.


### Maven snippet

The processor likes to know the project classpath. Until I learn how else to do it that is achieved by an extra bit of maven voodoo. Here is the snippet to include the processor:

```maven
<plugins>
	<plugin>
		<artifactId>maven-dependency-plugin</artifactId>
		<version>2.8</version>
		<executions>
			<execution>
				<phase>generate-sources</phase>
				<goals>
					<goal>build-classpath</goal>
				</goals>
				<configuration>
					<outputProperty>maven.compile.classpath</outputProperty>
					<pathSeparator>:</pathSeparator>
				</configuration>
			</execution>
		</executions>
	</plugin>
	<plugin>
		<groupId>org.apache.maven.plugins</groupId>
		<artifactId>maven-compiler-plugin</artifactId>
		<configuration>
			<compilerArgs>
				<arg>-Aorg.springframework.boot.reflectionannotationprocessor.classpath=${maven.compile.classpath}</arg>
			</compilerArgs>
			<annotationProcessors>
				<annotationProcessor>
					org.springframework.boot.reflectionprocessor.ReflectiveAccessAnnotationProcessor

				</annotationProcessor>
			</annotationProcessors>
			<debug>true</debug>
		</configuration>
	</plugin>
</plugins>
```

### Output

The processor writes a `META-INF/reflect.json` file that has this kind of format:

```json
[
  {
    "name" : "java.lang.Class",
    "allDeclaredConstructors" : true,
    "allPublicConstructors" : true,
    "allDeclaredMethods" : true,
    "allPublicMethods" : true,
    "allDeclaredClasses" : true,
    "allPublicClasses" : true
  },
  {
    "name" : "java.lang.String",
    "fields" : [
      { "name" : "value", "allowWrite" : true },
      { "name" : "hash" }
    ],
    "methods" : [
      { "name" : "<init>", "parameterTypes" : [] },
      { "name" : "<init>", "parameterTypes" : ["char[]"] },
      { "name" : "charAt" },
      { "name" : "format", "parameterTypes" : ["java.lang.String", "java.lang.Object[]"] }
    ]
  },
  {
    "name" : "java.lang.String$CaseInsensitiveComparator",
    "methods" : [
      { "name" : "compare" }
    ]
  }
]
```
as described in [REFLECTION.md](https://github.com/oracle/graal/blob/master/substratevm/REFLECTION.md)

### Running Graal

The `test-projects` folder includes some examples. See the `build.sh` files in each, for example:

After running `mvn clean package` the `build.sh` file will:

```bash
unzip target/app-0.0.1-SNAPSHOT.jar -d target/app-0.0.1-SNAPSHOT

native-image
  -H:ReflectionConfigurationFiles=target/app-0.0.1-SNAPSHOT/META-INF/reflect.json 
  -Dio.netty.noUnsafe=true
  -H:+ReportExceptionStackTraces 
  --allow-incomplete-classpath
  -H:+ReportUnsupportedElementsAtRuntime
  -Dfile.encoding=UTF-8 
  -cp ".:$(echo target/app-0.0.1-SNAPSHOT/BOOT-INF/lib/*.jar | tr ' ' ':')":target/app-0.0.1-SNAPSHOT/BOOT-INF/classes
  com.example.demo1.Application
```
which will result in a executable form of the app in the current folder.



### Resources

[Graal REFLECTION.md](https://github.com/oracle/graal/blob/master/substratevm/REFLECTION.md)