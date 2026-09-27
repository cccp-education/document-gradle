package document.ci

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * DOC-CI-ISOLATION (D3) — the publication hygiene guard reads the *published*
 * catalog version injected by Gradle, never a neighbour repository's working
 * tree (racy between sessions, absent on an isolated CI checkout — graphify
 * D5-RACE, S-029).
 */
class PublishedCatalogVersionTest {

    @Test
    fun `returns the injected property value`() {
        val value = PublishedCatalogVersion.require("document.publishedCatalog.documentVersion") { "0.0.18" }

        assertThat(value).isEqualTo("0.0.18")
    }

    @Test
    fun `a missing property is an explicit error, never a silent green`() {
        assertThatThrownBy {
            PublishedCatalogVersion.require("document.publishedCatalog.documentVersion") { null }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("document.publishedCatalog.documentVersion")
    }

    @Test
    fun `a blank property is an explicit error`() {
        assertThatThrownBy {
            PublishedCatalogVersion.require("document.publishedCatalog.bomVersion") { "   " }
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
