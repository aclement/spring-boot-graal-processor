
unzip target/demo1-0.0.1-SNAPSHOT.jar -d target/demo1-0.0.1-SNAPSHOT

native-image -H:ReflectionConfigurationFiles=target/demo1-0.0.1-SNAPSHOT/META-INF/reflect.json -Dio.netty.noUnsafe=true -H:+ReportExceptionStackTraces --allow-incomplete-classpath -H:+ReportUnsupportedElementsAtRuntime -Dfile.encoding=UTF-8 -cp ".:$(echo target/demo1-0.0.1-SNAPSHOT/BOOT-INF/lib/*.jar | tr ' ' ':')":target/demo1-0.0.1-SNAPSHOT/BOOT-INF/classes com.example.demo1.Demo1Application

