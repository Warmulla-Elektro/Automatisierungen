#!/bin/bash

./gradlew installDist
cd build/install/hourtables/bin
./hourtables -up
