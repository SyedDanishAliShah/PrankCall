package com.example.prankcallapp.daoclass

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import com.example.prankcallapp.dataclasses.ScheduledCall

@Dao
interface ScheduledCallDao {
    @Insert
    suspend fun insert(scheduledCall: ScheduledCall)

    @Delete
    suspend fun delete(scheduledCall: ScheduledCall)
}