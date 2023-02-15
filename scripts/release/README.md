# Release build scripts

## Build environment

The build environment can be setup using this command:

```sh
docker build pekko:build
```

## Release script

`build-release.sh` will run the build, and provided gpg credentials by forwarding the GPG agent connection to the docker container.
Understand the risk of exposing the gpg-agent to a container.

```sh
docker run \
  -ti --rm \
  -v `pwd`:/scripts:ro \
  -v ${HOME}/.gnupg/:/home/user/.gnupg/:ro -v /run/user/$(id -u)/:/run/user/$(id -u)/:ro \
  -e BUILD_COMMIT=origin/main \
  -e GPG_SIGNING_KEY=8DEF770BCFC57CEC83BF0410DC20AD935AC6CEF4 \
  pekko:build /scripts/build-release.sh
```

## Config environment settings

 * `BUILD_COMMIT`: The commit to checkout and build
 * `GPG_SIGNING_KEY`: The GPG key ID to use for signing artifacts