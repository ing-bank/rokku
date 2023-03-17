#!/bin/bash
# waitForContainerSetup.sh

set -e

# Max query attempts before consider setup failed
MAX_TRIES=150

# Return true-like values if and only if logs contain the expected "ready" line
function cephIsReady() {
  docker-compose logs ceph | grep "* Running on http://\[::\]:5000/"
}
function rangerAdminIsReady() {
  docker-compose logs ranger-admin | grep "Ranger setup completed"
}
function rokkuStsIsReady() {
  docker-compose logs rokku-sts | grep "Sts service started listening:"
}
function keycloakIsReady() {
  docker-compose logs keycloak | grep "Admin console listening"
}
function redisIsReady() {
  docker-compose logs redis | grep "Ready to accept connections"
}

function waitUntilServiceIsReady() {
  attempt=1
  while [ $attempt -le $MAX_TRIES ]; do
    if "$@"; then
      echo "$2 container is up!"
      break
    fi
    echo "Waiting for $2 container... (attempt: $((attempt++)))"
    sleep 10
  done

  if [ $attempt -gt $MAX_TRIES ]; then
    echo "Error: $2 not responding, cancelling set up"
    exit 1
  fi
}

waitUntilServiceIsReady rokkuStsIsReady "Rokku STS"
waitUntilServiceIsReady cephIsReady "Ceph"
waitUntilServiceIsReady rangerAdminIsReady "Ranger Admin"
waitUntilServiceIsReady keycloakIsReady "Keycloack"
waitUntilServiceIsReady redisIsReady "Redis"

