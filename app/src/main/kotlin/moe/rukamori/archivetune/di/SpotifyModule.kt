/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import moe.rukamori.archivetune.spotify.session.SpotifyCredentialsStore
import moe.rukamori.archivetune.spotify.session.SpotifySessionManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpotifyModule {

    @Provides
    @Singleton
    fun provideSpotifyCredentialsStore(
        @ApplicationContext context: Context,
    ): SpotifyCredentialsStore = SpotifyCredentialsStore(context)

    @Provides
    @Singleton
    fun provideSpotifySessionManager(
        @ApplicationContext context: Context,
    ): SpotifySessionManager = SpotifySessionManager(context)
}
