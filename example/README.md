## ExampleBot


### Docker image

The docker image build by the instructions bellow 
has no shells (ie no `/bin/sh`) nor `/dev/tty` devices, 
and no other executable besides the keybase client and the java runtime itself.

# Usage

Create a `secrets.env` file
having the following variables set:

```
KEYBASE_ALLOW_ROOT=1
KEYBASE_USERNAME=yourbot
KEYBASE_PAPERKEY=something very secret
```

```shell
# build the docker image
mill -i _.docker.build

# Run the bot
docker run \
 --env-file secrets/keybase.env \
 -ti keybase-amm
```

