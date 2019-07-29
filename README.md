# spring-boot-graal-processor

Annotation processor that produces a JSON file detailing types needing reflective access that can be passed to a graal native image build step.

### Operation

The processor is looking for a @CompilationHint either directly on a type or used as a meta annotation on a type.
Based on these hints it computes the reflection/resource/dynamic-proxy json files in a META-INF/native-image folder and
creates a native-image.properties file that points at them. When the native-image command is used later it will pick them up and configure
itself appropriately.


This is all very prototypey and experimenty. The interesting sample being worked on is in samples/commandlinerunner - this is
a simple spring app that is attempting to use the output from a processor rather than a graal feature.


For this to work you need to use a custom build of spring framework and spring boot.

The compilation hint class is actually defined in the core spring framework project and then various framework/boot modules
are configured to use this annotation processor as they are built. This results in spring framework/boot component jars that
include the necessary graal native-image configuration data.


Spring Framework compilation hinted fork: https://github.com/aclement/spring-framework/tree/v5.2.0.M1plus
(The version number is 5.2.0.M1plus when built)

Spring Boot compilation hinted fork: https://github.com/aclement/spring-boot/tree/2.2.0.M2plus
(The version number is 2.2.0.M2plus when built)

To build these forks you must have 'mvn clean install' the processor project.

And then we set the dependencies in the commandlinerunner app to be on these plus versions of framework/boot.

It isn't quite finished yet as there are a few additional hints required across framework/boot. The compile.sh script
in the commandlinerunner folder unpacks the jar (into an unpack folder) then runs native-image against it. There
is a workingunpacked.zip - this contains the shape of an unpack folder that would run ok through native-image. In this
working unpacked folder you will see the native-image folder contains files with more data that computed right now
by pure annotation processing. i.e. it contains the entries that the annotation processor needs to be expanded
to cover. (giving a nice target to aim at to make a working system).

