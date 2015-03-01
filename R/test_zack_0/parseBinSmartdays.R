setwd("/home/hector/Desktop/work/pebble_code/R/test_zack_0")
filePebble <- file("pebbleAccel_5dcdd302b589c29f_20150301124841.bin", "rb")
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
pebble <- data.frame(timestamp=timestamp.pebble, x=xyz[,1], y=xyz[,2], z=xyz[,3])

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/pebble_code/R/test_zack_0")
filePhone <- file("phoneAccel_5dcdd302b589c29f_20150301124841.bin", "rb")
timestamp_l <- list()
xyz_l <- list()
weights <- rev(2^seq(0, 56, 8))
buffer.size <- 25
sampling.period <- 40


while (TRUE) {
    temp <- readBin(filePhone, integer(), 8, 1, signed=FALSE)
    if (length(temp) < 8) {
        break
    }
    temp <- sum(temp * weights)
    timestamp_l[[length(timestamp_l)+1]] <- temp
    
    temp <- readBin(filePhone, integer(), 3*buffer.size, 2, endian="big")
    xyz_l[[length(xyz_l)+1]] <- matrix(temp, ncol=3, byrow=TRUE)
}

close(filePhone)

timestamp <- do.call(c, timestamp_l)

#timestamp.order <- order(timestamp)
timestamp.order <- 1:length(timestamp)
timestamp.ordered <- timestamp[timestamp.order]
xyz <- do.call(rbind, xyz_l[timestamp.order])

timestamp.phone <- as.numeric(sapply(timestamp.ordered, function(x){seq(x, by=sampling.period, length.out=buffer.size)}))
phone <- data.frame(timestamp=timestamp.phone, x=xyz[,1], y=xyz[,2], z=xyz[,3])

#--------------------------------------------------------------------------------------------------
par(oma=c(0,0,0,0), mar=c(4,4,3,1), mfrow=c(2,1))
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000))
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")

plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000))
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")



par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,1))
boxplot(list(pebble=pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)], phone=phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)]), ylab="Time [ms]", main="Sampling period")
grid()

par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,2))
hist(pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)], main="Pebble sampling period")
hist(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)], main="Phone sampling period")




