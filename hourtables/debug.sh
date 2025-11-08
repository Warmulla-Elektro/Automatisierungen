#!/bin/bash

./pull.sh
./gradlew simplifyShadow || (echo 'Project build failed' && exit 1)
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 -jar build/libs/hourtables.jar -op
