package com.github.fschroffner.radiodroid3.utils

class RateLimiter(private val limit: Int, private val fullReplenishTime: Long) {
    private var available = limit.toDouble()
    private var lastTime = System.currentTimeMillis()

    fun allowed(): Boolean {
        val now = System.currentTimeMillis()
        available += Math.abs(now - lastTime) * (1.0 / fullReplenishTime) * limit
        if (available > limit) available = limit.toDouble()

        return if (available < 1.0) {
            false
        } else {
            available--
            lastTime = now
            true
        }
    }
}
