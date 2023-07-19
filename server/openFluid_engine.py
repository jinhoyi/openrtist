# OpenRTiST
#   - Real-time Style Transfer
#
#   Authors: Zhuo Chen <zhuoc@cs.cmu.edu>
#           Shilpa George <shilpag@andrew.cmu.edu>
#           Thomas Eiszler <teiszler@andrew.cmu.edu>
#           Padmanabhan Pillai <padmanabhan.s.pillai@intel.com>
#           Roger Iyengar <iyengar@cmu.edu>
#           Meng Cao <mcao@andrew.cmu.edu>
#
#   Copyright (C) 2011-2020 Carnegie Mellon University
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
#
# Portions of this code borrow from sample code distributed as part of
# Intel OpenVino, which is also distributed under the Apache License.
#
# Portions of this code were modified from sampled code distributed as part of
# the fast_neural_style example that is part of the pytorch repository and is
# distributed under the BSD 3-Clause License.
# https://github.com/pytorch/examples/blob/master/LICENSE

import cv2
import numpy as np
import logging
from gabriel_server import cognitive_engine
from gabriel_protocol import gabriel_pb2
import openrtist_pb2
import os
from io import BytesIO
from time import time
import zmq
import os
import signal
import subprocess
import sys
from easyprocess import EasyProcess
from pyvirtualdisplay.smartdisplay import SmartDisplay

logger = logging.getLogger(__name__)
zmq_address = "tcp://localhost:5559"
zmq_imu_address = "tcp://*:5560"

from azure.cognitiveservices.vision.face import FaceClient
from azure.cognitiveservices.vision.face.models import FaceAttributeType
from msrest.authentication import CognitiveServicesCredentials
import http.client, urllib.request, urllib.parse, urllib.error, base64
import json
from emotion_to_style import emotion_to_style_map



class OpenrtistEngine(cognitive_engine.Engine):
    SOURCE_NAME = "openrtist"

    # For Acceleration Sensor Event
    GRAVITY_EARTH =  9.80665 ** 2
    THRESOLD = 40
    WAIT_TIME = 1000
    SHAKE_STYLE = "going_to_work"


    def __init__(self, compression_params, adapter):
        self.compression_params = compression_params
        self.adapter = adapter
        self.face_supported = os.getenv("FaceEnabled", False)
        if self.face_supported:
            logger.info("Emotion-based styling enabled via MS Face Service.")
        else:
            logger.info("Emotion-based styling disabled.")
        # The waterMark is of dimension 30x120
        wtr_mrk4 = cv2.imread("../wtrMrk.png", -1)

        # The RGB channels are equivalent
        self.mrk, _, _, mrk_alpha = cv2.split(wtr_mrk4)

        self.alpha = mrk_alpha.astype(float) / 255

        self.curr_accel = self.GRAVITY_EARTH
        self.x = 0
        self.y = 0
        self.z = 0
        self.mv_accel = self.GRAVITY_EARTH
        self.isShaking = False
        self.lastTimeShakeDetected = 0

        self.zmq_context = zmq.Context()
        self.zmq_socket = self.zmq_context.socket(zmq.REQ)
        self.zmq_socket.connect(zmq_address)

        self.zmq_imu_socket = self.zmq_context.socket(zmq.REP)
        self.zmq_imu_socket.bind(zmq_imu_address)

        self.phys_simulator = None
        self.xvfb = None
        self.sendStyle = True
        self.screen_w = 720
        self.screen_h = 640


        # TODO support server display

        # check if Face api is supported
        if self.face_supported:
            self.face_client = FaceClient(
                "http://ms-face-service:5000",
                CognitiveServicesCredentials(os.getenv("ApiKey")),
            )

        logger.info("FINISHED INITIALISATION")

    def __del__(self):
        if self.phys_simulator != None:
            self.phys_simulator.kill()
        if self.xvfb != None:    
            self.xvfb.kill()

    def grab_frame(self):
        self.zmq_socket.send_string("0")
        raw_msg = self.zmq_socket.recv()

        input_frame = gabriel_pb2.InputFrame()
        input_frame.ParseFromString(raw_msg)
        orig_img = np.frombuffer(input_frame.payloads[0], dtype=np.uint8)
        orig_img = np.flipud(orig_img.reshape((self.screen_h,self.screen_w,4)))
        orig_img = cv2.cvtColor(orig_img, cv2.COLOR_BGRA2RGB)

        return orig_img
    
    def send_imu(self, extras):
        raw_msg = self.zmq_imu_socket.recv()
        self.zmq_imu_socket.send(extras.SerializeToString())
        return


    def handle(self, input_frame):
        if input_frame.payload_type != gabriel_pb2.PayloadType.IMAGE:
            status = gabriel_pb2.ResultWrapper.Status.WRONG_INPUT_FORMAT
            return cognitive_engine.create_result_wrapper(status)
        
        extras = cognitive_engine.unpack_extras(openrtist_pb2.Extras, input_frame)
        
        # if extras.screen_value.height != 0:
        #     print(f'-windowed={self.screen_w }x{self.screen_h}')
        #     ratio = extras.screen_value.height / extras.screen_value.width
        #     self.screen_w = extras.screen_value.width
        #     self.screen_h = extras.screen_value.height

        ratio = extras.screen_value.height / extras.screen_value.width
        if int(self.screen_w  * ratio) != self.screen_h:
            self.screen_h = int(ratio * self.screen_w)
            if self.phys_simulator != None:
                self.phys_simulator.kill()
                ARGS = [f'exec backend/bin/linux64/NvFlexDemoReleaseCUDA_x64 -vsycn=0 -windowed={self.screen_w }x{self.screen_h}']
                self.phys_simulator = subprocess.Popen(ARGS, shell=True, preexec_fn=os.setsid)

        if self.phys_simulator == None:
            print(f'-windowed={self.screen_w }x{self.screen_h}')
            ARGS = [f'exec backend/bin/linux64/NvFlexDemoReleaseCUDA_x64 -vsycn=0 -windowed={self.screen_w }x{self.screen_h}']
            # ARGS = ['exec backend/bin/linux64/NvFlexDemoReleaseCUDA_x64', '-vsycn=0']
            self.phys_simulator = subprocess.Popen(ARGS, shell=True, preexec_fn=os.setsid)
        
        self.send_imu(extras)
        # self.zmq_imu_socket.send(extras.SerializeToString())

        # new_style = False
        # send_style_list = False

        self.mv_accel = 0


        # if extras.style == "?": # Style list is not retrieved by the client
        #     # new_style = True
        #     send_style_list = True

        # if extras.style != self.adapter.get_style():
        #     self.adapter.set_style(self.SHAKE_STYLE)
        #     logger.info("New Style: %s", extras.style)
        #     new_style = True


        # Preprocessing steps used by both engines
        image = self.grab_frame()

        # # It is possible that no face is detected and style is None, if so bypass processing
        # if style:
        image = self.process_image(image)
        # else:
        #     image = orig_img


        # # scale image back to original size to get a better watermark

        # if orig_img.shape != image.shape:
        #     orig_h, orig_w, _ = orig_img.shape
        #     image = cv2.resize(
        #         image, (orig_w, orig_h), interpolation=cv2.INTER_LINEAR
        #     )
        image = self._apply_watermark(image)

        _, jpeg_img = cv2.imencode(".jpg", image, self.compression_params)
        img_data = jpeg_img.tostring()

        result = gabriel_pb2.ResultWrapper.Result()
        result.payload_type = gabriel_pb2.PayloadType.IMAGE
        result.payload = img_data

        extras = openrtist_pb2.Extras()

        # if style:
        #     extras.style = style

        # if new_style:
        #     extras.style_image.value = self.adapter.get_style_image()
        if self.sendStyle:
            for k, v in self.adapter.get_all_styles().items():
                extras.style_list[k] = v
            self.sendStyle = False

        status = gabriel_pb2.ResultWrapper.Status.SUCCESS
        result_wrapper = cognitive_engine.create_result_wrapper(status)
        result_wrapper.results.append(result)
        result_wrapper.extras.Pack(extras)

        return result_wrapper

    # https://westus.dev.cognitive.microsoft.com/docs/services/563879b61984550e40cbbe8d/operations/563879b61984550f30395236
    def emotion_detection(self, img_bytes):
        style = None
        detected_faces = []

        try:
            detected_faces = self.face_client.face.detect_with_stream(
                image=BytesIO(img_bytes),
                return_face_id=False,
                return_face_landmarks=False,
                return_face_attributes=list([FaceAttributeType.emotion]),
            )
        except Exception as e:
            logger.error(e)

        if len(detected_faces) == 0:
            # no face detected
            style = None
        else:
            # get the largest face in the image
            largest_face = detected_faces[0]

            # get the strongest emotion of the face
            emotions = largest_face.face_attributes.emotion
            strongest_emotion = max(emotions.as_dict(), key=emotions.as_dict().get)

            if strongest_emotion in emotion_to_style_map:
                style = emotion_to_style_map[strongest_emotion]

        return style

    def process_image(self, image):
        return image

    def inference(self, preprocessed):
        """Allow timing engine to override this"""
        return preprocessed

    def _apply_watermark(self, image):
        img_mrk = image[-30:, -120:]  # The waterMark is of dimension 30x120
        img_mrk[:, :, 0] = (1 - self.alpha) * img_mrk[:, :, 0] + self.alpha * self.mrk
        img_mrk[:, :, 1] = (1 - self.alpha) * img_mrk[:, :, 1] + self.alpha * self.mrk
        img_mrk[:, :, 2] = (1 - self.alpha) * img_mrk[:, :, 2] + self.alpha * self.mrk
        image[-30:, -120:] = img_mrk
        # img_out = image.astype("uint8")
        # img_out = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

        return image
