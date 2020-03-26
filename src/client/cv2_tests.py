#!/usr/bin/python

# ./pi_surveillance_testing2_from_change.py -c pi_surv_config.json -o /home/mdm/storage/proc_images

# import the necessary packages
#from pyimagesearch.tempimage import TempImage
######from pyimagesearch2.keyclipwriter import KeyClipWriter
#import dropbox
#from picamera.array import PiRGBArray
#from picamera import PiCamera
#from twilio.rest import Client
#from threading import Thread
import argparse
import warnings
import datetime
import imutils
import json
import time
import cv2
import os



org = cv2.imread('/home/mdm/Downloads/hello_world_org.jpg', 1) 
mod = cv2.imread('/home/mdm/Downloads/hello_world_mod.jpg', 1) 
added1 = cv2.add(org, mod) 
added2 = cv2.add(mod, org) 

added3 = org.copy() 

for i in range(300):
        added3 = cv2.addWeighted(org,0.5, added3,0.5,0) 

# cv2.imshow('org',org)
# cv2.waitKey(0)


# cv2.imshow('mod',mod)
# cv2.waitKey(0)


# cv2.imshow('added1',added1)
# cv2.waitKey(0)

# cv2.imshow('added2',added2)
# cv2.waitKey(0)

cv2.imshow('added3',added3)
cv2.waitKey(0)


cv2.destroyAllWindows()