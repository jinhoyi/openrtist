#
# Makefile to set up consistent build environment for generated files
#

# specific versions to ensure consistent rebuilds
BLACK_VERSION = 22.6.0
PYQT5_VERSION = 5.14.2

LOCAL_EXEC_ASSET_DIR = android-client/app/src/main/assets

GENERATED_FILES = \
	python-client/src/openrtist/openfluid_pb2.py

REQUIREMENTS = \
	'PyQT5==$(PYQT5_VERSION)' \
	'fire' \
	'torch==$(TORCH_VERSION)' \
	'torchvision==$(TORCHVISION_VERSION)' \
	'black==$(BLACK_VERSION)' \
	flake8 \
	flake8-bugbear \
	'grpcio-tools==1.44.0'\
	'protobuf==3.20.3'\

all: $(GENERATED_FILES)

check: .venv
	.venv/bin/black --check .
	.venv/bin/flake8

reformat: .venv
	.venv/bin/black .

docker: all
	docker build -t cmusatyalab/openrtist .

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
python-client/src/openfluid/openfluid_pb2.py: android-client/app/src/main/proto/openfluid.proto .venv
	.venv/bin/python -m grpc_tools.protoc --python_out=server -I android-client/app/src/main/proto openfluid.proto
	.venv/bin/python -m grpc_tools.protoc --python_out=python-client/src/openfluid -I android-client/app/src/main/proto openfluid.proto

$(LOCAL_EXEC_ASSET_DIR)/%.pt: models/%.model .venv
	mkdir -p $(LOCAL_EXEC_ASSET_DIR)
	.venv/bin/python scripts/freeze_model.py freeze --weight-file-path='$<' --output-file-path='$@'

.PHONY: all check reformat docker clean distclean
.PRECIOUS: $(GENERATED_FILES)
