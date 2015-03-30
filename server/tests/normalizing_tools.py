__author__ = 'hector'

import paramiko
import os
import pandas as pd
import numpy as np
import sklearn.preprocessing
from datetime import datetime
import time

def download_files(user_code):
    ssh = paramiko.SSHClient()
    # automatically add keys without requiring human intervention
    ssh.set_missing_host_key_policy( paramiko.AutoAddPolicy() )
    ssh.connect("193.134.218.36", username="smartroot", password="dexterDEXTER0")

    base_command = "cd smartdays/server/uploads" + '/'+ user_code
    stdin, stdout, stderr = ssh.exec_command(base_command + "; du *")
    size_name = stdout.readlines()
    this_sizes = [f.split('\t')[0] for f in size_name]
    this_names = [f.split('\t')[1][:-1] for f in size_name]
    this_dates = [name.split('_')[2][0:8] for name in this_names]

    ssh.close()

    dates_unique = list(set([item for item in this_dates]))
    dates_unique.sort()

    paramiko.util.log_to_file("/home/hector/Desktop/work/SmartDays/pebble_code/server/tests/paramiko.log")

    if not os.path.exists("/media/hector/DATA/articleMoodZackData/" + user_code):
        os.makedirs("/media/hector/DATA/articleMoodZackData/" + user_code)

    # Open a transport
    transport = paramiko.Transport(("193.134.218.36", 22))
    # Auth
    transport.connect(username="smartroot", password="dexterDEXTER0")
    # Go!
    sftp = paramiko.SFTPClient.from_transport(transport)

    # Download
    base_dir_local = "/media/hector/DATA/articleMoodZackData/" + user_code + '/'
    base_dir_remote = "smartdays/server/uploads" + '/' + user_code + '/'
    for name in this_names:
        sftp.get(base_dir_remote + name, base_dir_local + name)

    # Close
    sftp.close()
    transport.close()


def correct_items(dataframe):
    dataframe["social"] = dataframe["social"].replace("alone", "ALONE")
    dataframe["social"] = dataframe["social"].replace("with others", "WITH_OTHERS")
    dataframe["label"] = dataframe["label"].replace("No Activity", "No activity")
    dataframe["label"] = dataframe["label"].replace("Socializing/Relaxing/Leisure", "Social/Leisure")
    dataframe["label"] = dataframe["label"].replace("Eating/Drinking", "Eat/Drink")
    dataframe["label"] = dataframe["label"].replace("Breakfast", "Eat/Drink")
    dataframe["label"] = dataframe["label"].replace("Car", "Commuting")
    dataframe["label"] = dataframe["label"].replace("Clean", "Household")
    dataframe["label"] = dataframe["label"].replace("Coffee", "Eat/Drink")
    dataframe["label"] = dataframe["label"].replace("Cook", "Household")
    dataframe["label"] = dataframe["label"].replace("Dinner", "Eat/Drink")
    dataframe["label"] = dataframe["label"].replace("Food", "Eat/Drink")
    dataframe["label"] = dataframe["label"].replace("Hygiene", "Personal care")
    dataframe["label"] = dataframe["label"].replace("Lunch", "Eat/Drink")
    dataframe["label"] = dataframe["label"].replace("Relax", "Social/Leisure")
    dataframe["label"] = dataframe["label"].replace("Rest", "Social/Leisure")
    dataframe["label"] = dataframe["label"].replace("Sleep", "Personal care")
    dataframe["label"] = dataframe["label"].replace("Sports", "Sports/Active")
    dataframe["label"] = dataframe["label"].replace("Walk", "Commuting")
    dataframe["label"] = dataframe["label"].replace("Work", "Working")
    return dataframe


def read_user_timeline(user_code):
    base_dir = "/media/hector/DATA/articleMoodZackData/" + user_code + '/'
    #base_dir = "/media/hector/LaCie/backup/articleMoodZackData/" + user_code + '/'
    this_names = os.listdir(base_dir)
    activities_all = []
    for name in this_names:
        if name.find("activity") >= 0:
            print "---> reading ", name
            if os.stat(base_dir + name).st_size == 0:
                print "ignoring " + name + " (empty)"
            else:
                this_timeline = pd.read_csv(base_dir + name, sep=',', na_values=["NA"])
                if len(this_timeline) <= 1:
                    print "ignoring " + name + " (single)"
                else:
                    if len(this_timeline.columns) > 3:
                        print "ignoring " + name + " (wrong format)"
                    else:
                        if len(this_timeline.columns) == 2:#wrong header
                            if this_timeline.index.dtype == "int64":#no social
                                temp1 = pd.DataFrame(data=this_timeline["timestamp"].values, index=range(len(this_timeline)), columns=["timestamp"])
                                temp2 = pd.DataFrame(data=["NaN"] * len(this_timeline), index=range(len(this_timeline)), columns=["social"])
                                temp3 = pd.DataFrame(data=this_timeline["label"].values, index=range(len(this_timeline)), columns=["label"])
                                this_timeline = pd.concat([temp1, temp2, temp3], axis=1)
                            else:
                                temp1 = pd.DataFrame(data=this_timeline["timestamp"].values, index=range(len(this_timeline)), columns=["timestamp"])
                                temp2 = pd.DataFrame(data=this_timeline["label"].values, index=range(len(this_timeline)), columns=["social"])
                                temp3 = pd.DataFrame(data=this_timeline.index.values, index=range(len(this_timeline)), columns=["label"])
                                this_timeline = pd.concat([temp1, temp2, temp3], axis=1)
                        date = pd.to_datetime(this_timeline["timestamp"].values, unit="ms").tz_localize("UTC").tz_convert("Europe/Berlin")
                        this_timeline = pd.concat([this_timeline, pd.DataFrame(data=date, index=range(len(this_timeline)), columns=["date"])], axis=1)
                        this_timeline = correct_items(this_timeline)
                        #ignore long start-stop ( > 1 hour )
                        if (len(this_timeline) == 2) and ((this_timeline["timestamp"][1] - this_timeline["timestamp"][0]) > (60*60*1000)):
                            print "ignoring " + name + " (too long)"
                        else:
                            print "appending " + name
                            activities_all.append(this_timeline)
    return activities_all


def create_activity_matrix(activity_list):
    activities_merged = pd.DataFrame(columns = ["timestamp", "social", "label", "date"])

    for this_timeline in activity_list:
        activities_merged = activities_merged.append(this_timeline, ignore_index=True)
    activities_merged = activities_merged.sort(columns=["timestamp"])

    lencoder = sklearn.preprocessing.LabelEncoder()
    lencoder.fit(np.unique(activities_merged["label"]))

    activities_index = lencoder.transform(activities_merged["label"])

    first_second_in_log = min(activities_merged["timestamp"]) / 1000
    last_second_in_log = max(activities_merged["timestamp"]) / 1000
    first_minute_datetime = datetime.fromtimestamp(first_second_in_log).strftime("%Y-%m-%d 00:00:00")
    last_minute_datetime = datetime.fromtimestamp(last_second_in_log).strftime("%Y-%m-%d 23:59:59")
    first_minute = time.mktime(datetime.strptime(first_minute_datetime, "%Y-%m-%d %H:%M:%S").timetuple()) / 60
    last_minute = time.mktime(datetime.strptime(last_minute_datetime, "%Y-%m-%d %H:%M:%S").timetuple()) / 60

    activity_matrix = np.zeros((int(last_minute - first_minute) + 1, max(activities_index)+1), dtype=np.int)
    for this_timeline in activity_list:
        minutes = np.round((this_timeline["timestamp"].values / (1000*60)) - first_minute)
        this_activities_index = lencoder.transform(this_timeline["label"])
        for i, (t, tnext) in enumerate(zip(minutes[:-1], minutes[1:])):
            if this_timeline["label"][i] != "No activity":
                activity_matrix[int(t):int(tnext), this_activities_index[i]] = True

    return (activity_matrix, np.unique(activities_merged["label"].values))

def decode_mood_line(line):
    to_return = pd.DataFrame(columns=["timestamp", "label"])
    start = 0
    comma = line.find(',', start)
    while comma >= 0:
        if (comma - start) < 3:    #to avoid NAs due to bad format
            start = comma + 1
        else:
            label = line[start:comma]
            comma += 1
            timestamp = line[comma:(comma+13)]
            if label != "Don't know" and label != "Sad":
                if label == "Positive":
                    label = "Happy"
                if label == "Anxious":
                    label = "Tense"
                if label == "Calm":
                    label = "Relaxed"
                to_return = pd.concat([to_return, pd.DataFrame([[timestamp, label]], columns=["timestamp", "label"])], ignore_index=True)
            start = comma + 13
        comma = line.find(',', start)
    return to_return

def read_user_mood(user_code):
    base_dir = "/media/hector/DATA/articleMoodZackData/" + user_code + '/'
    #base_dir = "/media/hector/LaCie/backup/articleMoodZackData/" + user_code + '/'
    this_names = os.listdir(base_dir)
    mood_all = pd.DataFrame(columns=["timestamp", "label"])
    for name in this_names:
        if name.find("mood") >= 0:
            print "---> reading ", name
            if os.stat(base_dir + name).st_size == 0:
                print "ignoring " + name + " (empty)"
            else:
                f = open(base_dir + name, 'r')
                f.readline()
                line = f.readline()
                if len(line) == 0:
                    print "ignoring " + name + " (empty)"
                else:
                    this_mood = decode_mood_line(line)
                    mood_all = pd.concat([mood_all, this_mood], ignore_index=True)
                f.close()
    return mood_all