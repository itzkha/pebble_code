setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_matched_1")
filePebble <- file("testPebbleAccel", "rb")
timestamp_l <- list()
xyz_l <- list()
weights <- rev(2^seq(0, 56, 8))
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
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_matched_1")
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

par(oma=c(0,0,0,0), mar=c(3,2,1,1), mfrow=c(2,4))
timestamp.pebble <- as.numeric(sapply(pebble$timestamp, function(x){seq(x, by=40, length.out=25)})) - pebble$timestamp[1]
plot(timestamp.pebble, pebble$xyz[,1], type="l", col="red", ylim=c(-4000, 2000), xlim=c(108000, 114000))
lines(timestamp.pebble, pebble$xyz[,2], col="green")
lines(timestamp.pebble, pebble$xyz[,3], col="blue")
abline(v=108350, xpd=NA)
plot(timestamp.pebble, pebble$xyz[,1], type="l", col="red", ylim=c(-1000, 2000), xlim=c(1460000, 1480000))
lines(timestamp.pebble, pebble$xyz[,2], col="green")
lines(timestamp.pebble, pebble$xyz[,3], col="blue")
abline(v=1465000, xpd=NA)
plot(timestamp.pebble, pebble$xyz[,1], type="l", col="red", ylim=c(-1000, 2000), xlim=c(1985000, 2005000))
lines(timestamp.pebble, pebble$xyz[,2], col="green")
lines(timestamp.pebble, pebble$xyz[,3], col="blue")
abline(v=1990600, xpd=NA)
plot(timestamp.pebble, pebble$xyz[,1], type="l", col="red", ylim=c(-4000, 2000), xlim=c(7105000, 7111000))
lines(timestamp.pebble, pebble$xyz[,2], col="green")
lines(timestamp.pebble, pebble$xyz[,3], col="blue")
abline(v=7106250, xpd=NA)
abline(v=7106730, xpd=NA)

timestamp.phone <- phone$timestamp - phone$timestamp[1]
plot(timestamp.phone, phone$xyz[,1], type="l", col="red", ylim=c(-4000, 2000), xlim=c(108000, 114000))
lines(timestamp.phone, phone$xyz[,2], col="green")
lines(timestamp.phone, phone$xyz[,3], col="blue")
plot(timestamp.phone, phone$xyz[,1], type="l", col="red", ylim=c(-1000, 2000), xlim=c(1460000, 1480000))
lines(timestamp.phone, phone$xyz[,2], col="green")
lines(timestamp.phone, phone$xyz[,3], col="blue")
plot(timestamp.phone, phone$xyz[,1], type="l", col="red", ylim=c(-1000, 2000), xlim=c(1985000, 2005000))
lines(timestamp.phone, phone$xyz[,2], col="green")
lines(timestamp.phone, phone$xyz[,3], col="blue")
plot(timestamp.phone, phone$xyz[,1], type="l", col="red", ylim=c(-4000, 2000), xlim=c(7105000, 7111000))
lines(timestamp.phone, phone$xyz[,2], col="green")
lines(timestamp.phone, phone$xyz[,3], col="blue")





boxplot(list(pebble=(pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)])/buffer.size, phone=(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)])), ylab="Time [ms]", main="Sampling period")
grid()

par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,2))
hist((pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)])/buffer.size, main="Pebble sampling period")
hist((phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)]), main="Phone sampling period")


par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(2,1))
plot((pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)])/25, type="l")
grid()
plot(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)], type="l")
grid()

par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,1))
plot(timestamp.pebble - timestamp.phone, type="l")
grid()

