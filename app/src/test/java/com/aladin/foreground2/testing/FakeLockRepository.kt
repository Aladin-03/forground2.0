package com.aladin.foreground2.testing

import com.aladin.foreground2.core.data.repo.LockRepository
import com.aladin.foreground2.core.domain.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LockRepository] observed as a flow; active means ending after [clock]'s now. */
class FakeLockRepository(
    private val clock: Clock,
) : LockRepository {
    val locks = MutableStateFlow<Map<String, Long>>(emptyMap())

    override suspend fun activeLocks(): Map<String, Long> = locks.value.filterValues { it > clock.nowMillis() }

    override fun observeLocks(): Flow<Map<String, Long>> = locks

    override suspend fun lock(
        packageName: String,
        unlockAtMillis: Long,
    ): Long =
        maxOf(unlockAtMillis, locks.value[packageName] ?: Long.MIN_VALUE).also { locks.value += packageName to it }

    override suspend fun deleteEnded() {
        locks.value = locks.value.filterValues { it > clock.nowMillis() }
    }
}
