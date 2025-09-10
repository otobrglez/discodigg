version := `cat VERSION`
discodigg-image-tag := "discodigg:" + version
discodigg-image-repository := "discodigg/discodigg"
discodigg-image-repository-base := "azul/zulu-openjdk-alpine:24-jre-headless-latest"

clean:
    rm -rf main main.jar

docker-build: clean
    scala-cli \
      --power package \
      --jvm 24 \
      --project-version={{ version }} \
      --docker . \
      --docker-from={{ discodigg-image-repository-base }} \
      --docker-image-repository={{ discodigg-image-repository }} \
      --docker-image-tag={{ version }}

discodigg-docker-collect-run: docker-build
    docker run --rm \
        -e JAVA_OPTS="--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED " \
        -v ${PWD}/servers.yml:/tmp/servers.yml \
        {{ discodigg-image-repository }}:{{ version }} -- collect \
            --refresh-interval=PT360S ./tmp/servers.yml

discodigg-docker-server-run: docker-build
    docker run --rm \
        -e JAVA_OPTS="--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED " \
        -v ${PWD}/servers.yml:/tmp/servers.yml \
        -p 8082:8081 \
        {{ discodigg-image-repository }}:{{ version }} -- server --port=8082 ./tmp/servers.yml

discodigg-collect-run:
    scala-cli run --jvm 24 --project-version={{ version }} . -- collect \
        --refresh-interval=PT120S ./servers.yml

discodigg-server-run:
    scala-cli run --jvm 24 --project-version={{ version }} . -- server ./servers.yml -P 8081

update-dependencies:
    scala-cli --power dependency-update --all .
