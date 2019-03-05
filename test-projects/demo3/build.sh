
unzip target/demo3-0.0.1-SNAPSHOT.jar -d target/demo3-0.0.1-SNAPSHOT

#native-image -H:ReflectionConfigurationFiles=graal.json -Dio.netty.noUnsafe=true -H:+ReportUnsupportedElementsAtRuntime -Dfile.encoding=UTF-8 -cp ".:$(echo target/spring-boot-graal-demo-0.0.1-SNAPSHOT/BOOT-INF/lib/*.jar | tr ' ' ':')":target/spring-boot-graal-demo-0.0.1-SNAPSHOT/BOOT-INF/classes com.example.graaldemo.GraalDemoApplication

native-image -H:ReflectionConfigurationFiles=target/demo3-0.0.1-SNAPSHOT/META-INF/reflect.json -Dio.netty.noUnsafe=true -H:+ReportExceptionStackTraces --allow-incomplete-classpath -H:+ReportUnsupportedElementsAtRuntime -Dfile.encoding=UTF-8 -cp ".:$(echo target/demo3-0.0.1-SNAPSHOT/BOOT-INF/lib/*.jar | tr ' ' ':')":target/demo3-0.0.1-SNAPSHOT/BOOT-INF/classes com.example.demo1.Demo1Application

