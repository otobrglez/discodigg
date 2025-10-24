#!/usr/bin/env bash
set -ex

read_version() {
    if [ ! -f VERSION ]; then
        echo "ERROR: VERSION file not found" >&2
        exit 1
    fi
    cat VERSION
}

VERSION=$(read_version)

scala-cli \
  --power package \
  --jvm 25 \
  --project-version="${VERSION}" \
  --docker . \
  --docker-from=azul/zulu-openjdk-alpine:25-jre-headless-latest \
  --docker-image-repository=ghcr.io/otobrglez/discodigg \
  --docker-image-tag="${VERSION}"