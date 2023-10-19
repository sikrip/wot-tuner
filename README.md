# Wot Tuner

Analyzes ECU logs in order to produce adjusted fuel tables at WOT conditions.

The existing fuel map is expected in the file 'fuel.map' file and the new map will be 
produced in the 'new-fuel.map'.

Also, a brief analysis of the ECU logs is printed in the console.

I have tested this with logs from [ApexiPowerTune](https://github.com/sikrip/ApexiPowerTune) but it could be used with
logs from other ECUs (you will need to adjust the properties accordingly).

## Usage

Create a properties file (like [this one](./tuner.properties)) and a fuel map file (like [this one](./fuel.map)) and then run:

> java -jar wot-tuner.jar ecu-log-file-path

## Safety notice
This is experimental, use it with caution and at your own risk!
