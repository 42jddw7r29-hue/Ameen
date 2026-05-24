package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM whitelisted_contacts ORDER BY createdAt DESC")
    fun getAllContactsFlow(): Flow<List<WhitelistedContact>>

    @Query("SELECT * FROM whitelisted_contacts ORDER BY createdAt DESC")
    suspend fun getAllContacts(): List<WhitelistedContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: WhitelistedContact)

    @Update
    suspend fun updateContact(contact: WhitelistedContact)

    @Delete
    suspend fun deleteContact(contact: WhitelistedContact)

    @Query("DELETE FROM whitelisted_contacts WHERE id = :id")
    suspend fun deleteContactById(id: Int)
}
