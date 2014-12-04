setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R")
filePebble <- file("testPebbleAccel", "rb")
timestamp_l <- list()
xyz_l <- list()
weights <- 2^seq(0, 56, 8)
buffer.size <- 25

while (TRUE) {
    temp <- readBin(filePebble, integer(), 8, 1, signed=FALSE)
    if (length(temp) < 8) {
        break
    }
    temp <- sum(temp * weights)
    timestamp_l[[length(timestamp_l)+1]] <- temp
    
    temp <- readBin(filePebble, integer(), 3*buffer.size, 2, endian="little")
    xyz_l[[length(xyz_l)+1]] <- matrix(temp, ncol=3, byrow=TRUE)
}

close(filePebble)

timestamp <- do.call(c, timestamp_l)

timestamp.order <- order(timestamp)
timestamp.ordered <- timestamp[timestamp.order]
xyz <- do.call(rbind, xyz_l[timestamp.order])


#plot(timestamp.ordered)
#plot(timestamp.ordered[-1] - timestamp.ordered[-length(timestamp.ordered)])
#boxplot(timestamp.ordered[-1] - timestamp.ordered[-length(timestamp.ordered)])

plot(xyz[,1], type="l", col="red", ylim=c(-4000, 4000))
lines(xyz[,2], col="green")
lines(xyz[,3], col="blue")

pebble <- list()
pebble[["timestamp"]] <- timestamp.ordered
pebble[["xyz"]] <- xyz

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R")
filePhone <- file("testPhoneAccel", "rb")
timestamp_l <- list()
xyz_l <- list()
weights <- rev(2^seq(0, 56, 8))

while (TRUE) {
    temp <- readBin(filePhone, integer(), 8, 1, signed=FALSE)
    if (length(temp) < 8) {
        break
    }
    temp <- sum(temp * weights)
    timestamp_l[[length(timestamp_l)+1]] <- temp
    
    temp <- readBin(filePhone, integer(), 3, 2, endian="big")
    xyz_l[[length(xyz_l)+1]] <- temp
}

close(filePhone)

timestamp <- do.call(c, timestamp_l)

timestamp.order <- order(timestamp)
timestamp.ordered <- timestamp[timestamp.order]

xyz <- do.call(rbind, xyz_l[timestamp.order])
#plot(timestamp.ordered)
#plot(timestamp.ordered[-1] - timestamp.ordered[-length(timestamp.ordered)])
#boxplot(timestamp.ordered[-1] - timestamp.ordered[-length(timestamp.ordered)])

plot(xyz[,1], type="l", col="red", ylim=c(-4000, 4000))
lines(xyz[,2], col="green")
lines(xyz[,3], col="blue")

phone <- list()
phone[["timestamp"]] <- timestamp.ordered
phone[["xyz"]] <- -xyz

#--------------------------------------------------------------------------------------------------
par(oma=c(0,0,0,0), mar=c(3,2,1,1), mfrow=c(2,1))
plot(pebble$xyz[,1], type="l", col="red", ylim=c(-4000, 4000))
lines(pebble$xyz[,2], col="green")
lines(pebble$xyz[,3], col="blue")

plot(phone$xyz[,1], type="l", col="red", ylim=c(-4000, 4000))
lines(phone$xyz[,2], col="green")
lines(phone$xyz[,3], col="blue")

plot(phone$timestamp, col="red", ylim=c(min(min(pebble$timestamp), min(phone$timestamp)), max(max(phone$timestamp), max(pebble$timestamp))))
points(pebble$timestamp, col="blue")


boxplot(list(pebble=(pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)])/buffer.size, phone=(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)])), ylab="Time [ms]", main="Sampling period")
grid()

par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,2))
hist((pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)])/buffer.size, main="Pebble sampling period")
hist((phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)]), main="Phone sampling period")



