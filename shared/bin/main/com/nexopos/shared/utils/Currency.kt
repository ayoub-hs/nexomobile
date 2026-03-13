package com.nexopos.shared.utils

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency utilities for precise financial calculations.
 * 
 * WHY: Double has precision issues (e.g., 0.1 + 0.2 != 0.3)
 * Financial calculations require exact decimal arithmetic.
 * BigDecimal provides arbitrary precision and correct rounding.
 */
object Currency {
    private const val SCALE = 2 // Two decimal places for currency
    private val ROUNDING = RoundingMode.HALF_UP // Standard rounding (0.5 rounds up)
    
    /**
     * Convert Double to BigDecimal with proper scale
     */
    fun Double.toBigDecimal(): BigDecimal = 
        BigDecimal.valueOf(this).setScale(SCALE, ROUNDING)
    
    /**
     * Sum a list of BigDecimal values
     */
    fun List<BigDecimal>.sum(): BigDecimal = 
        this.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
    
    /**
     * Format BigDecimal as currency string
     */
    fun BigDecimal.formatCurrency(symbol: String = "$"): String {
        return "$symbol${this.setScale(SCALE, ROUNDING)}"
    }
}

/**
 * Type-safe Money class for currency values.
 * Prevents accidental mixing of currency with regular numbers.
 * 
 * USAGE:
 * ```
 * val price = Money(19.99)
 * val quantity = 3
 * val total = price * quantity.toDouble()
 * ```
 */
data class Money(val amount: BigDecimal) : Comparable<Money> {
    
    constructor(amount: Double) : this(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP))
    constructor(amount: Long) : this(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP))
    constructor(amount: Int) : this(BigDecimal.valueOf(amount.toLong()).setScale(2, RoundingMode.HALF_UP))
    
    // Arithmetic operations
    operator fun plus(other: Money) = Money(amount.add(other.amount))
    operator fun minus(other: Money) = Money(amount.subtract(other.amount))
    operator fun times(multiplier: Double) = Money(amount.multiply(BigDecimal.valueOf(multiplier)))
    operator fun times(multiplier: Int) = Money(amount.multiply(BigDecimal.valueOf(multiplier.toLong())))
    operator fun div(divisor: Double) = Money(amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP))
    operator fun div(divisor: Int) = Money(amount.divide(BigDecimal.valueOf(divisor.toLong()), 2, RoundingMode.HALF_UP))
    
    // Unary operations
    operator fun unaryMinus() = Money(amount.negate())
    
    // Comparison
    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)
    
    // Conversions
    fun toDouble(): Double = amount.toDouble()
    fun toLong(): Long = amount.toLong()
    
    // Formatting
    fun format(symbol: String = "$"): String = "$symbol${amount.setScale(2, RoundingMode.HALF_UP)}"
    override fun toString(): String = format()
    
    // Utility
    fun isPositive(): Boolean = amount > BigDecimal.ZERO
    fun isNegative(): Boolean = amount < BigDecimal.ZERO
    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0
    fun abs(): Money = Money(amount.abs())
    
    companion object {
        val ZERO = Money(BigDecimal.ZERO)
        val ONE = Money(BigDecimal.ONE)
        
        /**
         * Sum a list of Money values
         */
        fun sum(values: List<Money>): Money = 
            Money(values.fold(BigDecimal.ZERO) { acc, money -> acc.add(money.amount) })
        
        /**
         * Calculate percentage
         */
        fun percentage(value: Money, percent: Double): Money = 
            Money(value.amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100.0), 2, RoundingMode.HALF_UP))
    }
}

/**
 * Extension functions for collection operations with Money
 */
fun Iterable<Money>.sum(): Money = Money.sum(this.toList())
fun Iterable<Money>.average(): Money {
    val list = this.toList()
    return if (list.isEmpty()) Money.ZERO else Money.sum(list) / list.size
}
