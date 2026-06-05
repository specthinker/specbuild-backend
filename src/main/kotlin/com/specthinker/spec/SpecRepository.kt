package com.specthinker.spec

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface SpecRepository : CrudRepository<Spec, String> {
    fun findAllByOrderByUpdatedAtDesc(): List<Spec>
}
