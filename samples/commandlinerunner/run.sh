#!/usr/bin/env bash

cd unpack

cd BOOT-INF/classes
export LIBPATH=`find ../../BOOT-INF/lib | tr '\n' ':'`
export CP=.:$LIBPATH

# Spring configuration for native-image should be picked up from classpath
#export CP=$CP:../../../../../target/spring-boot-graal-feature-0.5.0.BUILD-SNAPSHOT.jar

java -classpath $CP com.example.commandlinerunner.CommandlinerunnerApplication


