// callback function for location success
function locationSuccess(pos) {
    console.log('Got position:' + JSON.stringify(pos));
    position = pos;
    obtainedLoc = true;

    postLocalizedMarker(appMessageInput,pos);
    //navigator.geolocation.clearWatch(locationWatcher);

}

// callback function for location failed
function locationError(err) {
    console.log('location error (' + err.code + '): ' + err.message);
    obtainedLoc = false;
    postMarker(appMessageInput);
    navigator.geolocation.clearWatch(locationWatcher);
}

function postMarker(e) {
    var req = new XMLHttpRequest();
    req.open('POST', "https://actikeshi.appspot.com/marker", true);
    req.setRequestHeader("Content-type","application/x-www-form-urlencoded");
    req.onload = function (e) {
	// Notify pebble of server confirmation
	if (req.status == 200) {
	    Pebble.sendAppMessage({
		"serverConfirmed": 1
	    });
	} else {
	    Pebble.sendAppMessage({
		"serverConfirmed": 0
	    });
	}
	console.log("server confirmation: " +  req.status.toString());
    }

    // Pebble.sendAppMessage({
    // 	"gpsConfirmed": 0
    // });  
    // console.log("gps confirmation to pebble:" + obtainedLoc.toString());

    var sendString = "accountToken="+Pebble.getAccountToken()+"&markerContent="+JSON.stringify(e.payload);
    console.log("***" + sendString);
    req.send(sendString);
}
function postLocalizedMarker(e,pos) {
    var req = new XMLHttpRequest();
    req.open('POST', "https://actikeshi.appspot.com/marker", true);
    req.setRequestHeader("Content-type","application/x-www-form-urlencoded");
    req.onload = function (e) {
	if (req.status == 200) {
	    Pebble.sendAppMessage({
		"serverConfirmed": 1
	    });
	} else {
	    Pebble.sendAppMessage({
		"serverConfirmed": 0
	    });
	}
	console.log("server confirmation: " +  req.status.toString());
    }
    
    // Pebble.sendAppMessage({
    // 	"gpsConfirmed": 1
    // });  
    // console.log("gps confirmation to pebble:" + obtainedLoc.toString());
    
    var sendString = "accountToken="+Pebble.getAccountToken()+
	"&markerContent="+JSON.stringify(e.payload) + 
	"&gps="+JSON.stringify(position);
    
    console.log("****" + sendString);
    req.send(sendString);
}
var appMessageInput;

var locationOptions = { "timeout":10000, "maximumAge": 60000, "enableHighAccuracy": true }; 

var obtainedLoc = new Boolean();
var position;
var locationWatcher;

// Set callback for the app ready event
Pebble.addEventListener("ready",
                        function(e) {
                            console.log("connect!" + e.ready);
			   
                            console.log(e.type);
			  
                        });

Pebble.addEventListener("appmessage",
  function(e) {
      appMessageInput = e;
      navigator.geolocation.getCurrentPosition(locationSuccess, locationError, locationOptions); 
      //Everything proceeds according to locationSuccess/locationError functions
  }
);