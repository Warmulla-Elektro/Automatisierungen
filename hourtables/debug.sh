#!/bin/bash

./pull.sh
./gradlew simplifyShadow || (echo 'Project build failed' && exit 1)
(cd build/libs && java -agentlib:jdwp=transport=dt_socket,server=n,address=*:5005,suspend=y -jar hourtables.jar -up)
