package com.specthinker.spec

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/specs")
class SpecController(
    private val service: SpecService,
) {

    @GetMapping
    fun list(): List<SpecSummary> = service.list()

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): Spec = service.get(id)

    @PostMapping
    fun create(@RequestBody req: CreateSpecRequest): ResponseEntity<Spec> =
        ResponseEntity.status(201).body(service.create(req))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody req: UpdateSpecRequest,
    ): Spec = service.update(id, req)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/render", produces = ["text/markdown", "text/plain", "text/html"])
    fun renderSaved(
        @PathVariable id: String,
        @RequestParam("format", defaultValue = "markdown") formatRaw: String,
    ): ResponseEntity<String> {
        val format = Format.parse(formatRaw)
        val body = service.renderSaved(id, format)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(format.mediaType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(body)
    }

    @PostMapping("/render", produces = ["text/markdown", "text/plain", "text/html"])
    fun renderAdHoc(
        @RequestBody req: RenderRequest,
        @RequestParam("format", defaultValue = "markdown") formatRaw: String,
    ): ResponseEntity<String> {
        val format = Format.parse(formatRaw)
        val body = service.render(req.title, req.sections, format)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(format.mediaType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(body)
    }
}
