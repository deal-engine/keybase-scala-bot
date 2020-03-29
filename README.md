Create a `secrets/keybase.env` file
having the following variables set:

```
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
