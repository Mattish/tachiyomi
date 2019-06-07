package eu.kanade.tachiyomi.data.sync

import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.CustomRobolectricGradleTestRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(constants = BuildConfig::class, sdk = intArrayOf(Build.VERSION_CODES.LOLLIPOP))
@RunWith(CustomRobolectricGradleTestRunner::class)
class SyncDiffEngineTest {

    @Before
    fun setup() {

    }

    @Test
    fun newMangaHasAllChapters() {
        val existing = listOf<SyncManga>()
        val incoming = listOf(createManga("someurl",1234,1))

        val result = SyncDiffEngine.getAddedOrUpdated(existing,incoming)

        assertThat(result).hasSize(1)
        assertThat(result[0].chapters).hasSize(1)
    }

    @Test
    fun mangaGetsRemoved() {
        val existing = listOf(createManga("someurl",1234,1))
        val incoming = listOf<SyncManga>()

        val result = SyncDiffEngine.getAddedOrUpdated(existing,incoming)

        assertThat(result).hasSize(0)
    }

    @Test
    fun existingMangaHasExistingChapterRead() {
        val existing = listOf(createManga("someurl",1234,2))
        val incoming = listOf(createManga("someurl",1234,2))
        incoming[0].chapters[0].last_page_read = 1

        val result = SyncDiffEngine.getAddedOrUpdated(existing,incoming)

        assertThat(result).hasSize(1)
        assertThat(result[0].chapters).hasSize(1)
        assertThat(result[0].chapters[0].last_page_read).isEqualTo(1)
    }

    @Test
    fun existingMangaHasExistingChapterRead_Multiple() {
        val existing = listOf(createManga("someurl",1234,2))
        val incoming = listOf(createManga("someurl",1234,2))
        incoming[0].chapters[0].read = true
        incoming[0].chapters[1].read = true

        val result = SyncDiffEngine.getAddedOrUpdated(existing,incoming)

        assertThat(result).hasSize(1)
        assertThat(result[0].chapters).hasSize(2)
        assertThat(result[0].chapters[0].read).isEqualTo(true)
        assertThat(result[0].chapters[1].read).isEqualTo(true)
    }

    @Test
    fun noChangesIsEmpty() {
        val existing = listOf(createManga("someurl",1234,1))
        val incoming = listOf(createManga("someurl",1234,1))

        val result = SyncDiffEngine.getAddedOrUpdated(existing,incoming)

        assertThat(result).hasSize(0)
    }

    private fun createManga(url: String, source: Long,numOfChapters:Int): SyncManga{

        val newChapters = mutableListOf<SyncChapter>()

        for (i in 1..numOfChapters){
            newChapters += SyncChapter("url/$i",0,false)
        }

        return SyncManga(url,source,newChapters)
    }

}
