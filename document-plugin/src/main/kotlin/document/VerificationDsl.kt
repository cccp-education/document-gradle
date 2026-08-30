package document

import org.gradle.api.provider.Property

/**
 * Verification DSL block for document verification tasks.
 */
class VerificationDsl(
    val htmlLinks: Property<Boolean>
)