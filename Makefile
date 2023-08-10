#
# Makefile to build and run dockerfile
#

IMAGE_ID ?= 9ac5dec1e42b
GITHUB_USRNAME ?= jinhoyi
VERSION ?= version1.0

ifeq (docker-push,$(firstword $(MAKECMDGOALS)))
	ifneq ($(word 2,$(MAKECMDGOALS)),)
		IMAGE_ID := $(word 2,$(MAKECMDGOALS))
		$(eval $(IMAGE_ID):;@:)
	endif

	ifneq ($(word 3,$(MAKECMDGOALS)),)
  		VERSION := $(word 3,$(MAKECMDGOALS))
		$(eval $(VERSION):;@:)
	endif

	ifneq ($(word 4,$(MAKECMDGOALS)),)
  		GITHUB_USRNAME := $(word 4,$(MAKECMDGOALS))
		$(eval $(GITHUB_USRNAME):;@:)
	endif
	
    # RUN_ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
    # # ...and turn them into do-nothing targets
    # $(eval $(RUN_ARGS):;@:)
endif

ifeq ($(filter docker-git-run docker-run docker-build docker-pull,$(firstword $(MAKECMDGOALS))), $(firstword $(MAKECMDGOALS)) )
	ifneq ($(word 2,$(MAKECMDGOALS)),)
  		VERSION := $(word 2,$(MAKECMDGOALS))
		$(eval $(VERSION):;@:)
	endif

	ifneq ($(word 3,$(MAKECMDGOALS)),)
  		GITHUB_USRNAME := $(word 3,$(MAKECMDGOALS))
		$(eval $(GITHUB_USRNAME):;@:)
	endif
endif

ifeq ($(filter docker-env-build docker-env-run docker-env-git-run,$(firstword $(MAKECMDGOALS))),$(firstword $(MAKECMDGOALS)))
	ifneq ($(word 2,$(MAKECMDGOALS)),)
  		GITHUB_USRNAME := $(word 2,$(MAKECMDGOALS))
		$(eval $(GITHUB_USRNAME):;@:)
	endif
endif

all:


# docker-build [version] [username] 
docker-build:
	sudo docker build -t $(GITHUB_USRNAME)/openfluid:$(VERSION) -f Dockerfile .

# docker-run [version] [username] 
docker-run:
	sudo docker run --gpus all --rm -it -p 9099:9099 $(GITHUB_USRNAME)/openfluid:$(VERSION)

# docker-push [image-id] [version] [username] 
docker-push:
	echo $(CR_PAT) | sudo docker login ghcr.io -u $(GITHUB_USRNAME) --password-stdin && \
	sudo docker tag $(IMAGE_ID) ghcr.io/$(GITHUB_USRNAME)/openfluid:$(VERSION) && \
	sudo docker push ghcr.io/$(GITHUB_USRNAME)/openfluid:$(VERSION)

# docker-pull:
# 	echo $(CR_PAT) | sudo docker login ghcr.io -u $(GITHUB_USRNAME) --password-stdin && \
# 	sudo docker inspect ghcr.io/$(GITHUB_USRNAME)/openfluid:$(VERSION) | jq -r '.[0].Id' | \
# 	xargs -I {} sudo docker pull ghcr.io/$(GITHUB_USRNAME)/openfluid@{}

# docker-pull [version] [username] 
docker-pull:
	echo $(CR_PAT) | sudo docker login ghcr.io -u $(GITHUB_USRNAME) --password-stdin
	sudo docker pull ghcr.io/$(GITHUB_USRNAME)/openfluid:$(VERSION)

# docker-git-run [version] [username] 
docker-git-run:
	sudo docker run --gpus all --rm -it -p 9099:9099 ghcr.io/$(GITHUB_USRNAME)/openfluid:$(VERSION)

# docker-env-build [username] 
docker-env-build:
	sudo docker build -t $(GITHUB_USRNAME)/openfluid:env -f DockerfileBuildEnv .

# docker-env-run [username] 
docker-env-run:
	sudo docker run --gpus all -it -p 9099:9099 --rm --name=openfluid-env --mount type=bind,source=${PWD}/server,target=/server $(GITHUB_USRNAME)/openfluid:env

# docker-env-git-run [username] 
docker-env-git-run:
	sudo docker run --gpus all -it -p 9099:9099 --rm --name=openfluid-env --mount type=bind,source=${PWD}/server,target=/server ghcr.io/$(GITHUB_USRNAME)/openfluid:env

.PHONY: all docker-env-build docker-env-run docker-build docker-run docker-pull docker-push docker-git-run