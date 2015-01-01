#!/usr/bin/python
import json
from sys import stdout
temperature = 12.12
humidity = 07.07
stdout.write(json.dumps({'temperature' : temperature, 'humidity' : humidity}))