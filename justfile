clean:
    rm -rf main main.jar

docker-build: clean
    scala-cli \
      --power package \
      --docker src/Main.scala \
      --docker-from azul/zulu-openjdk-alpine:24-jre-headless-latest \
      --docker-image-repository discodigg

docker-run: docker-build
    docker run --rm discodigg:latest

discodigg-run:
    scala-cli run --jvm 24 .

update-dependencies:
    scala-cli --power dependency-update --all .
