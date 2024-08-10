package com.example.prankcallapp.databaseclass

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.prankcallapp.daoclass.ScheduledCallDao
import com.example.prankcallapp.dataclasses.ScheduledCall

@Database(entities = [ScheduledCall::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduledCallDao(): ScheduledCallDao
}