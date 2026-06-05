package com.specthinker.spec

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class SpecService(
    private val repository: SpecRepository,
    private val renderers: Map<String, Renderer>,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(
            renderers.containsKey("markdown") &&
                renderers.containsKey("text") &&
                renderers.containsKey("html"),
        ) {
            "renderers map must contain entries for 'markdown', 'text', and 'html'"
        }
    }

    fun list(): List<SpecSummary> = repository.findAllByOrderByUpdatedAtDesc()
        .map { it.toSummary() }

    fun get(id: String): Spec = repository.findById(id).orElseThrow { SpecNotFoundException(id) }

    fun create(req: CreateSpecRequest): Spec {
        val now = Instant.now(clock)
        val spec = Spec(
            id = UUID.randomUUID().toString(),
            title = req.title.trim(),
            sections = req.sections,
            createdAt = now,
            updatedAt = now,
            version = 0L,
        )
        return repository.save(spec)
    }

    fun update(id: String, req: UpdateSpecRequest): Spec {
        val existing = repository.findById(id).orElseThrow { SpecNotFoundException(id) }
        if (existing.version != req.version) {
            throw SpecVersionMismatchException(id, req.version, existing.version)
        }
        val now = Instant.now(clock)
        val updated = existing.withUpdates(req.title.trim(), req.sections, now)
        return repository.save(updated)
    }

    fun delete(id: String) {
        if (!repository.existsById(id)) throw SpecNotFoundException(id)
        repository.deleteById(id)
    }

    fun renderSaved(id: String, format: Format): String {
        val spec = get(id)
        return render(spec.title, spec.sections, format)
    }

    fun render(title: String, sections: Sections, format: Format): String {
        val key = format.name.lowercase()
        val renderer = renderers[key]
            ?: throw IllegalStateException("No renderer registered for format: $format")
        return renderer.render(title, sections)
    }
}

internal fun Spec.toSummary(): SpecSummary = SpecSummary(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
)

internal fun Spec.withUpdates(title: String, sections: Sections, now: Instant): Spec =
    copy(title = title, sections = sections, updatedAt = now)
