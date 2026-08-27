#!/bin/zsh

export JAVA_HOME=/Users/samuel/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home
export GITHUB_TOKEN=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

echo "Starting Amie Full Stack..."

# Start Backend
cd /Users/samuel/Documents/GitHub/Amie/packageRepository
./gradlew bootRun -Dorg.gradle.jvmargs="-Xmx2g" &

# Start Frontend
cd /Users/samuel/Documents/GitHub/Amie/app
./gradlew :site:kobwebStart &

echo "Servers are starting in the background."
