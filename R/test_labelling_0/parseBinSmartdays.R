setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_labelling_0")
filePebble <- file("testPebbleAccel", "rb")
timestamp_l <- list()
xyz_l <- list()
weights <- 2^seq(0, 56, 8)
buffer.size <- 25
sampling.period <- 40


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

#timestamp.order <- order(timestamp)
timestamp.order <- 1:length(timestamp)
timestamp.ordered <- timestamp[timestamp.order]
xyz <- do.call(rbind, xyz_l[timestamp.order])

timestamp.pebble <- as.numeric(sapply(timestamp.ordered, function(x){seq(x, by=sampling.period, length.out=buffer.size)}))
#timestamp.pebble <- as.numeric(sapply(timestamp.ordered, function(x){seq(x, by=sampling.period, length.out=buffer.size)}))
pebble <- data.frame(timestamp=timestamp.pebble, x=xyz[,1], y=xyz[,2], z=xyz[,3])

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_labelling_0")
filePhone <- file("testPhoneSyncedAccel", "rb")
#filePhone <- file("testPhoneAccel", "rb")
weights <- rev(2^seq(0, 56, 8))
phone.packet.size <- 8 + (3 * 2)

all.bytes <- NULL
while (TRUE) {
    temp <- readBin(filePhone, integer(), 10000*phone.packet.size, 1, signed=FALSE)
    all.bytes <- c(all.bytes, temp)
    if (length(temp) < 10000*phone.packet.size) {
        break
    }
}

close(filePhone)

all.bytes <- matrix(all.bytes, nrow=phone.packet.size)
timestamp <- colSums(all.bytes[1:8,] * weights)
timestamp.phone.ordered <- timestamp
xyz <- cbind(colSums(all.bytes[9:10,] * c(256, 1)), colSums(all.bytes[11:12,] * c(256, 1)), colSums(all.bytes[13:14,] * c(256, 1)))
xyz <- xyz - ((2^16) * (xyz > (2^15)))

phone <- data.frame(timestamp=timestamp, x=-xyz[,1], y=-xyz[,2], z=-xyz[,3])
#phone <- data.frame(timestamp=timestamp, x=-xyz[,1], y=-xyz[,2], z=-xyz[,3])

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_labelling_0")
labels <- read.table("testLabels", sep=",", header=FALSE)

#--------------------------------------------------------------------------------------------------
par(oma=c(0,0,0,0), mar=c(4,4,3,1), mfrow=c(2,1))
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000))
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=labels[,2])

plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000))
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")
abline(v=labels[,2])

