package com.specthinker.spec

class SpecNotFoundException(id: String) : RuntimeException("Spec not found: $id")

class SpecVersionMismatchException(id: String, expected: Long, actual: Long?) :
    RuntimeException("Spec $id version mismatch: expected=$expected actual=$actual")
