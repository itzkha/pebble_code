import glob, re, os, pdb, shutil
import time
from pymongo import *

import numpy as np

raw_data_directory = "/usr/itetnas01/data-ife-01/zazhu/smartdays/uploads/"
files = glob.glob(raw_data_directory+"*")
all_users = set()

for fname in files:
	all_users.add(fname.split("/")[-1])

mood_categories = set(["Bored",
            "Excited",
            "Happy",
            "Relaxed",
            "Stressed",
            "Tense",
            "Tired",
            "Upset"])
activity_categories = set(["commuting",
            "eat/drink",
            "education",
            "household",
            "personal care",
            "prof. services",
            "shopping",
            "social/leisure",
            "sports/active",
            "working",
            "no activity"])

dict_old_act_to_new_act = {
	"Work":"working",
	"Eating/Drinking":"eat/drink",
	"Socializing/Relaxing/Leisure":"social/leisure",
	"Lunch":"eat/drink",
	"Breakfast":"eat/drink",
	"Coffee":"eat/drink",
	"Dinner":"eat/drink",
	"Hygiene":"personal care",
	"Clean":"household",
	"Walk":"commuting",
	"Rest":"social/leisure",
	"Car":"commuting",
	"Sports":"sports/active",
	"Sleeping":"personal care",
	"Relax":"social/leisure",
	"Cook":"household",
	"Sleep":"personal care",
	"Food":"eat/drink"
}

client = MongoClient("localhost", 27017)
client.smartdays.authenticate('zack', 'smartadmin')

db = client.smartdays

def write_act_line(act_start_time, act_end_time, act, is_social):
	if act_start_time is None or act_end_time is None or act is None or is_social is None:
		print("Incorrect write!!")
		return
	print ("Writing line: %s" % ",".join([act_start_time, act_end_time, is_social, act]))

def extract_social(field):
	if("others" in field.lower()):
		return "WITH_OTHERS"
	if("alone" in field.lower()):
		return "ALONE"

	if(field.lower() == "na" or field.lower() == "unknown"):
		return "UNKNOWN"

	#print("New field name for field found: " + field)
	return None

def initiate_act_line(line_items):
	act_start_time = line_items[-1] #set beginning of next act
	act = line_items[0]
	if len(line_items) == 3:
		is_social = extract_social(line_items[1])
	else:
		is_social = "UNKNOWN"

	return act_start_time, act, is_social

def make_activity_record(user,act_start_time, act_end_time, is_social, act):
	return { 
			"user": user,
			"start": float(act_start_time)/1000,
			"end":float(act_end_time)/1000, 
			"social":is_social,
			"activity":act,
			}

def make_mood_record(user,label, timestamp):
	return { 
			"user": user,
			"mood": label,
			"timestamp":float(timestamp)/1000,
			}

def make_location_record(user,timestamp,latitute,longitude,altitute,accuracy,provider):
	return { 
			"user": user,
			"loc":{"type": "Point", "coordinates": [float(longitude), float(latitute)]},#, altitute]},
			"timestamp": float(timestamp)/1000,
			"accuracy": float(accuracy),
			"provider": provider,
			}

def standardize_act_line(line_items):
	# Ordering should be: act, social, timestamp
	act = None
	social = None
	timestamp = None

	for i,item in enumerate(line_items):

		if i > 2: # discard rest if line_items is larger than 3 items
			continue

		if item.lower() in activity_categories:
			act = item.lower()
		elif item in dict_old_act_to_new_act:
			act = dict_old_act_to_new_act[item]

		if re.match(r"^-?[0-9]+$",item) is not None:
			timestamp = item

		if social is None: # if social has not been set already
			social = extract_social(item)

	if act is None or timestamp is None:
		print ("Invalid line: one of the fields in " + str(line_items) +" is None.")
		return ()		

	if social is None:
		social = "UNKNOWN"

	return (act,social,timestamp)



def stitch_text_data(users = all_users):
	# ### STITCH THE FILES TOGETHER BY USER AND PLACE IN APPROPRIATE FOLDER ###
	for user in users:
		print ("Processing user: %s" % user)
		activity_records = []	
		mood_records = []
		location_records = []
		num_error_lines = 0

		with open(raw_data_directory+user+"/activity.csv", "w") as outfile:

			# Write one header
			outfile.write("start,stop,social,label\n")

			activity_files = glob.glob(raw_data_directory+user+"/activity_"+user+"*")
			for activity_file in activity_files:
				with open(activity_file, "r") as infile:

					file_lines = infile.readlines()
					n_lines = len(file_lines)

					act_start_time = None
					act_end_time = None
					act = None
					is_social = None

					for i,line in enumerate(file_lines):
						if i==0:
							continue
						line_items = line.strip().split(",")
						
						# Process line to standardize token ordering
						# Ordering becomes: act, social, timestamp
						line_items = standardize_act_line(line_items) 
						

						# if line_items[0].lower() not in activity_categories:
						# 	print ("Skipping label line: %s" % line)
						# 	act_start_time = None
						# 	act_end_time = None
						# 	act = None
						# 	is_social = None
						# 	continue
						if len(line_items) == 0:
							num_error_lines+=1
							continue

						if line_items[0].lower() != "no activity":
							if act_start_time is not None:
								act_end_time = str(int(line_items[-1])-1) #act already set, close it up
								outfile.write(",".join([act_start_time, act_end_time, is_social, act])+"\n")
								activity_records.append(make_activity_record(user=user,
																			 act_start_time=act_start_time,
																			 act_end_time=act_end_time,
																			 is_social=is_social,
																			 act=act))
								
								# reset after write
								act_start_time = None
								act_end_time = None
								act = None
								is_social = None

								act_start_time, act, is_social = initiate_act_line(line_items)
							else: 
								act_start_time, act, is_social = initiate_act_line(line_items) # act has not been set, set it				
								continue
						else: # If No Activity is detected...

							if act != None: #... check if an activity is already set
								act_end_time = line_items[-1] #act already set, close it up
								outfile.write(",".join([act_start_time, act_end_time, is_social, act])+"\n")
								activity_records.append(make_activity_record(user=user,
																			 act_start_time=act_start_time,
																			 act_end_time=act_end_time,
																			 is_social=is_social,
																			 act=act))
								
								# reset after write
								act_start_time = None
								act_end_time = None
								act = None
								is_social = None

							else: #... if activity not set and no activity is detected, reset and continue
								act_start_time = None
								act_end_time = None
								act = None
								is_social = None
								continue

						# # check if we have all required items for a line
					
						# print ("start_time: %s end_time: %s act: %s is_social: %s" % (act_start_time, act_end_time, act, is_social))
						# if act_start_time != None and act_end_time != None and is_social != None and act != None:
							
				

						
		with open(raw_data_directory+user+"/mood.csv", "w") as outfile:
			outfile.write("label,timestamp\n")

			mood_files = glob.glob(raw_data_directory+user+"/mood_"+user+"*")
			for mood_file in mood_files:
				with open(mood_file, "r") as infile:
					t = infile.read()
					
					t = t.replace("label,timestamp\n","")
					t = t.replace("\n","")

					mood_labels = re.findall("[A-Z ']*,[0-9]{13}",t, flags=re.IGNORECASE)
					for mood_label in mood_labels:
						if mood_label.split(",")[0] not in mood_categories:
							continue
						outfile.write(mood_label+"\n")
						mood_records.append(make_mood_record(user=user,label=mood_label.split(",")[0],timestamp=mood_label.split(",")[1]))
							
							
		
		with open(raw_data_directory+user+"/location.csv", "w+") as outfile:
			outfile.write("timestamp,latitute,longitude,altitute,accuracy,provider\n")
			loc_files = glob.glob(raw_data_directory+user+"/location_"+user+"*")

			for loc_file in loc_files:
				with open(loc_file) as infile:
					previous_line = ""
					for i, line in enumerate(infile):
						if "NA" in line: #Remove lines with NA
							continue
						if "timestamp,latitute,longitude,altitute,accuracy,provider" in line: # remove unnecessary headers
							continue

						# skip if the previous line is the same
						if previous_line.split(",")[1:] == line.split(",")[1:]: # 
							continue
						previous_line = line

						outfile.write(line)
						tokens = line.strip().split(",")
						location_records.append(make_location_record(user=user,
																	 timestamp=tokens[0],
																	 latitute=tokens[1],
																	 longitude=tokens[2],
																	 altitute=tokens[3],
																	 accuracy=tokens[4],
																	 provider=tokens[5]))
		print ("user:" + user)
		print( "Location records:%d, activity labels: %d, mood labels: %d" %
			(len(location_records), len(activity_records), len(mood_records)))
		print ("Number of error lines: %d " % num_error_lines)

		
		# Write all to mongo db per user.
		if len(activity_records) > 0:
			db.activity.insert(activity_records)	

		if len(mood_records) > 0:
			db.mood.insert(mood_records)

		if len(location_records) > 0:
			db.location.insert(location_records)
			

if __name__ == "__main__":
	stitch_text_data()