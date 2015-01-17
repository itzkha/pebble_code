setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_unmatched_2")
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

timestamp.pebble <- as.numeric(sapply(timestamp.ordered, function(x){seq(x, by=sampling.period, length.out=buffer.size)})) - timestamp.ordered[1]
pebble <- data.frame(timestamp=timestamp.pebble, x=xyz[,1], y=xyz[,2], z=xyz[,3])

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_unmatched_2")
filePhone <- file("testPhoneAccel", "rb")
timestamp_l <- list()
xyz_l <- list()
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

phone <- data.frame(timestamp=timestamp-timestamp[1], x=-xyz[,1], y=-xyz[,2], z=-xyz[,3])

#--------------------------------------------------------------------------------------------------
par(oma=c(0,0,0,0), mar=c(4,4,3,1), mfrow=c(2,2))
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(4500, 8500))
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=4910, xpd=NA, lty=2)
abline(v=6430, xpd=NA, lty=2)
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(3035000, 3039000))
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=3036020, xpd=NA, lty=2)
abline(v=3037000, xpd=NA, lty=2)

plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(4500, 8500))
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")
plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(3035000, 3039000))
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")


par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,1))
boxplot(list(pebble=pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)], phone=phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)]), ylab="Time [ms]", main="Sampling period")
grid()

par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,2))
hist(pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)], main="Pebble sampling period")
hist(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)], main="Phone sampling period")


par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(2,1))
plot(pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)], type="l", xlim=c(16400, 16800), ylim=c(0, 50))
grid()
plot(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)], type="l")
grid()

plot(16600:16700, pebble$timestamp[16600:16700], type="l")



