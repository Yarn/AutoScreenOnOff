# Auto Screen OnOff

## Overview
An android app to turn on/off screen automatically by detecting values from proximity, light, and magnet sensors.
Requires android 4.3 due to it using uncalibrated magnet sensor data.
This program is designed for use with a case that has a metal plate in the front flap part. (also assumes your magnet sensor works the same as my HTC One)
It also has a mode that will use the light sensor to try to filter out false positives from the proximity sensor.
If you only want to use the proximity sensor you should look at https://github.com/plateaukao/AutoScreenOnOff

## Features
0. Assume everything is broken.
1. By detecting p-sensor and light sensor, or magnet sensor automatically turn on/off the screen for you.
4. Separate timeout values for screen on/off delay to prevent from accidentally triggering the feature.
5. A widget is supported to quickly toggle the function.

### How it works
Modify Settings in "Auto Screen Settings" app and enable the function

or 

1. Add widget "AutoScreenOnOff" to your home screen
2. Press once on the icon to trigger Device Management Confirmation Dialog.
3. Agree to activate device management. (This is required to turn off the screen)
4. Now everything should work now. Try cover your hand over the top area of the screen (where the proximity sensor might be located) to see if it works.

## Development
This project is built using Android Studio. If you want to clone the git and modify the codes, please use Android Studio too.

## Screenshots
Preference Screen (out of date)
<img src="https://github.com/plateaukao/AutoScreenOnOff/raw/master/screenshots/autoscreenonoff_preferences.png" alt="preference" style="width: 400px;"/>