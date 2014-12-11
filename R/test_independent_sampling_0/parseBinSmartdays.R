setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_independent_sampling_0")
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


pebble <- list()
pebble[["timestamp"]] <- timestamp.ordered
pebble[["xyz"]] <- xyz

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_independent_sampling_0")
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

phone <- list()
phone[["timestamp"]] <- timestamp.ordered
phone[["xyz"]] <- -xyz

#--------------------------------------------------------------------------------------------------

par(oma=c(0,0,0,0), mar=c(3,2,1,1), mfrow=c(2,2))
timestamp.pebble <- as.numeric(sapply(pebble$timestamp, function(x){seq(x, by=40, length.out=25)})) - pebble$timestamp[1]
plot(timestamp.pebble, pebble$xyz[,1], type="l", col="red", ylim=c(-4000, 4000), xlim=c(40000, 70000))
lines(timestamp.pebble, pebble$xyz[,2], col="green")
lines(timestamp.pebble, pebble$xyz[,3], col="blue")
abline(v=58850, lty=2, xpd=NA)
plot(timestamp.pebble, pebble$xyz[,1], type="l", col="red", ylim=c(-4000, 4000), xlim=c(1950000, 1980000))
lines(timestamp.pebble, pebble$xyz[,2], col="green")
lines(timestamp.pebble, pebble$xyz[,3], col="blue")
abline(v=1977000, lty=2, xpd=NA)

timestamp.phone <- phone$timestamp - phone$timestamp[1] + 270
plot(timestamp.phone, phone$xyz[,1], type="l", col="red", ylim=c(-4000, 4000), xlim=c(40000, 70000))
lines(timestamp.phone, phone$xyz[,2], col="green")
lines(timestamp.phone, phone$xyz[,3], col="blue")
plot(timestamp.phone, phone$xyz[,1], type="l", col="red", ylim=c(-4000, 4000), xlim=c(1950000, 1980000))
lines(timestamp.phone, phone$xyz[,2], col="green")
lines(timestamp.phone, phone$xyz[,3], col="blue")



par(oma=c(0,0,0,0), mar=c(3,2,1,1), mfrow=c(1,1))
boxplot(list(pebble=(timestamp.pebble[-1] - timestamp.pebble[-length(timestamp.pebble)]), phone=(timestamp.phone[-1] - timestamp.phone[-length(timestamp.phone)])))
grid()

par(oma=c(0,0,0,0), mar=c(3,2,1,1), mfrow=c(1,2))
hist(timestamp.pebble[-1] - timestamp.pebble[-length(timestamp.pebble)])
hist(timestamp.phone[-1] - timestamp.phone[-length(timestamp.phone)])

par(oma=c(0,0,0,0), mar=c(3,2,1,1), mfrow=c(1,2))
plot(timestamp.pebble, type="l")
plot(timestamp.phone, type="l")


