package com.bitcoin_ring.virtualboltcard.db.dao
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bitcoin_ring.virtualboltcard.db.entities.Card

@Dao
interface CardDao {
    @Query("SELECT * FROM Card")
    fun getAll(): List<Card>

    @Query("SELECT * FROM Card where id=:card_id")
    fun get(card_id: Int): List<Card>

    @Query("UPDATE Card set counter=counter+1 where id=:card_id")
    fun incCounter(card_id: Int): Int

    @Insert
    fun insert(card: Card): Long

    @Update
    fun update(card: Card): Int

    @Query("UPDATE card SET name = :name, uid = :uid, url = :url, key1 = :key1, key2 = :key2, counter = :counter, drawableName = :drawableName WHERE id = :id")
    fun updateCard(id: Int, name: String, uid: String, url: String, key1: String, key2: String, counter: Int, drawableName: String)

    @Delete
    fun delete(card: Card)
}