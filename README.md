# discodigg

[discodigg] is a tool for collecting basic information about Discord servers.

## Usage

```bash
$ discodigg resolve-from-urls --invite-url 'https://discord.gg/ZGfbbXcN' --invite-url 'https://discord.gg/QGJTzFaR' 
$ discodigg resolve-from-file --file ./servers.yml
$ discodigg resolve-from-file --help
```

## Development

```bash
cargo run -- resolve-from-file --file ./servers.yml
carbo build --release
```

## Author

- [Oto Brglez](https://github.com/otobrglez)

[discodigg]: https://github.com/otobrglez/discodigg