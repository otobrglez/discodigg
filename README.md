# discodigg

System for observing Discord servers and their membership.

Live: [discord.pinkstack.com - Slovenian Discord Servers](https://discord.pinkstack.com/)

## Development

- Please use [devenv] for development with [Scala CLI][scala-cli].
- All the "tasks" are written in [`justfile`](./justfile) and can be run with [just](https://just.systems/) command runner.
- System uses the latest [JVM/JDK 25](https://openjdk.org/projects/jdk/25/), [ZIO](https://zio.dev/), [ZIO HTTP](https://github.com/zio/zio-http), [HTMX](https://htmx.org/) and [Scala](https://www.scala-lang.org/) 3.x
- Get familiar with [Discord API](https://discord.com/developers/docs/intro), especially around [Invite Resource](https://discord.mintlify.app/developers/docs/resources/invite).

## Deployment

Build a Docker image with the help of just in [`justfile`](justfile):

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

Port `8081` is for the UI, and port `8082` is for serving [Prometheus metrics](https://prometheus.io/).

There is also a Kubernetes deployment in the [k8s](./k8s/) folder.

## Author

[Oto Brglez](https://github.com/otobrglez)

[scala-cli]: https://scala-cli.virtuslab.org/
[devenv]: https://devenv.sh/
