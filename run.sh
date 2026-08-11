#!/bin/zsh

# Set your token here so all child processes see it
export GITHUB_TOKEN=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

echo "Starting Amie Full Stack..."

cd /Users/samuel/Documents/GitHub/Amie/packageRepository
./gradlew bootRun &

cd /Users/samuel/Documents/GitHub/Amie/app
./gradlew :site:kobwebStart &

echo "Servers are starting in the background. Check your browser at http://localhost:8081"
