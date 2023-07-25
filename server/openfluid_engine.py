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
import time
from threading import Thread, Lock

logger = logging.getLogger(__name__)


from azure.cognitiveservices.vision.face import FaceClient
from azure.cognitiveservices.vision.face.models import FaceAttributeType
from msrest.authentication import CognitiveServicesCredentials
import http.client, urllib.request, urllib.parse, urllib.error, base64


class OpenrfluidEngine(cognitive_engine.Engine):
    SOURCE_NAME = "openfluid"
    SHAKE_STYLE = "going_to_work"

    scene_list = [("00","Rock Pool"),
                  ("01","Pot Pourri"),
                  ("02","Viscosity Low"),
                  ("03","Viscosity Med"),
                  ("04","Viscosity High"),
                  ("05","Buoyancy"),
                  ("06","Surface Tension Low"),
                  ("07","Surface Tension Med"),
                  ("08","Surface Tension High"),
                  ("09","DamBreak"),
                  ("10","Fluid Block")
                    ]

    def __init__(self, compression_params, args_engine = None):
        if args_engine == None:
            args_engine = {'frame_port': '5559', 'imu_port': '5560'}
        
        self.zmq_address = args_engine['frame_port']
        self.zmq_imu_address = args_engine['imu_port']

        # Water Mark
        self.compression_params = compression_params
        wtr_mrk4 = cv2.imread("../wtrMrk.png", -1)

        self.mrk, _, _, mrk_alpha = cv2.split(wtr_mrk4)     # The RGB channels are equivalent
        self.alpha = mrk_alpha.astype(float) / 255
        self.mrk = np.expand_dims(self.mrk, axis=-1)
        self.alpha = np.expand_dims(self.alpha, axis=-1)

        self.mrk_h = self.mrk.shape[0]
        self.mrk_w = self.mrk.shape[1]
        self.mrk_ratio = self.mrk_h / self.mrk_w  # H / W

        # Initialize ZeroMQ Context and Socket
        self.zmq_context = zmq.Context()

        # self.frame_socket = self.zmq_context.socket(zmq.PULL)
        # self.frame_socket.setsockopt(zmq.RCVHWM, 1)
        # self.frame_socket.connect(self.zmq_address)
        # # self.frame_socket.setsockopt_string(zmq.SUBSCRIBE, "Img")
        self.frame_socket = self.zmq_context.socket(zmq.REQ)
        # self.frame_socket.setsockopt( zmq.RCVTIMEO, 500 ) # milliseconds
        self.frame_socket.bind("tcp://*:" + self.zmq_address)

        self.imu_socket = self.zmq_context.socket(zmq.PUSH)
        # self.imu_socket.setsockopt(zmq.SNDHWM , 1)
        self.imu_socket.bind("tcp://*:" + self.zmq_imu_address)
        

        # IMU sensor Data
        self.x = 0
        self.y = 0
        self.z = 0

        # Initialize Screen Resolution of the client
        self.screen_w = 480
        self.screen_h = 1080
        # self.screen_h = 1440
        # self.screen_h = 2176

        # Pointer to the Physics Simulation Engine Process
        self.phys_simulator = None

        # Scene List retreived
        self.sendStyle = True

        logger.info("FINISHED INITIALISATION")

    def __del__(self):
        if self.phys_simulator != None:
            self.phys_simulator.kill()
        
        while self.phys_simulator.poll() is None:
                    time.sleep(0.1)

    

    # def get_frame(self):
    #     #Async Request of new rendered frame to the Simulation Engin
    #     self.frame_socket.send_string("0")
    #     raw_msg = self.frame_socket.recv()

    #     input_frame = gabriel_pb2.InputFrame()
    #     input_frame.ParseFromString(raw_msg)
    #     orig_img = np.frombuffer(input_frame.payloads[0], dtype=np.uint8)
    #     orig_img = np.flipud(orig_img.reshape((self.screen_h,self.screen_w,4)))
    #     orig_img = cv2.cvtColor(orig_img, cv2.COLOR_BGRA2BGR)

    #     return orig_img

    def reset_my_socket(self):
        self.frame_socket.close()
        self.frame_socket = self.zmq_context.socket( zmq.REQ )
        self.frame_socket.connect("tcp://localhost:" + self.zmq_address)
        pass
        # self.imu_socket.unbind(self.zmq_imu_address)
        
        # url = self.imu_socket.LAST_ENDPOINT
        # self.imu_socket.unbind(url)
        # self.imu_socket.close()
        # self.imu_socket = self.zmq_context.socket(zmq.PUSH)
        # self.imu_socket.setsockopt(zmq.SNDHWM , 1)
        # self.imu_socket.bind(self.zmq_imu_address)
    
    # def get_frame(self):
    #     # Async Request of new rendered frame to the Simulation Engine
    #     raw_msg = self.frame_socket.recv()
    #     input_frame = gabriel_pb2.InputFrame()
    #     input_frame.ParseFromString(raw_msg)
    #     return input_frame.payloads[0]
    def get_frame(self):
        # Async Request of new rendered frame to the Simulation Engine
        # print("sending 0")
        self.frame_socket.send_string("0")
        # print("asking for frame")
        raw_msg = self.frame_socket.recv()
        # except:
        #     return None

        # print("done")
        if raw_msg == None:
            return None

        input_frame = gabriel_pb2.InputFrame()
        input_frame.ParseFromString(raw_msg)

        return input_frame.payloads[0]
    
    def send_imu(self, extras):
        #Async Reply to the IMU_data request from the Simulation Engine
        self.imu_socket.send(extras.SerializeToString())
        return


    def handle(self, input_frame):
        # Check Input
        if input_frame.payload_type != gabriel_pb2.PayloadType.IMAGE:
            status = gabriel_pb2.ResultWrapper.Status.WRONG_INPUT_FORMAT
            return cognitive_engine.create_result_wrapper(status)
        
        # Retrieve data sent by the client
        extras = cognitive_engine.unpack_extras(openrtist_pb2.Extras, input_frame)
        
        # Check if Scene info needs to be updated
        if extras.style == "?":
            self.sendStyle = True

        ratio = extras.screen_value.width / extras.screen_value.height
        # ratio = self.screen_h / self.screen_w
        if int(ratio * self.screen_h) != self.screen_w :
            self.screen_w = int(ratio * self.screen_h)
            print(f'-windowed={self.screen_w }x{self.screen_h}')
            self._resize_watermark()
            if self.phys_simulator != None:
                self.phys_simulator.kill()
                while self.phys_simulator.poll() is None:
                    time.sleep(0.1)
                self.reset_my_socket()
                ARGS = [f'exec Flex/bin/linux64/NvFlexDemoReleaseCUDA_x64 -vsycn=0 -windowed={self.screen_w }x{self.screen_h}']
                self.phys_simulator = subprocess.Popen(ARGS, shell=True, preexec_fn=os.setsid)

        if self.phys_simulator == None:
            print(f'-windowed={self.screen_w }x{self.screen_h}')
            ARGS = [f'exec Flex/bin/linux64/NvFlexDemoReleaseCUDA_x64 -vsycn=0 -windowed={self.screen_w }x{self.screen_h}']
            # ARGS = ['exec Flex/bin/linux64/NvFlexDemoReleaseCUDA_x64', '-vsycn=0']
            self.phys_simulator = subprocess.Popen(ARGS, shell=True, preexec_fn=os.setsid)
            # outs, errs = self.phys_simulator.communicate()
        
        # send imu data/get new rendered frame from/to the Physics simulation Engine
        self.send_imu(extras)
        img_data = self.process_image(None)

        while (img_data == None):
            img_data = self.process_image(None)


        # Serialize the result (protobuf)
        result = gabriel_pb2.ResultWrapper.Result()
        result.payload_type = gabriel_pb2.PayloadType.IMAGE
        result.payload = img_data

        extras = openrtist_pb2.Extras()
        if self.sendStyle:
            for k, v in self.scene_list:
                extras.style_list[k] = v
            self.sendStyle = False
            
        status = gabriel_pb2.ResultWrapper.Status.SUCCESS

        result_wrapper = cognitive_engine.create_result_wrapper(status)
        result_wrapper.results.append(result)
        result_wrapper.extras.Pack(extras)

        return result_wrapper

    def process_image(self, image):
        post_inference = self.inference(image)
        return post_inference

    def inference(self, image):
        return self.get_frame()
    
    def _resize_watermark(self):
        self.mrk_w = int(self.screen_w / 3.0 + 0.5)
        self.mrk_h = int(self.mrk_w * self.mrk_ratio + 0.5)

        self.mrk = cv2.resize(self.mrk, (self.mrk_w , self.mrk_h), interpolation=cv2.INTER_NEAREST)
        self.alpha = cv2.resize(self.alpha, (self.mrk_w , self.mrk_h), interpolation=cv2.INTER_NEAREST)
        
        self.mrk = np.expand_dims(self.mrk, axis=-1)
        self.alpha = np.expand_dims(self.alpha, axis=-1)


    def _apply_watermark(self, image):
        img_mrk = image[-self.mrk_h:, -self.mrk_w:]
        img_mrk = (1 - self.alpha) * img_mrk + self.alpha * self.mrk # Broad Casting
        image[-self.mrk_h:, -self.mrk_w:, :] = img_mrk
        return image