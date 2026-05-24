package com.example.data

import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {
    val allContactsFlow: Flow<List<WhitelistedContact>> = contactDao.getAllContactsFlow()

    suspend fun getAllContacts(): List<WhitelistedContact> = contactDao.getAllContacts()

    suspend fun insertContact(contact: WhitelistedContact) {
        contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: WhitelistedContact) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: WhitelistedContact) {
        contactDao.deleteContact(contact)
    }

    suspend fun deleteContactById(id: Int) {
        contactDao.deleteContactById(id)
    }
}
