#
# Makefile to set up consistent build environment for generated files
#

# specific versions to ensure consistent rebuilds
BLACK_VERSION = 22.6.0
PYQT5_VERSION = 5.14.2
TORCH_VERSION = 1.11.0 # originally 1.4.0
TORCHVISION_VERSION = 0.12.0 # originally 0.5.0
GRPCIO_TOOLS =  1.44.0
protobuf==3.20.3


# LOCAL_EXECUTION_MODELS = \
# 	cafe_gogh.model \
# 	candy.model \
# 	david_vaughan.model \
# 	dido_carthage.model \
# 	fall_icarus.model \
# 	femmes_d_alger.model \
# 	going_to_work.model \
# 	monet.model \
# 	mosaic.model \
# 	rain_princess.model \
# 	starry-night.model \
# 	sunday_afternoon.model \
# 	the_scream.model \
# 	udnie.model \
# 	weeping_woman.model

# LOCAL_EXEC_ASSET_DIR = android-client/app/src/main/assets

GENERATED_FILES = \
	./demo/proto/include/openrtist.pb.cc

REQUIREMENTS = \
	'PyQT5==$(PYQT5_VERSION)' \
	'opencv-python' \
	'fire' \
	'torch==$(TORCH_VERSION)' \
	'torchvision==$(TORCHVISION_VERSION)' \
	'black==$(BLACK_VERSION)' \
	flake8 \
	flake8-bugbear \
	'grpcio-tools==1.44.0'\
	matplotlib \
	zmq \
	gabriel-server

all: $(GENERATED_FILES)

check: .venv
	.venv/bin/black --check .
	.venv/bin/flake8

reformat: .venv
	.venv/bin/black .

# docker: all
# 	docker build -t cmusatyalab/openrtist .

clean:
	$(RM) $(GENERATED_FILES)

distclean: clean
	$(RM) -r .venv

.venv:
	python3 -m venv .venv
	.venv/bin/pip install $(REQUIREMENTS)
	mkdir -p .venv/tmp
	touch .venv

%.py: %.ui .venv
	.venv/bin/pyuic5 -x $< -o $@

#update pip install grpcio-tools
demo/proto/include/openrtist.pb.cc: .venv
	/usr/bin/protoc -I=./demo/proto -I=./demo/proto/include --cpp_out=demo/proto gabriel.proto
	/usr/bin/protoc -I=./demo/proto -I=./demo/proto/include --cpp_out=demo/proto openrtist.proto

# .venv/bin/python -m grpc_tools.protoc --python_out=python-client/src/openrtist -I android-client/app/src/main/proto openrtist.proto

.PHONY: all check reformat docker clean distclean
.PRECIOUS: $(GENERATED_FILES)
