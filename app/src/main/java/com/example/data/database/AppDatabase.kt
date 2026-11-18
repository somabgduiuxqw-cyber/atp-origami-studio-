package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun projectDao(): ProjectDao

  companion object {
    fun create(context: Context): AppDatabase {
      return Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "atp_origami_studio.db",
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }
  }
}
