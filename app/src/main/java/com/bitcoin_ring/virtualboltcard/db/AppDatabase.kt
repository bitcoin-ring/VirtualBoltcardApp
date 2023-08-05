package com.bitcoin_ring.virtualboltcard.db
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bitcoin_ring.virtualboltcard.db.converters.AdditionalCardDataConverter
import com.bitcoin_ring.virtualboltcard.db.dao.CardDao
import com.bitcoin_ring.virtualboltcard.db.entities.Card


@Database(entities = [Card::class], version = 4, exportSchema = true)
@TypeConverters(AdditionalCardDataConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}