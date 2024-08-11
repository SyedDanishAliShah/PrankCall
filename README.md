# PrankCallAndroid

This prank call app lets users schedule a fake call by entering a name and phone number, and selecting a specific date and time. The call is triggered exactly at the set time, even if the device is idle(in sleep mode), using AlarmManager.setExactAndAllowWhileIdle. The app utilizes WakeLock, KeyguardManager, PowerManager, Foreground Service, BroadcastReceiver, and Room Database.
