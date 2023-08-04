#!/usr/bin/env python3

from gabriel_server import local_engine
# from openrtist_engine import OpenrtistEngine
from openfluid_engine import OpenfluidEngine
from timing_engine import TimingEngine
import logging
import cv2
import argparse
import importlib
import sys

DEFAULT_PORT = 9099
DEFAULT_NUM_TOKENS = 5
INPUT_QUEUE_MAXSIZE = 240
DEFAULT_STYLE = "the_scream"
COMPRESSION_PARAMS = [cv2.IMWRITE_JPEG_QUALITY, 67]
import signal 
import sys

logging.basicConfig(level=logging.INFO)

logger = logging.getLogger(__name__)

def main():
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    parser.add_argument(
        "-t", "--tokens", type=int, default=DEFAULT_NUM_TOKENS, help="number of tokens"
    )
    parser.add_argument(
        "-o",
        "--openvino",
        action="store_true",
        help="Pass this flag to force the use of OpenVINO."
        "Otherwise Torch may be used",
    )
    parser.add_argument(
        "-c",
        "--cpu-only",
        action="store_true",
        help="Pass this flag to prevent the GPU from being used.",
    )
    parser.add_argument(
        "--torch",
        action="store_true",
        help="Set this flag to force the use of torch. Otherwise"
        "OpenVINO may be used.",
    )
    parser.add_argument(
        "--myriad",
        action="store_true",
        help="Set this flag to use Myriad VPU (implies use OpenVino).",
    )
    parser.add_argument(
        "--timing", action="store_true", help="Print timing information"
    )
    parser.add_argument(
        "-p", "--port", type=int, default=DEFAULT_PORT, help="Set port number"
    )
    args = parser.parse_args()

    def engine_setup():
        # adapter = create_adapter(args.openvino, args.cpu_only, args.torch, args.myriad)
        if args.timing:
            engine = TimingEngine(COMPRESSION_PARAMS, None)
        else:
            engine = OpenfluidEngine(COMPRESSION_PARAMS, None)

        return engine




    local_engine.run(
        engine_setup,
        OpenfluidEngine.SOURCE_NAME,
        INPUT_QUEUE_MAXSIZE,
        args.port,
        args.tokens,
    )

def signal_handler(signal, frame):
    for obj in OpenfluidEngine.instances:
        print("releasing")
        obj.release()
    sys.exit(0)


if __name__ == "__main__":
    signal.signal(signal.SIGINT, signal_handler)

    main()
