#!/bin/bash

./pull.sh
./gradlew installDist
cd build/install/hourtables/bin
./hourtables -up
