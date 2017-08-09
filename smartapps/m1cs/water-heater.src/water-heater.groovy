/**
 *  Turn on the water heater if it is needed in the morning
 *	This is for the scenario where an immerison heater is used to heat a water tank, along with other sources.
 *	Currently we have: Solar Water Heating, Boiler Water Heating, Immersion Water Heating, (where the boiler will also heat the house (no separation))
 *	If the water is below a threshold, turn the immersion on for a set time period.
 *	In future we can detect if the PV Panels or Wind Turbine is spinning to use the most cost effective heating method.
 *
 *	Author: Mike Baird
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
 */
 definition(
    name: "Water Heater",
    namespace: "m1cs",
    author: "Mike Baird",
    description: "Turn on the immersion heater if needed",
    category: "Convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/Weather/weather2-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Weather/weather2-icn@2x.png"
)

preferences {
	section("At a Time...") {
		input name: "startTime", title: "Turn On Time?", type: "time", required: true
	}
	section("Check the water temperature...") {
		input "temperatureSensor1", "capability.temperatureMeasurement", required: true
	}
	section("If the temperature is below...") {
		input "temperature1", "number", title: "Minimum Temperature?", required: true
	}
	//section("And we are generating electricity...") {
	//	input "generator1", "capability.sensor", title: "Generator?", required: false
	//}
    section("And the Boiler is not on...") {
		input "boiler1", "capability.switch", required: false
	}
	section("Turn on the Immersion Heater...") {
		input "immersion1", "capability.switch", required: true
	}	
	//section("Or if the house temperature is below...") {
	//	input "thermostat1", "capability.temperatureMeasurement", title: "Thermostat?", required: false
	//}
	//section("Turn on the Boiler...") {
	//	input "boiler1", "capability.switch", required: false
	//}	
	section("For a duration of...") {
		input "duration1", "number", title: "Minutes?", required: true
	}
    section("or until the temperature reaches...") {
		input "temperature2", "number", title: "Target Temperature?", required: true
	}
    section( "Notifications" ) {
        input("recipients", "contact", title: "Send notifications to") {
            input "sendPushMessage", "enum", title: "Send a push notification?", options: ["Yes", "No"], required: false
            input "phone1", "phone", title: "Send a Text Message?", required: false
        }
    }
}


def installed() {
	schedule(startTime, "startTimerCallback")
}

def updated() {
	unschedule()
	schedule(startTime, "startTimerCallback")
}

def startTimerCallback() {
    
	def currentTemp = temperatureSensor1.temperatureState.doubleValue
	def minTemp = temperature1
	def mySwitch = settings.immersion1

	state.startTemperature = currentTemp

	if (currentTemp <= minTemp) {
        log.debug "Water Temperature ($currentTemp) is below $minTemp"     
        def boilerOn = boiler1?.currentValue("switch") == "on"
    	if (boilerOn == null || boilerOn == false) {
        	log.debug "Boiler is off or not defined"
            log.debug "Water Temperature ($currentTemp) is below $minTemp:  sending notification and activating $mySwitch"
            def tempScale = location.temperatureScale ?: "C"
            send("Turning on $mySwitch because ${temperatureSensor1.displayName} is reporting a temperature of ${currentTemp}${tempScale}")
            turnOnImmersion()
            //start monitoring the temperature
            subscribe(temperatureSensor1, "temperature", temperatureHandler)
            def MinuteDelay = 60 * duration1
            runIn(MinuteDelay, immersionTimerExpired)
        }
        else {
            log.debug "Boiler is already heating the water - no need for immersion"        
        }
	}
    else {    
		log.debug "Temperature is above $minTemp:  not activating $mySwitch"
    }
}


def temperatureHandler(evt) {
	log.trace "Current Water Temperature: $evt.value"

	def targetTemperature = temperature2
	def mySwitch = settings.immersion

	if (evt.doubleValue >= targetTemperature) {
        log.debug "Temperature above $targetTemperature:  sending notification and deactivating $mySwitch"
        def tempScale = location.temperatureScale ?: "C"
        send("${temperatureSensor1.displayName} is too cold, reporting a temperature of ${evt.value}${evt.unit?:tempScale}")
        //turn off the immersion and unsubscribe the event.
        turnOffImmersion()
        unsubscribe()
	}
}
def immersionTimerExpired() {
	def currentTemp = temperatureSensor1.temperatureState.doubleValue
    
	log.debug "Turning off the immersion as the timer has expired."
    log.debug "Current Water Temperature is $currentTemp, start Temperature was $state.startTemperature"
	turnOffImmersion()
}

def turnOnImmersion() {
	def mySwitch = settings.immersion1
	log.debug "Enabling $mySwitch"
	immersion1?.on()
}

def turnOffImmersion() {
	def mySwitch = settings.immersion1
	log.debug "Disabling $mySwitch"
	immersion1?.off()
}

private send(msg) {
    if (location.contactBookEnabled) {
        log.debug("Sending Notifications To: ${recipients?.size()}")
        sendNotificationToContacts(msg, recipients)
    }
    else {
        if (sendPushMessage != "No") {
            log.debug("Sending Push Message")
            sendPush(msg)
        }

        if (phone1) {
            log.debug("Sending Text Message")
            sendSms(phone1, msg)
        }
   	}
    log.debug msg
}