# About

A [keybase-bot](https://keybasebots.com/) that can take [scala](https://scala-lang.org) 
snippets and evaluates them using [ammonite](https://ammonite.io/).

# Build your own keybase-bot in scala

To create a bot the user must create a *Bot* that has *BotAction*s. A *BotAction* takes messages passed to the Bot and responds according to the *Action*. An *Action* is the command passed to the Bot (eg: !help, help would be the *Action*).

```scala
import keybase._

object App extends Bot(
    "help" -> BotAction(
        logMessage = args => s"User asked help with args: $args",
        response = (args: String, reply: BotAction.ReplyFunction): Future[Unit] = {
            (for {
                _ <- reply("Looking for help...")
                _ <- Future[Unit] { Thread.sleep(2000) }
                _ <- reply("Found help! Hold tight.")
            } yield {}).recoverWith {
                case error => reply("Help not found => ${error.getMessage}")
            }
        }
    )
)
```

Check out examples in the examples folder.

# Security considerations

The authors are not responsible for any damage caused by a the use
of this project. Use only under your own risk. 

Please read the LICENSE file on this repo.

### Arbitrary code execution.

Since you can feed any scala code snippet to this bot, you must be aware that
it's possible to download any arbitrary maven dependency using ammonite, 
and hence you are solely responsible for who has access to this bot and what
kind of code is evaluated by it.

### Docker image

The docker image build by the instructions bellow 
has no shells (ie no `/bin/sh`) nor `/dev/tty` devices, 
and no other executable besides the keybase client and the java runtime itself.

# Usage

Create a `secrets/keybase.env` file
having the following variables set:

```
KEYBASE_ALLOW_ROOT=1
KEYBASE_USERNAME=yourbot
KEYBASE_PAPERKEY=something very secret
```

```shell
# build the docker image
mill -i keybase.docker.build

# Run the bot
docker run \
 --env-file secrets/keybase.env \
 -ti keybase-amm
```

Useful links:

- https://github.com/keybase/client/tree/master/packaging/linux/docker
- https://github.com/keybase/keybase-bot
- https://keybase.io/blog/bots
