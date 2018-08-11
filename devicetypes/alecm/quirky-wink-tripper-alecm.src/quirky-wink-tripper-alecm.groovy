/**
 *  Quirky/Wink Tripper Contact Sensor
 *
 *  Copyright 2015 Mitch Pond
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * 2016-17  AlecM - Minor tweak I updated this driver by by Mitch Pond to have different values for battery percentages 
 * and fix a couple of typos in lines around 210
 * 02-01-2018 AlecM - changed namespace to "alecm" to enable github repo sync and cleaned out some testing code
 * 08-11-2018 AlecM - Used code copied or adapted from 
 			https://github.com/bspranger/Xiaomi/tree/master/devicetypes/bspranger/xiaomi-door-window-sensor.src to 
 			-   Report "Last Opened" as secondary controller - modified from 
 			-   Shift battery from secondary control to new tile with Xiaomi groups battery icon
                        -   Add preferences for date format (US - Month/Date/Year or UK Date/Month/Year)
                        -   Add preferences for 12-hour clock or 24 hour clock
                        -   Battery icon created by the bspranger Xiaomi group used with permission by the group- originally at 
                        "https://raw.githubusercontent.com/bspranger/Xiaomi/master/images/XiaomiBattery.png"
 *			
 *
 */

metadata {
	definition (name: "Quirky Wink Tripper AlecM V3", namespace: "alecm", author: "Mitch Pond") {
    
		capability "Contact Sensor"
		capability "Battery"
		capability "Configuration"
		capability "Sensor"
		capability "Tamper Alert"
    
		command "configure"
		command "resetTamper"
		command "testTamper"
   
   		attribute "lastOpened", "String"
   		attribute "lastOpenedDate", "Date" 
        
		fingerprint endpointId: "01", profileId: "0104", deviceId: "0402", inClusters: "0000,0001,0003,0500,0020,0B05", outClusters: "0003,0019", manufacturer: "Sercomm Corp.", model: "Tripper"
	}

	// UI tile definitions
	tiles(scale: 2) {
    	multiAttributeTile(name:"richcontact", type: "generic", width: 6, height: 4) {
        	tileAttribute("device.contact", key: "PRIMARY_CONTROL") {
            	attributeState "open", label: '${name}', icon:"st.contact.contact.open", backgroundColor:"#ffa81e"
                attributeState "closed", label: '${name}', icon:"st.contact.contact.closed", backgroundColor:"#79b821"
            }
            

            //tileAttribute("device.battery", key: "SECONDARY_CONTROL") {
            ///	attributeState "battery", label:'${currentValue} % battery', unit:""
               tileAttribute("device.lastOpened", key: "SECONDARY_CONTROL") {
              attributeState("default", label:'Last Opened: ${currentValue}')

            }
        }
	}
        
		standardTile("tamper", "device.tamper", decoration: "flat", width:2, height: 2) {
			state "clear", label: "Clear", icon: "st.security.alarm.on", backgroundColor:"#79b821"
			state "detected", label: "Tamper Detected", action: "resetTamper", icon: "st.security.alarm.off", backgroundColor:"#ffa81e"
		}
        //AlecM 2018-08-11 - add spacers to even out layout of tiles below multitile
        valueTile("spacer", "spacer", decoration: "flat", inactiveLabel: false, width: 1, height: 2) {
	    state "default", label:''
        }
        valueTile("spacer2", "spacer2", decoration: "flat", inactiveLabel: false, width: 1, height: 2) {
	    state "default", label:''
        }
        
         valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
            state "battery", label:'${currentValue}%', unit:"%", icon:"https://raw.githubusercontent.com/alecm/SmartThingsAlecM/master/images/XiaomiBattery.png",
            backgroundColors:[
                [value: 10, color: "#bc2323"],
                [value: 26, color: "#f1d801"],
 //             [value: 51, color: "#44b621"] - changed shade of green for above 51%
                [value: 51, color: "#79b821"]
                
            ]
        }
		main ("richcontact")
        details(["richcontact","spacer","tamper","battery","spacer2"]) 
         preferences {
		//Date & Time Config
		input description: "", type: "paragraph", element: "paragraph", title: "DATE & CLOCK"    
		input name: "dateformat", type: "enum", title: "Set Date Format\n US (MDY) - UK (DMY)", description: "Date Format", options:["US","UK"]
		input name: "clockformat", type: "bool", title: "Use 24 hour clock?"

  } 
	}


// Parse incoming device messages to generate events
def parse(String description) {
	//def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    def timeString = clockformat ? "HH:mm:ss" : "h:mm:ss aa"
   	//def nowUS = new Date().format("EEE MMM dd yyyy ${timeString}", location.timeZone)
    
    def nowUS = new Date().format("EEE MMM dd yyyy ${timeString}", location.timeZone)
    def nowUK = new Date().format("EEE dd MMM yyyy ${timeString}", location.timeZone)
    //def nowDate = new Date(now).getTime()
    log.debug "description: $description"

	def results = []
    if (description?.startsWith('zone status 0x0031 ')) 
    {
      //sendEvent(name: "lastOpened", value: now)
      if (dateformat == "US" || dateformat == "" || dateformat == null){
      	sendEvent(name: "lastOpened", value: nowUS)}
        else if (dateformat == "UK"){ 
        sendEvent(name: "lastOpened", value: nowUK)
      
      //sendEvent(name: "lastOpenedDate", value: nowDate) 
    }  
    }
	if (description?.startsWith('catchall:')) {
		results = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr -')) {
		results = parseReportAttributeMessage(description)
	}
	else if (description?.startsWith('zone status')) {
		results = parseIasMessage(description)
	}
	log.debug "Parse returned $results"

	if (description?.startsWith('enroll request')) {
		List cmds = enrollResponse()
		log.debug "enroll response: ${cmds}"
		results = cmds?.collect { new physicalgraph.device.HubAction(it) }
	}
    
    
	return results
}

//Initializes device and sets up reporting
def configure() {
	String zigbeeId = swapEndianHex(device.hub.zigbeeId)
	log.debug "Configuring Reporting, IAS CIE, and Bindings."
    
	def cmd = [
		"zcl global write 0x500 0x10 0xf0 {${zigbeeId}}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 1", "delay 1500",
	
		"zcl global send-me-a-report 0x500 0x0012 0x19 0 0xFF {}", "delay 200", //get notified on tamper
		"send 0x${device.deviceNetworkId} 1 1", "delay 1500",
		
		"zcl global send-me-a-report 1 0x20 0x20 5 21600 {01}", "delay 200", //battery report request
		"send 0x${device.deviceNetworkId} 1 1", "delay 1500",
	
		"zdo bind 0x${device.deviceNetworkId} 1 1 0x500 {${device.zigbeeId}} {}", "delay 500",
		"zdo bind 0x${device.deviceNetworkId} 1 1 1 {${device.zigbeeId}} {}", "delay 500",
		"st rattr 0x${device.deviceNetworkId} 1 1 0x20"
		]
	cmd
}


//Sends IAS Zone Enroll response
def enrollResponse() {
	log.debug "Sending enroll response"
	[	
	"raw 0x500 {01 23 00 00 00}", "delay 200",
	"send 0x${device.deviceNetworkId} 1 1"
	]
}

private Map parseCatchAllMessage(String description) {
 	def results = [:]
 	def cluster = zigbee.parse(description)
 	if (shouldProcessMessage(cluster)) {
		switch(cluster.clusterId) {
			case 0x0001:
				log.debug "Received a catchall message for battery status. This should not happen."
				results << createEvent(getBatteryResult(cluster.data.last()))
				break
            }
        }

	return results
}

private boolean shouldProcessMessage(cluster) {
	// 0x0B is default response indicating message got through
	// 0x07 is bind message
	boolean ignoredMessage = cluster.profileId != 0x0104 || 
		cluster.command == 0x0B ||
		cluster.command == 0x07 ||
		(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
	return !ignoredMessage
}

private parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	//log.debug "Desc Map: $descMap"

	def results = []
    
	if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		log.debug "Received battery level report"
		results = createEvent(getBatteryResult(Integer.parseInt(descMap.value, 16)))
	}
 

	return results
}

private parseIasMessage(String description) {
	List parsedMsg = description.split(' ')
	String msgCode = parsedMsg[2]
	int status = Integer.decode(msgCode)
	def linkText = getLinkText(device)

	def results = []
	log.debug(description)
	if (status & 0b00000001) {
    		//sendEvent(name: "lastOpened", value: nowTime, displayed: true)
  
            results << createEvent(getContactResult('open'))
           // createEvent(name: "lastOpenedDate", value: nowDate, displayed: true)}
           }
	else if (~status & 0b00000001) results << createEvent(getContactResult('closed'))

	if (status & 0b00000100) {
    		log.debug "Tampered"
            results << createEvent([name: "tamper", value:"detected"])
	}
	else if (~status & 0b00000100) {
		//don't reset the status here as we want to force a manual reset
		//log.debug "Not tampered"
		//results << createEvent([name: "tamper", value:"OK"])
	}
	
	if (status & 0b00001000) {
		//battery reporting seems unreliable with these devices. However, they do report when low.
		//Just in case the battery level reporting has stopped working, we'll at least catch the low battery warning.
		//
		//** Commented this out as this is currently conflicting with the battery level report **/
		//log.debug "${linkText} reports low battery!"
		//results << createEvent([name: "battery", value: 10])
	}
	else if (~status & 0b00001000) {
		//log.debug "${linkText} battery OK"
	}
	//log.debug results
	return results
}

//Converts the battery level response into a percentage to display in ST
//and creates appropriate message for given level

//AlecM - 8-31 Added mapping values for 34 -29 to map below -since they were valid values - 3 or 2.9  volts were returning null

private getBatteryResult(volts) {
	def batteryMap = [34:100, 33:100, 32:100, 31:100, 30:100, 29:95, 28:90, 27:80, 26:75, 25:50, 24:25, 23:20,
                          22:10, 21:0]
	def minVolts = 21
    //AlecM 10-2-2016 changed maxvolts to 34 to see what really getting w/ new battery
	def maxVolts = 34  
	def linkText = getLinkText(device)
	def result = [name: 'battery']
	
    
    if (volts < minVolts) volts = minVolts
    	else if (volts > maxVolts) volts = maxVolts
    
    result.value = batteryMap[volts]   
    result.descriptionText = "${linkText} battery was ${result.value}%"
    log.debug("${linkText} reports battery voltage at ${result.value}%") //added logging for voltage level to help determine actual min voltage from users
	return result
}


private Map getContactResult(value) {
	def linkText = getLinkText(device)
	def descriptionText = "${linkText} was ${value == 'open' ? 'opened' : 'closed'}"
	return [
		name: 'contact',
		value: value,
		descriptionText: descriptionText
		]
}

//Resets the tamper switch state
private resetTamper(){
	log.debug "Tamper alarm reset."
	sendEvent([name: "tamper", value:"clear"])
}

private hex(value) {
	new BigInteger(Math.round(value).toString()).toString(16)
}

private String swapEndianHex(String hex) {
	reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
	int i = 0;
	int j = array.length - 1;
	byte tmp;
	while (j > i) {
		tmp = array[j];
		array[j] = array[i];
		array[i] = tmp;
		j--;
		i++;
	}
	return array
}
private testTamper() {
	sendEvent([name: "tamper", value: "detected"])
}
