package com.aistudio.youthverse.rvzkmq.repository

import com.aistudio.youthverse.rvzkmq.base.FirestoreEmulatorTestBase
import com.aistudio.youthverse.rvzkmq.data.model.ContentItem
import com.aistudio.youthverse.rvzkmq.data.model.LibraryEntry
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.UUID

class YouthVerseRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createContent_asAdmin_succeeds() = runBlocking {
    signInTestUser(ADMIN_EMAIL)
    val contentRepo = ContentRepository(firestore)

    val item = ContentItem(
      title = "Lord of the Mysteries",
      author = "Cuttlefish",
      contentType = "NOVEL",
      genres = listOf("Fantasy", "Mystery"),
      published = true
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      contentRepo.createContent(item)
    }
    if (result.isFailure) {
      throw AssertionError("createContent failed: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
    }
    assertTrue(result.isSuccess)
    val id = result.getOrThrow()
    assertTrue(id.isNotEmpty())
  }

  @Test
  fun libraryEntry_ownerCanWriteAndRead() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val libraryRepo = LibraryRepository(firestore)
    val contentId = "content_" + UUID.randomUUID().toString()

    val setResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      libraryRepo.setLibraryStatus(aliceUid, contentId, "READING", isFavorite = true, isBookmarked = true)
    }
    if (setResult.isFailure) {
      throw AssertionError("setLibraryStatus failed: ${setResult.exceptionOrNull()?.message}", setResult.exceptionOrNull())
    }
    assertTrue(setResult.isSuccess)

    val entryResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      libraryRepo.getLibraryEntry(aliceUid, contentId)
    }
    if (entryResult.isFailure) {
      throw AssertionError("getLibraryEntry failed: ${entryResult.exceptionOrNull()?.message}", entryResult.exceptionOrNull())
    }
    assertTrue(entryResult.isSuccess)
    val entry = entryResult.getOrThrow()
    assertNotNull(entry)
    assertEquals("READING", entry?.status)
    assertEquals(true, entry?.isFavorite)
  }

  @Test
  fun libraryEntry_crossUserAccess_isPrevented() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceLibraryRepo = LibraryRepository(firestore)
    val contentId = "content_" + UUID.randomUUID().toString()

    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceLibraryRepo.setLibraryStatus(aliceUid, contentId, "READING")
    }

    // Now sign in as Bob and attempt to read Alice's library
    signInTestUser(BOB_EMAIL)
    val bobLibraryRepo = LibraryRepository(firestore)
    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobLibraryRepo.getLibraryEntry(aliceUid, contentId)
    }
    // Result should be failure due to PERMISSION_DENIED
    assertTrue(result.isFailure)
  }

  private companion object {
    const val ADMIN_EMAIL = "admin@test.com"
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 6000L
  }
}
