#
# Makefile to build and run dockerfile
#

all:

docker-env-build:
	sudo docker build -t cmusatyalab/openfluid:env -f DockerfileBuildEnv .

docker-env-run:
	sudo docker run --gpus all -it -p 9099:9099 --rm --name=openfluid-env --mount type=bind,source=${PWD}/server,target=/server cmusatyalab/openfluid:env

docker-build:
	sudo docker build -t cmusatyalab/openfluid:version1.0 -f Dockerfile .

docker-run:
	sudo docker run --gpus all --rm -it -p 9099:9099 cmusatyalab/openfluid:version1.0

.PHONY: all docker-env-build docker-env-run docker-build docker-run

