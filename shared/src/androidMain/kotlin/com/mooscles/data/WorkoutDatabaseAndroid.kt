package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver

/**
 * The system SQLite through Room's driver API: the same `databases/mooscles.db`, migrations and
 * creation callback as every installed version. It is the only place `:shared` takes a Context.
 */
fun WorkoutDatabase.Companion.open(context: Context): WorkoutDatabase = Room.databaseBuilder(
    context.applicationContext, WorkoutDatabase::class.java, WorkoutDatabase.FILE_NAME
).buildMooscles(AndroidSQLiteDriver())
