#!/bin/zsh
cd ./packageRepository
./gradlew bootRun &

cd ..
cd ./app
kobweb run &


