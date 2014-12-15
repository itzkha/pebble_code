setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_matched_3")
filePebble <- file("testPebbleAccel", "rb")
timestamp_l <- list()
xyz_l <- list()
weights <- 2^seq(0, 56, 8)
buffer.size <- 25
sampling.period <- 40
phone.packet.size <- 8 + (3 * 2)


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

timestamp.pebble.ordered <- timestamp.ordered

timestamp.pebble <- as.numeric(sapply(timestamp.ordered, function(x){seq(x, by=sampling.period, length.out=buffer.size)})) - timestamp.ordered[1]
pebble <- data.frame(timestamp=timestamp.pebble, x=xyz[,1], y=xyz[,2], z=xyz[,3])

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_matched_3")
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
pdf("synchro_pebble_phone.pdf", width=14, height=8, paper="special")

par(oma=c(0,0,0,0), mar=c(4,4,3,1), mfrow=c(2,3))
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(6000, 10000), xlab="Timestamp [mS]", ylab="Acceleration [mg]", main="Beggining - Pebble")
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=6080, xpd=NA, lty=2)
abline(v=6780, xpd=NA, lty=2)
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(8645000, 8649000), xlab="Timestamp [mS]", ylab="Acceleration [mg]", main="After ~2h - Pebble")
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=8644925, xpd=NA, lty=2)
abline(v=8646145, xpd=NA, lty=2)
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(14262500, 14266500), xlab="Timestamp [mS]", ylab="Acceleration [mgr]", main="After ~4h - Pebble")
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=14262840, xpd=NA, lty=2)
abline(v=14264420, xpd=NA, lty=2)

plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(6000, 10000), xlab="Timestamp [mS]", ylab="Acceleration [mg]", main="Beggining - Phone")
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")
plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(8645000, 8649000), xlab="Timestamp [mS]", ylab="Acceleration [mg]", main="After ~2h - Phone")
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")
plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000), xlim=c(14262500, 14266500), xlab="Timestamp [mS]", ylab="Acceleration [mg]", main="After ~4h - Phone")
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")

dev.off()


par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,1))
boxplot(list(pebble=pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)], phone=(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)])), ylab="Time [ms]", main="Sampling period")
grid()

par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,2))
hist(pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)], main="Pebble sampling period")
hist(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)], main="Phone sampling period")


par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(2,1))
plot(pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)], type="l")
grid()
plot(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)], type="l")
grid()


cumsum.pebble <- cumsum(pebble$timestamp[-1] - pebble$timestamp[-length(pebble$timestamp)])
cumsum.phone <- cumsum(phone$timestamp[-1] - phone$timestamp[-length(phone$timestamp)])
par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,1))
plot(pebble$timestamp[-1], cumsum.pebble, type="l", xlim=c(0,100), ylim=c(0,100))
lines(phone$timestamp[-1], cumsum.phone, col="red")

par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,1))
plot(pebble$timestamp[-1], cumsum.pebble, type="l", xlim=c(16130000, 16150000), ylim=c(16130000, 16150000))
lines(phone$timestamp[-1], cumsum.phone, col="red")


par(oma=c(0,0,0,0), mar=c(3,3,1,1), mfrow=c(1,1))
plot(timestamp.pebble.ordered, timestamp.pebble.ordered, type="l")
lines(timestamp.phone.ordered, timestamp.phone.ordered, col="red")



