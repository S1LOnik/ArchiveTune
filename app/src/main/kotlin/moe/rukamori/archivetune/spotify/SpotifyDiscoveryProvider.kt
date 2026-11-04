/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object SpotifyDiscoveryProvider {
    // [FIX-ZCODE] Wave 8 (owner request): the fake "Daily Mix 1/2", "Discover Weekly" and
    // "Release Radar" entries were removed — those playlists are personalized and have no
    // global ID, so fetching them always failed. The home feed now builds this section from
    // the user's real playlists (homePlaylists) and only falls back to these public ones.
    val curatedPlaylists: List<SpotifyPlaylist> =
        listOf(
            SpotifyPlaylist(
                id = "37i9dQZF1DXcBWIGoYBM5M",
                name = "Today's Top Hits",
                description = "Lady Gaga & Bruno Mars are on top of the Hottest 50!",
                images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67706f000000021e86095368a356fb39f60046")),
                owner = SpotifyPlaylistOwner(displayName = "Spotify"),
                tracks = SpotifyPlaylistTracksRef(total = 50),
            ),
            SpotifyPlaylist(
                id = "37i9dQZEVXbMDoHDwVN2tF",
                name = "Top 50 - Global",
                description = "Your daily update of the most played tracks right now - Global.",
                images = listOf(SpotifyImage(url = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_global_default.jpg")),
                owner = SpotifyPlaylistOwner(displayName = "Spotify"),
                tracks = SpotifyPlaylistTracksRef(total = 50),
            ),
            SpotifyPlaylist(
                id = "37i9dQZF1DX0XUsuxWHRQd",
                name = "RapCaviar",
                description = "Music from Drake, Travis Scott, Kendrick Lamar and Metro Boomin.",
                images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67706f00000002221edb8214ec0a6f443b7410")),
                owner = SpotifyPlaylistOwner(displayName = "Spotify"),
                tracks = SpotifyPlaylistTracksRef(total = 50),
            ),
            SpotifyPlaylist(
                id = "37i9dQZF1DX4WYpdgoIcn6",
                name = "Chill Hits",
                description = "Kick back to the best new and recent chill hits.",
                images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67706f000000028e202166db2ca392fc60f6db")),
                owner = SpotifyPlaylistOwner(displayName = "Spotify"),
                tracks = SpotifyPlaylistTracksRef(total = 75),
            ),
        )

    // [FIX-ZCODE] Wave 8: personalized "made for you" playlists come from the user's own
    // account, so they rank first; the rest of the user's playlists follow, and curated
    // public playlists pad the shelf when the user has few.
    fun homePlaylists(
        userPlaylists: List<SpotifyPlaylist>,
        maxItems: Int = 8,
    ): List<SpotifyPlaylist> {
        val personalized =
            userPlaylists.filter { it.id.isNotBlank() && isPersonalizedName(it.name) }
        val otherUser = userPlaylists.filter { it.id.isNotBlank() && !isPersonalizedName(it.name) }
        return (personalized + otherUser + curatedPlaylists)
            .distinctBy { it.id }
            .take(maxItems)
    }

    private fun isPersonalizedName(name: String): Boolean {
        val normalized = name.trim().lowercase()
        return normalized.startsWith("daily mix") ||
            normalized.startsWith("дейли микс") ||
            normalized == "discover weekly" ||
            normalized == "release radar"
    }

    val trendingTracks: List<SpotifyTrack> =
        listOf(
            SpotifyTrack(
                id = "2plbrEY59IikOBgBGLjaoe",
                name = "Die With A Smile",
                artists = listOf(SpotifySimpleArtist(id = "1HY2Jd0NmPuamShAr6KMms", name = "Lady Gaga"), SpotifySimpleArtist(id = "0du5cEVh5yTK9QJze8zA0C", name = "Bruno Mars")),
                album = SpotifySimpleAlbum(id = "0S0Fs2x4l4bSikwA2Nn94E", name = "Die With A Smile", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b27382ea2e9e1f4a9192bb3d843d"))),
                durationMs = 251667,
            ),
            SpotifyTrack(
                id = "6dOtVTDdiauQNBQEDOtlAB",
                name = "BIRDS OF A FEATHER",
                artists = listOf(SpotifySimpleArtist(id = "6qqNVTkY8uBg9cP3Jd7DAH", name = "Billie Eilish")),
                album = SpotifySimpleAlbum(id = "7aJuG4MyanPK2J26z3KnHa", name = "HIT ME HARD AND SOFT", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b27371d62ea7ea8a5be92d3c1f62"))),
                durationMs = 194373,
            ),
            SpotifyTrack(
                id = "2qSkIjg1o9h3YT9RAgYN75",
                name = "Espresso",
                artists = listOf(SpotifySimpleArtist(id = "74KM79TiuVKeVCqs8QtB0B", name = "Sabrina Carpenter")),
                album = SpotifySimpleAlbum(id = "5quMT85FMsK9gvpG2w464n", name = "Short n' Sweet", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b273659cd4673230913b3918e0d5"))),
                durationMs = 175459,
            ),
            SpotifyTrack(
                id = "5G2f63n7IPVPPjfNIGih7Q",
                name = "Taste",
                artists = listOf(SpotifySimpleArtist(id = "74KM79TiuVKeVCqs8QtB0B", name = "Sabrina Carpenter")),
                album = SpotifySimpleAlbum(id = "5quMT85FMsK9gvpG2w464n", name = "Short n' Sweet", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b273659cd4673230913b3918e0d5"))),
                durationMs = 157279,
            ),
            SpotifyTrack(
                id = "6tNQ70jh4OwmPGpYy6R2o9",
                name = "Beautiful Things",
                artists = listOf(SpotifySimpleArtist(id = "22wbnEMDvgVIAGdFeek6ET", name = "Benson Boone")),
                album = SpotifySimpleAlbum(id = "24u5j62M79eXq78w62B5N8", name = "Fireworks & Rollerblades", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b27397e1b5f63be3d489814421b4"))),
                durationMs = 180304,
            ),
            SpotifyTrack(
                id = "7MXVkk9YMctZqd1Srtv4MB",
                name = "Starboy",
                artists = listOf(SpotifySimpleArtist(id = "1Xyo4u8uXC1ZmMpatF05PJ", name = "The Weeknd"), SpotifySimpleArtist(id = "4tZwfgrHOc3mvqYxwDoOD1", name = "Daft Punk")),
                album = SpotifySimpleAlbum(id = "2ODvWsOgouMbaA5xf0RkJe", name = "Starboy", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"))),
                durationMs = 230453,
            ),
            SpotifyTrack(
                id = "0VjIjW4GlUZAMYd2vXMi3b",
                name = "Blinding Lights",
                artists = listOf(SpotifySimpleArtist(id = "1Xyo4u8uXC1ZmMpatF05PJ", name = "The Weeknd")),
                album = SpotifySimpleAlbum(id = "4yP0hdKOJeuukj0DYbtgkz", name = "After Hours", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"))),
                durationMs = 200040,
            ),
            SpotifyTrack(
                id = "5HCyWlXZPP0y6Gqq8TgA20",
                name = "STAY",
                artists = listOf(SpotifySimpleArtist(id = "2tIP7wV1svHTDR0ry5hk8W", name = "The Kid LAROI"), SpotifySimpleArtist(id = "1uNFoZAHBGtllmzznpCI3s", name = "Justin Bieber")),
                album = SpotifySimpleAlbum(id = "4vGvjI0Y2x9Cj1r1q1n06p", name = "F*CK LOVE 3+: OVER YOU", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b27341e31d35a98709a067ff50b8"))),
                durationMs = 141806,
            ),
            SpotifyTrack(
                id = "4Dvkj6JhhA12EX05fT7y2e",
                name = "As It Was",
                artists = listOf(SpotifySimpleArtist(id = "6KImCVD70vtRIJWnq6xn9W", name = "Harry Styles")),
                album = SpotifySimpleAlbum(id = "5r36AJflADHT0GyR51a9K5", name = "Harry's House", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b2732e8f6374a44139e31d044248"))),
                durationMs = 167303,
            ),
            SpotifyTrack(
                id = "2QjOHCTQ1Jl3zawyYOpxh6",
                name = "Sweater Weather",
                artists = listOf(SpotifySimpleArtist(id = "7tYKF4w9nC0nq9CsPZTHte", name = "The Neighbourhood")),
                album = SpotifySimpleAlbum(id = "4xkM0B599FLgl9Dy924407", name = "I Love You.", images = listOf(SpotifyImage(url = "https://i.scdn.co/image/ab67616d0000b2738265a73628ee3269b6a38612"))),
                durationMs = 240400,
            ),
        )
}
