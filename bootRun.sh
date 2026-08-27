#!/bin/zsh

export JAVA_HOME=/Users/samuel/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home
export GITHUB_TOKEN=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

echo "...spring..."

# Kill any existing process on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null

# Start Backend
cd /Users/samuel/Documents/GitHub/Amie/packageRepository
./gradlew bootRun
