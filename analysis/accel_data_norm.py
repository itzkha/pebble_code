import glob, re, os, pdb, shutil
import time
from pymongo import *

import numpy as np

raw_data_directory = "/usr/itetnas01/data-ife-01/zazhu/smartdays/uploads/"
files = glob.glob(raw_data_directory+"*")
all_users = set()

for fname in files:
	all_users.add(fname.split("/")[-1])

client = MongoClient("localhost", 27017)
client.smartdays.authenticate('zack', 'smartadmin')

db = client.smartdays

def is_timestamp_valid(timestamp):
	'''The given timestamp should be between 1st Jan 2015(1420066800) and now.

	@timestamp: it is a list which should has length one.
	'''

	# Invalid format
	if len(timestamp) != 1:
		return False

	# Should not be in the future
	if timestamp[0]/1000.0 > time.time():
		return False

	# Should not be earlier than 1st Jan, 2015
	if(timestamp[0]/1000.0 < 1420066800):
		print("timestamp is smaller than Jan 1st")
		print(timestamp)
		return False

	return True

def is_accel_data_valid(accel_data):
	return not np.any(np.abs(accel_data)>4000)

def is_new_phone_format(phone_file):
	with open(phone_file, "rb") as infile:
		read_success, rec = read_packet(infile, "test", True) # Read with new format

		if read_success and rec != {}:
			print(phone_file + "-> new format")
			return True
		else: # Old format since the file was not read successfully
			print(phone_file + "-> old format")
			return False

def read_packet(infile, user, is_new_format):
	'''Read and consume 8 (timestamp) + 25*3*2 (25 triples of x,y,z, each of type short).
	Also check if the timestamp is valid and the accelerometer datas are between [-4k,+4k].

	Return the (boolean, dictionary) pair where the boolean is to indicate whether the read
	fails, and the dictionary is a record to be inserted to Mongo DB.
	There are the following scenarios:
		(true, dictionary): read succesfully, and the dictionary contains valid values
		(true, {}}): read succesfully, but the dictionary is empty since either the timestamp 
					 or accel data does not make sense.
		(false, {}): read failed. It means the end of file.
	'''
	# Read timestamp
	timestamp = np.fromfile(infile, dtype=">Q", count=1) #uint64_t in big endian

	# Check if end of file
	if len(timestamp) != 1:
		print ("packet read fail: timestamp returned []")
		return False, {}

	accel_data=[]
	try:
		if is_new_format:
			accel_data = np.fromfile(infile, dtype=">h", count=25*3).reshape(-1,3)  # 75 uint8_t in big endian
		else:
			accel_data = np.fromfile(infile, dtype=">h", count=3).reshape(-1,3)  # 3 uint8_t in big endian
	except ValueError:
		# End of the file and accel_data is []
		print ("packet read fail: accel_data returned []")
		return False, {}

	# Check validity of the data
	if not is_timestamp_valid(timestamp) or not is_accel_data_valid(accel_data):
		return True, {}

	# Form record
	rec = { 
		"user": user,
		"timestamp":float(timestamp[0]/1000.0), 
		"x":accel_data[:,0].tolist(),
		"y":accel_data[:,1].tolist(),
		"z":accel_data[:,2].tolist()
		}

	return True, rec

def stitch_phone_data(users = all_users):
	#TODO: remove these counters
	num_old_file = 0
	num_new_file = 0
	num_total_file = 0

	num_entries = 0

	for user in users:
		phone_files = glob.glob(raw_data_directory+user+"/phoneAccel_"+user+"*")

		print("User " + user + " has "+ str(len(phone_files)) + " phone files: ")

		for phone_file in phone_files:	

			records = []
			num_total_file+=1

			# Need to put it in to a local boolean because is_new_phone_format has side effect:
			# it opens the file and consumes the first packet of the data
			is_new_format=False
			if is_new_phone_format(phone_file):
				is_new_format=True

			# Reopen the file and start from the begining
			with open(phone_file, "rb") as infile:
				while True:
					if is_new_format:
						read_success, rec = read_packet(infile, user, True) # Read with new format
					else:
						read_success, rec = read_packet(infile, user, False) # Read with old format
					if not read_success:
						break

					if(rec == {}):
						continue

					records.append(rec)
					num_entries+=len(rec["x"])

				# Insert all records for this file
				if records == []:
					continue
				if is_new_format:
					num_new_file+=1
				else:
					num_old_file+=1

			#db.phone_accel.insert(records)
			
			# # Move the inserted documents into processed subfolder
			# directory = raw_data_directory+user+"/processed/"
			# if not os.path.exists(directory):
			# 	os.makedirs(directory)
			# shutil.move(phone_file, directory)

	print(num_new_file)
	print(num_old_file)
	print(num_total_file)

	print("In total we get this many entires:")
	print(num_entries)

def stitch_pebble_data(users = all_users):
	for user in users:
		print ("Processing user: %s" % user)

		pebble_files = glob.glob(raw_data_directory+user+"/pebbleAccel_"+user+"*")
		for pebble_file in pebble_files:
			with open(pebble_file, "rb") as infile:
				records = []
				print ("begin reading new file: " + str(infile))
				while True:

					read_success, rec = read_packet(infile, user, True)

					if not read_success:
						break

					if (rec == {}):
						continue
					records.append(rec)

				# go to next file if no records are in this one
				if len(records) == 0:
					continue

#				db.pebble_accel.insert(records)
				# # Move the inserted documents into processed subfolder
				# directory = raw_data_directory+user+"/processed/"
				# if not os.path.exists(directory):
				# 	os.makedirs(directory)
				# shutil.move(pebble_file, directory)

					# # Read data
					# timestamp = np.fromfile(infile, dtype="<Q", count=1) #uint64_t in little endian

					# if len(timestamp) != 1:
					# 	break

					# # TODO: modify filter to check between beginning of this year and time.time()
					# if is_timestamp_valid(timestamp): # error in file, break out
					# 	break

					# accel_data = np.fromfile(infile, dtype="<h", count=25*3).reshape(-1,3)  # 75 uint8_t in little endian

					# if np.any(np.abs(accel_data)>4000): # error in file, break out
					# 	break

					# # Form record
					# rec = { 
					# 	"user": user,
					# 	"timestamp":float(timestamp[0]/1000.0), 
					# 	"x":accel_data[:,0].tolist(),
					# 	"y":accel_data[:,1].tolist(),
					# 	"z":accel_data[:,2].tolist()
					# 	}
					# records.append(rec)

				# # Insert all records for this file
				# if records == []:
				# 	continue
				
			
				
				

if __name__ == "__main__":
	stitch_phone_data()