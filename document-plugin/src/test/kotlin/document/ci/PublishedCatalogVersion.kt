package document.ci

/**
 * DOC-CI-ISOLATION (D3) — resolves a version of the *published* workspace
 * catalog, injected by Gradle (`ws.versions.*` → `systemProperty`).
 *
 * The publication hygiene guard must never read a neighbour repository's
 * working tree (`../workspace-bom/…`): that is racy between sessions and absent
 * from an isolated CI checkout — the failure mode graphify-gradle's D5-RACE
 * EPIC fixed (S-029) and which kept document-gradle's CI red behind the
 * settings barrier. A missing injected property is an **explicit error**, never
 * a silent green.
 */
object PublishedCatalogVersion {
    fun require(
        property: String,
        lookup: (String) -> String? = System::getProperty,
    ): String =
        lookup(property)?.takeIf { it.isNotBlank() }
            ?: error(
                "propriété '$property' absente — la version du catalog publié doit être injectée " +
                    "par Gradle (systemProperty dans build.gradle.kts), jamais lue d'un dépôt voisin",
            )
}
