#!/bin/zsh

export JAVA_HOME=/Users/samuel/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home
export GITHUB_TOKEN=
# Keycloak-issued tokens: the issuer must match the realm frontend URL; see README "Environment"
export KEYCLOAK_ISSUER=${KEYCLOAK_ISSUER:-http://192.168.1.114:8080/realms/master}
export KEYCLOAK_JWKS_URI=${KEYCLOAK_JWKS_URI:-http://localhost:8080/realms/master/protocol/openid-connect/certs}
export KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET:-}

echo "Starting Amie Full Stack..."

# Start Backend
cd /Users/samuel/Documents/GitHub/Amie/packageRepository
./gradlew bootRun -Dorg.gradle.jvmargs="-Xmx2g" &

# Start Frontend
cd /Users/samuel/Documents/GitHub/Amie/app
./gradlew :site:kobwebStart &

# Start the S3 service (jar downloads, port 8088)
cd /Users/samuel/Documents/GitHub/Amie/awsS3
./gradlew bootRun &

echo "Servers are starting in the background."
