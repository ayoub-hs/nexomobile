package com.nexopos.desktop.hardware

import com.nexopos.shared.hardware.BarcodeScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/**
 * Desktop implementation of barcode scanner using keyboard wedge mode.
 *
 * Most USB barcode scanners work as keyboard wedge devices - they simulate
 * keyboard input and send characters followed by Enter/Return.
 *
 * This implementation:
 * - Accumulates keyboard characters synchronously
 * - Detects rapid input typical of barcode scanners (<100ms between chars)
 * - Emits accumulated string as barcode when Enter is pressed
 * - Auto-clears buffer after timeout to prevent stale data
 * - Consumes Enter key events when barcode detected to prevent propagation
 */
class DesktopBarcodeScanner : BarcodeScanner {
  private val _scannedBarcodes = MutableSharedFlow<String>(replay = 0)
  override val scannedBarcodes: Flow<String> = _scannedBarcodes.asSharedFlow()

  override var isScanning: Boolean = false
    private set

  // Buffer for accumulating barcode characters
  private val barcodeBuffer = StringBuilder()

  // Timestamp of last character input (for timing-based detection)
  private var lastInputTime: Long = 0

  // Track if we're in a shifted sequence (scanner sending Shift+key)
  private var shiftPressed: Boolean = false

  // Coroutine scope for emitting to Flow
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  // Threshold for clearing buffer (milliseconds)
  private val bufferClearThreshold = 500L
  
  // Rate limiting to prevent excessive processing
  private var lastProcessTime = 0L
  private companion object {
    private const val MIN_PROCESS_INTERVAL = 50L // 50ms between scans
  }

  override suspend fun startScanning() {
    isScanning = true
    barcodeBuffer.clear()
    lastInputTime = 0
    println("[DesktopBarcodeScanner] Started scanning (keyboard wedge mode)")
  }

  override suspend fun stopScanning() {
    isScanning = false
    barcodeBuffer.clear()
    lastInputTime = 0
    println("[DesktopBarcodeScanner] Stopped scanning")
  }

  /**
   * Process keyboard input for barcode detection.
   * Should be called from window-level key event handler.
   *
   * This is NOT a suspend function - it processes synchronously and returns
   * true if the event should be consumed (barcode detected on Enter).
   *
   * @param event KeyEvent from Compose
   * @return true if the event was consumed (Enter with barcode), false otherwise
   */
  fun processKeyEvent(event: KeyEvent): Boolean {
    if (!isScanning) {
      return false
    }

    // Rate limiting to prevent excessive processing
    val now = System.currentTimeMillis()
    if (now - lastProcessTime < MIN_PROCESS_INTERVAL) {
      return false // Skip, too soon
    }
    lastProcessTime = now

    val currentTime = now
    val timeSinceLastInput = currentTime - lastInputTime

    // If too much time passed, clear buffer (prevents mixing manual typing with scanner)
    if (lastInputTime > 0 && timeSinceLastInput > bufferClearThreshold) {
      if (barcodeBuffer.isNotEmpty()) {
        println("[DesktopBarcodeScanner] Buffer timeout, clearing: ${barcodeBuffer}")
      }
      barcodeBuffer.clear()
      shiftPressed = false
    }

    lastInputTime = currentTime

    return when (val key = event.key) {
      // Track Shift key state (some scanners send Shift+key for certain characters)
      Key.ShiftLeft, Key.ShiftRight -> {
        shiftPressed = true
        false // Don't consume Shift key itself
      }

      // Enter/Return key - emit accumulated barcode
      Key.Enter, Key.NumPadEnter -> {
        shiftPressed = false
        println("[DesktopBarcodeScanner] Enter key pressed, buffer: '${barcodeBuffer}'")
        if (barcodeBuffer.isNotEmpty()) {
          val barcode = barcodeBuffer.toString().trim()
          barcodeBuffer.clear()

          if (barcode.isNotBlank() && barcode.length >= 3) {
            println("[DesktopBarcodeScanner] ✓ Barcode detected: $barcode (consuming Enter key)")
            // Emit to Flow in coroutine (non-blocking)
            scope.launch {
              _scannedBarcodes.emit(barcode)
            }
            // Consume the Enter event to prevent it from propagating
            return true
          }
        }
        barcodeBuffer.clear()
        println("[DesktopBarcodeScanner] Enter pressed but no valid barcode in buffer")
        false // Don't consume if no valid barcode
      }

      // Alphanumeric and symbol keys - accumulate
      else -> {
        val char = keyToChar(key, shiftPressed)
        shiftPressed = false // Reset after processing character

        if (char != null) {
          barcodeBuffer.append(char)
          println("[DesktopBarcodeScanner] Accumulated '$char', buffer now: '${barcodeBuffer}'")
          // Don't consume regular character keys - let them propagate if needed
          // This allows shortcuts like 'G' or 'D' to still work when buffer is empty
          false
        } else {
          // Ignore non-barcode keys (shortcuts, etc.)
          false
        }
      }
    }
  }

  /**
   * Manual barcode detection for testing or alternative input methods.
   * Call this method when barcode input is detected from other sources.
   */
  suspend fun onBarcodeDetected(barcode: String) {
    if (isScanning && barcode.isNotBlank()) {
      println("[DesktopBarcodeScanner] Manual barcode input: $barcode")
      _scannedBarcodes.emit(barcode.trim())
    }
  }

  /**
   * Convert Compose Key to character.
   * Handles common barcode characters: digits, letters, and basic symbols.
   * Some scanners send Shift+key combinations for special characters.
   */
  private fun keyToChar(key: Key, shifted: Boolean = false): Char? {
    return when (key) {
      // Numbers (top row) - handle shifted symbols
      Key.Zero -> if (shifted) ')' else '0'
      Key.One -> if (shifted) '!' else '1'
      Key.Two -> if (shifted) '@' else '2'
      Key.Three -> if (shifted) '#' else '3'
      Key.Four -> if (shifted) '$' else '4'
      Key.Five -> if (shifted) '%' else '5'
      Key.Six -> if (shifted) '^' else '6'
      Key.Seven -> if (shifted) '&' else '7'
      Key.Eight -> if (shifted) '*' else '8'
      Key.Nine -> if (shifted) '(' else '9'

      // Numpad numbers (never shifted)
      Key.NumPad0 -> '0'
      Key.NumPad1 -> '1'
      Key.NumPad2 -> '2'
      Key.NumPad3 -> '3'
      Key.NumPad4 -> '4'
      Key.NumPad5 -> '5'
      Key.NumPad6 -> '6'
      Key.NumPad7 -> '7'
      Key.NumPad8 -> '8'
      Key.NumPad9 -> '9'

      // Letters - handle case
      Key.A -> if (shifted) 'A' else 'a'
      Key.B -> if (shifted) 'B' else 'b'
      Key.C -> if (shifted) 'C' else 'c'
      Key.D -> if (shifted) 'D' else 'd'
      Key.E -> if (shifted) 'E' else 'e'
      Key.F -> if (shifted) 'F' else 'f'
      Key.G -> if (shifted) 'G' else 'g'
      Key.H -> if (shifted) 'H' else 'h'
      Key.I -> if (shifted) 'I' else 'i'
      Key.J -> if (shifted) 'J' else 'j'
      Key.K -> if (shifted) 'K' else 'k'
      Key.L -> if (shifted) 'L' else 'l'
      Key.M -> if (shifted) 'M' else 'm'
      Key.N -> if (shifted) 'N' else 'n'
      Key.O -> if (shifted) 'O' else 'o'
      Key.P -> if (shifted) 'P' else 'p'
      Key.Q -> if (shifted) 'Q' else 'q'
      Key.R -> if (shifted) 'R' else 'r'
      Key.S -> if (shifted) 'S' else 's'
      Key.T -> if (shifted) 'T' else 't'
      Key.U -> if (shifted) 'U' else 'u'
      Key.V -> if (shifted) 'V' else 'v'
      Key.W -> if (shifted) 'W' else 'w'
      Key.X -> if (shifted) 'X' else 'x'
      Key.Y -> if (shifted) 'Y' else 'y'
      Key.Z -> if (shifted) 'Z' else 'z'

      // Common barcode symbols
      Key.Minus -> if (shifted) '_' else '-'
      Key.NumPadSubtract -> '-'
      Key.Period -> if (shifted) '>' else '.'
      Key.NumPadDot -> '.'
      Key.Slash -> if (shifted) '?' else '/'
      Key.NumPadDivide -> '/'
      Key.Spacebar -> ' '
      Key.Comma -> if (shifted) '<' else ','
      Key.Semicolon -> if (shifted) ':' else ';'
      Key.Apostrophe -> if (shifted) '"' else '\''
      Key.LeftBracket -> if (shifted) '{' else '['
      Key.RightBracket -> if (shifted) '}' else ']'
      Key.Backslash -> if (shifted) '|' else '\\'
      Key.Equals -> if (shifted) '+' else '='
      Key.Grave -> if (shifted) '~' else '`'

      else -> null
    }
  }

  /**
   * Clear the buffer manually if needed
   */
  fun clearBuffer() {
    barcodeBuffer.clear()
    lastInputTime = 0
  }
}
