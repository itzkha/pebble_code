setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_pebble_timestamp")
filePebble <- file("testPebbleAccel", "rb")
timestamp_l <- list()
xyz_l <- list()
weights <- 2^seq(0, 56, 8)
buffer.size <- 25
sampling.period <- 40
phone.packet.size <- 8 + 8 + (3 * 2)


while (TRUE) {
    temp1 <- readBin(filePebble, integer(), 8, 1, signed=FALSE)
    if (length(temp1) < 8) {
        break
    }
    temp1 <- sum(temp1 * weights)
    temp2 <- readBin(filePebble, integer(), 8, 1, signed=FALSE)
    if (length(temp2) < 8) {
        break
    }
    temp2 <- sum(temp2 * weights)
    timestamp_l[[length(timestamp_l)+1]] <- c(temp1, temp2)
    
    temp <- readBin(filePebble, integer(), 3*buffer.size, 2, endian="little")
    xyz_l[[length(xyz_l)+1]] <- matrix(temp, ncol=3, byrow=TRUE)
}

close(filePebble)

timestamp <- do.call(rbind, timestamp_l)

plot((timestamp[,1]-timestamp[1,1]) - (timestamp[,2]-timestamp[1,2]), type="l")

timestamp <- timestamp[,1]

timestamp.order <- order(timestamp)
timestamp.ordered <- timestamp[timestamp.order]
xyz <- do.call(rbind, xyz_l[timestamp.order])

timestamp.pebble.ordered <- timestamp.ordered

timestamp.pebble <- as.numeric(sapply(timestamp.ordered, function(x){seq(x, by=sampling.period, length.out=buffer.size)})) - timestamp.ordered[1]
pebble <- data.frame(timestamp=timestamp.pebble, x=xyz[,1], y=xyz[,2], z=xyz[,3])

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_pebble_timestamp")
filePhone <- file("testPhoneAccel", "rb")
timestamp_l <- list()
xyz_l <- list()
weights <- rev(2^seq(0, 56, 8))

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

phone <- data.frame(timestamp=timestamp-timestamp[1], x=-xyz[,1], y=-xyz[,2], z=-xyz[,3])

#--------------------------------------------------------------------------------------------------
par(oma=c(0,0,0,0), mar=c(4,4,3,1), mfrow=c(2,2))
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(4000, 9000))
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=3850, xpd=NA, lty=2)
abline(v=4660, xpd=NA, lty=2)
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(4776000, 4781000))
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=4776180, xpd=NA, lty=2)
abline(v=4777240, xpd=NA, lty=2)

plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(4000, 9000))
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")
plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(4776000, 4781000))
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")


