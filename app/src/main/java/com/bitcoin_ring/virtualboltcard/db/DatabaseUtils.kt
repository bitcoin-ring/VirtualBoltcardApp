package com.bitcoin_ring.virtualboltcard.db
import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bitcoin_ring.virtualboltcard.helper.Helper
import net.sqlcipher.database.SupportFactory

object DatabaseUtils {
    fun provideDatabase(context: Context): AppDatabase {
        val sharedPreferences = EncryptedSharedPreferencesHelper(context).encryptedSharedPreferences
        //if not defined setup a new dbpassword and save it.
        var dbpassphrase = sharedPreferences.getString("dbpassphrase", null) ?: ""
        Log.i(ContentValues.TAG, "dbpassword: " + dbpassphrase)
        if (dbpassphrase.isEmpty()){
            Log.i(ContentValues.TAG, "setting dbpassphrase: ")
            dbpassphrase = Helper.generateRandomString(12)
            Log.i(ContentValues.TAG, "generated dbpassphrase: " + dbpassphrase)
            val keyEditor = sharedPreferences.edit()
            keyEditor.putString("dbpassphrase", dbpassphrase)
            keyEditor.apply()
        }
        val factory = SupportFactory(Helper.generateSecureKey(dbpassphrase))
        //Migration
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Card ADD COLUMN additionalCardData TEXT")
            }
        }

        // Define migration from 2 to 3
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS new_Card")
                // Create new temporary table
                database.execSQL(
                    """
            CREATE TABLE new_Card (
                id INTEGER PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                uid TEXT NOT NULL,
                url TEXT NOT NULL,
                key1 TEXT NOT NULL,
                key2 TEXT NOT NULL,
                counter INTEGER NOT NULL,
                drawableName TEXT NOT NULL,
                additionalCardData TEXT NULL
            )
            """.trimIndent()
                )

                // Copy the data
                database.execSQL(
                    """
            INSERT INTO new_Card (id, name, type, uid, url, key1, key2, counter, drawableName, additionalCardData)
            SELECT id, name, type, uid, url, key1, key2, counter, 'virtualboltcard' , additionalCardData FROM Card
            """.trimIndent()
                )

                // Remove the old table
                database.execSQL("DROP TABLE Card")

                // Rename the new table
                database.execSQL("ALTER TABLE new_Card RENAME TO Card")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("UPDATE Card SET drawableName='virtualboltcard_lnbits' where type='AdditionalDataLNbits'")
            }
        }


        return Room.databaseBuilder(
            context,
            AppDatabase::class.java, "encrypted-db"
        ).openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4) // Add the migration here
            .build()
    }
}

