# discodigg

System for observing Discord servers and their membership.

## Development

- Please use [devenv] for development with [Scala CLI][scala-cli].
- All the "tasks" are written in [`justfile`](./justfile) and can be run with [just](https://just.systems/) command runner.
- System uses the latest JVM 25, with ZIO, ZIO HTTP and Scala 3.x

## Deployment

Build a Docker image with the help of `just`:

```bash
just docker-build

docker run --rm \
        -e JAVA_OPTS="--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED " \
        -v ${PWD}/servers.yml:/tmp/servers.yml \
        -p 8081:8081 \
        -p 8082:8082 \
        discodigg/discodigg:`cat VERSION` -- server \
        --refresh-interval=PT360S ./tmp/servers.yml

```


## Author

[Oto Brglez](https://github.com/otobrglez)

[scala-cli]: https://scala-cli.virtuslab.org/
[devenv]: https://devenv.sh/
