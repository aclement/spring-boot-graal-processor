#!/usr/bin/env bash
mvn clean package

export JAR="commandlinerunner-0.0.1-SNAPSHOT.jar"
rm clr
printf "Unpacking $JAR"
rm -rf unpack
mkdir unpack
cd unpack
jar -xvf ../target/$JAR >/dev/null 2>&1

# Copying from root of jar into place where we'll work...
cp -R META-INF BOOT-INF/classes


cd BOOT-INF/classes
export LIBPATH=`find ../../BOOT-INF/lib | tr '\n' ':'`
export CP=.:$LIBPATH

#mkdir -p META-INF/native-image
#cp ../../../resource-config.json META-INF/native-image/.


# Spring configuration for native-image should be picked up from classpath
#export CP=$CP:../../../../../target/spring-boot-graal-feature-0.5.0.BUILD-SNAPSHOT.jar

printf "\n\nCompile\n"
native-image \
  -Dio.netty.noUnsafe=true \
  --no-server \
  --verbose \
  -H:Name=clr \
  -H:+ReportExceptionStackTraces \
  --no-fallback \
  --allow-incomplete-classpath \
  --initialize-at-build-time=ch.qos.logback.classic.LoggerContext,ch.qos.logback.classic,ch.qos.logback.classic.Logger,org.springframework.util,org.apache.commons.logging,ch.qos.logback,org.apache.logging,ch.qos.logback.classic,org.slf4j.LoggerFactory,org.slf4j.helpers.SubstituteLoggerFactory,org.slf4j.helpers.NOPLoggerFactory,org.slf4j.impl.StaticLoggerBinder,org.springframework.core.annotation,org.slf4j.helpers.Util \
  --report-unsupported-elements-at-runtime \
  -cp $CP com.example.commandlinerunner.CommandlinerunnerApplication

# k,org.slf4j.helpers.SubstituteLoggerFactory \
mv clr ../../..

#printf "\n\nJava exploded jar\n"
#time java -classpath $CP com.example.commandlinerunner.CommandlinerunnerApplication

printf "\n\nCompiled app (clr)\n"
cd ../../..
time ./clr

