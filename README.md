# About

A [keybase-bot](https://keybasebots.com/) that can take [scala](https://scala-lang.org) 
snippets and evaluates them using [ammonite](https://ammonite.io/).

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
