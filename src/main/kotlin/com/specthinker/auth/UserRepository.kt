package com.specthinker.auth

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : CrudRepository<User, String> {
    fun findByEmail(email: String): Optional<User>
    fun findByStripeCustomerId(customerId: String): Optional<User>
}
