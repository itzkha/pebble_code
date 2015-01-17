
read.binary.smartdays <- function(file.name, endiannes) {
    timestamp_l <- list()
    xyz_l <- list()
    weights <- switch(endiannes, little=2^seq(0, 56, 8), big=rev(2^seq(0, 56, 8)))
    buffer.size <- 25
    sampling.period <- 40

    binary.file <- file(file.name, "rb")

    while (TRUE) {
        temp <- readBin(binary.file, integer(), 8, 1, signed=FALSE)
        if (length(temp) < 8) {
            break
        }
        temp <- sum(temp * weights)
        timestamp_l[[length(timestamp_l)+1]] <- temp
        
        temp <- readBin(binary.file, integer(), 3*buffer.size, 2, endian=endiannes)
        xyz_l[[length(xyz_l)+1]] <- matrix(temp, ncol=3, byrow=TRUE)
    }

    close(binary.file)

    timestamp <- do.call(c, timestamp_l)
    xyz <- do.call(rbind, xyz_l)
    timestamp.completed <- as.numeric(sapply(timestamp, function(x){seq(x, by=sampling.period, length.out=buffer.size)}))

    data.frame(timestamp=timestamp.completed, x=xyz[,1], y=xyz[,2], z=xyz[,3])
}

#--------------------------------------------------------------------------------------------------
setwd("/home/hector/Desktop/work/SmartDays/pebble_code/R/test_final_0")

file.pebble <- "pebbleAccel_5dcdd302b589c29f_20150109180110"
pebble <- read.binary.smartdays(file.pebble, "little")

file.phone <- "phoneAccel_5dcdd302b589c29f_20150109180110"
phone <- read.binary.smartdays(file.phone, "big")

labels <- read.table("labels_5dcdd302b589c29f_20150109180110", sep=",", header=FALSE)

#--------------------------------------------------------------------------------------------------
par(oma=c(0,0,0,0), mar=c(4,4,3,1), mfrow=c(3,1))
plot(pebble$timestamp, pebble$x, type="l", col="red", ylim=c(-4000, 4000))
lines(pebble$timestamp, pebble$y, col="green")
lines(pebble$timestamp, pebble$z, col="blue")
abline(v=labels[,2])

plot(phone$timestamp, phone$x, type="l", col="red", ylim=c(-4000, 4000))
lines(phone$timestamp, phone$y, col="green")
lines(phone$timestamp, phone$z, col="blue")
abline(v=labels[,2])

plot(pebble$timestamp, type="l")


