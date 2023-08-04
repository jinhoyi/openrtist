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
from time import time
import zmq
import subprocess
import sys
from easyprocess import EasyProcess
from pyvirtualdisplay.smartdisplay import SmartDisplay
import time
import multiprocessing
from threading import Thread, RLock, Event, Condition
import threading
import sys

logger = logging.getLogger(__name__)

import http.client, urllib.request, urllib.parse, urllib.error, base64


class OpenfluidEngine(cognitive_engine.Engine):
    SOURCE_NAME = "openfluid"
    REQUEST_TIMEOUT = 3000
    
    instances = []

    scene_list = {"00":"Rock Pool",
                  "01":"Pot Pourri",
                  "02":"Viscosity Low",
                  "03":"Viscosity Med",
                  "04":"Viscosity High",
                  "05":"Buoyancy",
                  "06":"Surface Tension Low",
                  "07":"Surface Tension Med",
                  "08":"Surface Tension High",
                  "09":"DamBreak",
                  "10":"Fluid Block"
    }

    monitor_stop = False

    def __init__(self, compression_params, args_engine = None, timeout = 30):
        OpenfluidEngine.instances.append(self)
        self.lock = threading.RLock()
        if args_engine == None:
            args_engine = {'frame_port': '5559', 'imu_port': '5560'}
        
        self.zmq_address = args_engine['frame_port']
        self.zmq_imu_address = args_engine['imu_port']

        # Initialize ZeroMQ Context and Socket
        self.zmq_context = zmq.Context()

        self.frame_socket = self.zmq_context.socket(zmq.REQ)
        self.frame_socket.connect("tcp://localhost:" + self.zmq_address)

        self.imu_socket = self.zmq_context.socket(zmq.PUSH)
        self.imu_socket.setsockopt(zmq.SNDHWM , 1)
        self.imu_socket.connect("tcp://localhost:" + self.zmq_imu_address)

        # IMU sensor Data
        self.x = 0
        self.y = 0
        self.z = 0

        # Initialize Screen Resolution of the client
        self.screen_w = 480
        # self.screen_h = 1080
        self.screen_h = 640
        # self.screen_ratio = self.screen_w / self.screen_h
        self.screen_ratio = self.screen_h / self.screen_w
        self.vsync = 0

        # Pointer to the Physics Simulation Engine Process
        self.phys_simulator = None
        self.activity_monitor = None

        # Scene List retreived
        self.sendStyle = True
        self.client_event = threading.Event()        

        # Initialize simulation engine and Client activity monitor
        self.start_sim()
        self.monitor_stop = False
        self.last_user_call = time.time()
        self.activity_monitor = threading.Thread(target = self.client_activity_monitor, args=(timeout, ))
        self.activity_monitor.start()

        self.latency_return = False
        self.server_fps = 0

        logger.info("FINISHED INITIALISATION")
        
    def release(self):
        print("Gracefully Terminating OpenfluidEngine")
        
        self.monitor_stop = True
        if self.activity_monitor != None:
            self.client_event.set()
            self.activity_monitor.join()
        print("time Monitor killed")
        
        self.terminate_sim()
        print("terminatd backend")

        
    # Turn off the simulation engine when no client is detected. 
    def client_activity_monitor(self, timeout=30):
        while not self.monitor_stop:
            if self.client_event.wait(timeout = timeout):
                self.client_event.clear()
            else:
                print("Client Inactive, terminating the simulation engine")
                with self.lock:
                    self.terminate_sim()
                self.client_event.wait()
                self.client_event.clear()
    
    def get_scenes(self):
        print("Updating scenes... sending request")
        self.frame_socket.send_string("1")

        reply = None
        while True:
            if (self.frame_socket.poll(self.REQUEST_TIMEOUT) & zmq.POLLIN) != 0:
                reply = self.frame_socket.recv()
                break
            
            print("No response from server")
            with self.lock:
                self.reset_simulator()

            print("\nResening Scene request")
            self.frame_socket.send_string("1")


        extras = openrtist_pb2.Extras()
        extras.ParseFromString(reply)
        self.scene_list = dict()
        for key in extras.style_list:
            self.scene_list[key] = extras.style_list[key]
        print("Got Scene reply. Scene-lists Updated")

    def terminate_sim(self):
        with self.lock:
            if self.phys_simulator != None:
                print("terminating...")
                self.phys_simulator.kill()
                
                while self.phys_simulator.poll() is None:
                    time.sleep(0.1)
                self.phys_simulator = None

        # if self.frame_socket != None:
        #     self.frame_socket.setsockopt(zmq.LINGER, 0)
        #     self.frame_socket.close()
        
        # if self.imu_socket != None:
        #     self.imu_socket.setsockopt(zmq.LINGER, 0)
        #     self.imu_socket.close()

    def start_sim(self):
        print("New Simulator")
        self.frame_socket = self.zmq_context.socket( zmq.REQ )
        self.frame_socket.connect("tcp://localhost:" + self.zmq_address)

        self.imu_socket = self.zmq_context.socket( zmq.PUSH )        
        self.imu_socket.setsockopt(zmq.SNDHWM , 1)
        self.imu_socket.connect("tcp://localhost:" + self.zmq_imu_address)


        with self.lock:
            ARGS = [f'exec Flex/bin/linux64/NvFlexDemoReleaseCUDA_x64 -vsync={self.vsync} -windowed={self.screen_w }x{self.screen_h}']
            
            print("New Simulator starting...")
            self.phys_simulator = subprocess.Popen(ARGS, shell=True, start_new_session=True)
        self.get_scenes()

    def reset_simulator(self):
        self.terminate_sim()
        self.start_sim()
    
    def get_frame(self):
        # Request of new rendered frame to the Simulation Engine
        self.frame_socket.send_string("0")
        reply = None
        while True:
            if (self.frame_socket.poll(self.REQUEST_TIMEOUT) & zmq.POLLIN) != 0:
                reply = self.frame_socket.recv()
                break
            
            print("No response from the Simulation Engine, restarting it")
            with self.lock:
                self.reset_simulator()
            self.frame_socket.send_string("0")
            
        if reply == None:
            return None

        input_frame = gabriel_pb2.InputFrame()
        input_frame.ParseFromString(reply)
        
        try:
            extras = cognitive_engine.unpack_extras(openrtist_pb2.Extras, input_frame)
            if (extras.latency_token == True):
                self.latency_return = True
            else:
                self.latency_return = False
            self.server_fps = extras.fps
        except:
            print("Exception")
            self.latency_return = False

        return input_frame.payloads[0]
    
    def send_imu(self, extras):
        #Push IMU_data to the Simulation Engine
        self.imu_socket.send(extras.SerializeToString())
        return

    def handle(self, input_frame):
        
        self.client_event.set()

        # Check Input
        if input_frame.payload_type != gabriel_pb2.PayloadType.IMAGE:
            status = gabriel_pb2.ResultWrapper.Status.WRONG_INPUT_FORMAT
            return cognitive_engine.create_result_wrapper(status)
        
        # Retrieve data sent by the client
        extras = cognitive_engine.unpack_extras(openrtist_pb2.Extras, input_frame)
        
        # Check if Scene info needs to be updated
        if extras.setting_value.scene == -1:
            self.sendStyle = True

        
        with self.lock:
            if (self.screen_ratio != extras.screen_value.ratio) or (self.screen_w != extras.screen_value.resolution) or (extras.fps != self.vsync):
                print(self.screen_ratio, extras.screen_value.ratio)
                self.screen_ratio = extras.screen_value.ratio
                self.screen_w = extras.screen_value.resolution
                self.screen_h = int(self.screen_ratio * self.screen_w + 0.5)
                self.vsync = extras.fps
                # print(extras.fps)
                print(f'-windowed={self.screen_w }x{self.screen_h} reset')
                self.reset_simulator()

        with self.lock:
            if self.phys_simulator == None:
                print(f'-windowed={self.screen_w }x{self.screen_h} new')
                self.start_sim()
        
        # send imu data/get new rendered frame from/to the Physics simulation Engine
        self.send_imu(extras)
        img_data = self.process_image(None)

        # Serialize the result (protobuf)
        result = gabriel_pb2.ResultWrapper.Result()
        result.payload_type = gabriel_pb2.PayloadType.IMAGE
        result.payload = img_data

        extras = openrtist_pb2.Extras()
        if self.sendStyle:
            for k, v in self.scene_list.items():
                extras.style_list[k] = v
            self.sendStyle = False

        if self.latency_return:
            extras.latency_token = True
            self.latency_return = False
        
        extras.fps = self.server_fps
            
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