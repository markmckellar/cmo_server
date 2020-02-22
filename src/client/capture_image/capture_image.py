#!/usr/bin/python3

import sys 
import picamera
import time
import requests
import os
import socket       
import logging


#server = "http://192.168.1.169:8081"
#server = "http://96.126.98.96:8081"
server = "192.168.1.177:8080"
endPoint = "/uploadfile"
sleepTime=0.5
imageQuality=10
port=9080
logging.basicConfig(format='%(asctime)s : %(message)s',stream=sys.stdout,level=logging.DEBUG)

try :
	s = socket.socket()         # Create a socket object
	host = socket.gethostname() # Get local machine name
	s.bind((host, port)) 
except :
	logging.info("looks like a server is already running");
	exit()

piImageServer = server+endPoint
imageName = "backDoor.jpg"
haveCamera = False;

try :
	logging.info("getting camera resrouce")
	camera = picamera.PiCamera()
	camera.brightness = 60
	haveCamera = True;
	time.sleep(2)
except picamera.exc.PiCameraMMALError as err :
	logging.info("Error getting camera resource : {0}".format(err))
except :
	logging.info("Unexpected error:", sys.exc_info()[0])
	raise

while haveCamera :

	#################################################################
	# Get the image
	#################################################################
	imageCaptured = False
	try :
		time.sleep(1)
		logging.info("starting image capture")
		camera.capture(imageName,quality=imageQuality)
		#camera.start_preview()
		#camera.vflip = True
		#camera.hflip = True
		#camera.brightness = 60
		imageCaptrued = True
	except picamera.exc.PiCameraMMALError as err :
		logging.info("Error taking picture : {0}".format(err))
	except :
		logging.info("Unexpected error:", sys.exc_info()[0])
		raise


	#################################################################
	# Send the image
	#################################################################
	imageSent = False
	if imageCaptrued :
		try :
			logging.info("captured : " + imageName)
			logging.info("starting transmit : " + piImageServer)
			with open(imageName, 'rb') as f: 
				r = requests.post(piImageServer, files={imageName: f})
			imageSent = True;
			logging.info("transimited : " + imageName)
		except :
			logging.info("Unexpected error sending file:", sys.exc_info()[0])
			logging.info("sleeping for 60....")
			time.sleep(60)


	#################################################################
	# remove the image
	#################################################################
	if imageCaptrued :
		try :
			os.remove(imageName)
			logging.info("removed : " + imageName)
		except :
			logging.info("Unexpected error deleting file:", sys.exc_info()[0])
			raise

	#################################################################
	# sleep.........
	#################################################################
	logging.info("--- sleep -----------------------------------------------------")
	time.sleep(sleepTime)
