#
# Makefile to set up consistent build environment for generated files
#

GENERATED_FILES = \
	./demo/proto/openfluid.pb.cc \
	./demo/proto/openfluid.pb.h \
	./demo/proto/gabriel.pb.cc \
	./demo/proto/gabriel.pb.h \

all: $(GENERATED_FILES)

clean:
	$(RM) $(GENERATED_FILES)

#update pip install grpcio-tools
./demo/proto/openfluid.pb.cc:
	/usr/bin/protoc -I=./demo/proto --cpp_out=demo/proto gabriel.proto
	/usr/bin/protoc -I=../../android-client/app/src/main/proto --cpp_out=demo/proto openfluid.proto

.PHONY: all check reformat docker clean distclean
.PRECIOUS: $(GENERATED_FILES)
