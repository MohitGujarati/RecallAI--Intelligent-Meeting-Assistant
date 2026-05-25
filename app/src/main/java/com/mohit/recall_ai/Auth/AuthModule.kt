package com.mohit.recall_ai.Auth


import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provides [AuthRepository] as a singleton.
 *
 * AuthRepository is annotated with @Inject constructor so Hilt can resolve it
 * automatically — this module is only needed if you want to provide a test
 * double. In production the @Singleton + @Inject on the class is sufficient.
 *
 * Keeping this file empty-but-present makes swapping to a mock repo in tests
 * trivial: just override the binding here.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule
// AuthRepository resolves automatically via @Singleton + @Inject constructor.
// Add @Binds / @Provides overrides here for testing.