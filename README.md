[![](https://jitpack.io/v/deal-engine/keybase-scala-bot.svg)](https://jitpack.io/#deal-engine/keybase-scala-bot)

# Lainz Bot Library - A smol library for Slack/Keybase Scala bots

## About

A library for building [Keybase Bots](https://keybasebots.com/) using [scala](https://scala-lang.org). And now also Keybase bots using the official java API SDK.

## Build your own keybase/slack-bot in scala

To create a bot the user must create a *Bot* that has *BotAction*s. A *BotAction* takes messages passed to the Bot and responds according to the *Action*. An *Action* is the command passed to the Bot (eg: !help, help would be the *Action*).

An example bot is available at `example/src`. And can be run via the following command

```shell
vi example/secrets.env # Follow example/README.md
./ci example # This will launch ExampleBot in docker
```

## Security considerations

The authors are not responsible for any damage caused by a the use
of this project. Use only under your own risk. 

Please read the LICENSE file on this repo.


## TODO

- More documentation. Sorry :c
- Scala 3

## Useful links:

- https://github.com/keybase/client/tree/master/packaging/linux/docker
- https://github.com/keybase/keybase-bot
- https://keybase.io/blog/bots
- https://github.com/slackapi/java-slack-sdk
