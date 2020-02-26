#!/usr/bin/python3

# inspired from https://www.pyimagesearch.com/2015/06/01/home-surveillance-and-motion-detection-with-the-raspberry-pi-python-and-opencv/
#

import os
import sys
import logging
import cv2


logging.basicConfig(format='%(asctime)s : %(message)s',stream=sys.stdout,level=logging.DEBUG)


relevant_path = sys.argv[1]
included_extensions = ['jpg','jpeg', 'bmp', 'png', 'gif']
file_names = [fn for fn in os.listdir(relevant_path)
              if any(fn.endswith(ext) for ext in included_extensions)]


file_names.sort()

counter = 0
for file_name in file_names: 
	full_file_name = relevant_path+file_name
	file_size = (os.path.getsize(full_file_name))
	print("working on "+full_file_name+" size is "+str(file_size)+" elemnt "+str(counter))#+" of "+len(file_names.size))

	# resize the frame, convert it to grayscale, and blur it
	image = cv2.imread(full_file_name)
	gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
	gray = cv2.GaussianBlur(gray, (21, 21), 0)
	counter = counter + 1