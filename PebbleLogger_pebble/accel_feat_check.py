import csv
import numpy as np

parsed_y = []
parsed_z = []
with open("/Users/zackzhu/to_del.csv") as csvfile:
    myreader = csv.reader(csvfile,delimiter=",")
    for row in myreader:
	parsed_y.append(int(row[1]))
        parsed_z.append(int(row[2]))

print np.mean(parsed_y)
print np.var(parsed_y)
print np.mean(parsed_z)
print np.var(parsed_z)

