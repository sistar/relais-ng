#!/usr/bin/python
import json
from sys import stdout
import Adafruit_DHT

# Sensor should be set to Adafruit_DHT.DHT11,
# Adafruit_DHT22, or Adafruit_AM2302.
sensor = Adafruit_DHT.DHT22
pin = 8

# Try to grab a sensor reading.  Use the read_retry method which will retry up
# to 15 times to get a sensor reading (waiting 2 seconds between each retry).
humidity, temperature = Adafruit_DHT.read_retry(sensor, pin)

# Note that sometimes you won't get a reading and
# the results will be null (because Linux can't
# guarantee the timing of calls to read the sensor).  
# If this happens try again!
if humidity is not None and temperature is not None:
	stdout.write(json.dumps({'temperature' : temperature, 'humidity' : humidity}))