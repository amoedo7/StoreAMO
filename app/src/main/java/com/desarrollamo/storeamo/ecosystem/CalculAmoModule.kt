package com.desarrollamo.storeamo.ecosystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

@Composable
fun CalculAmoModule() {
    var display by remember { mutableStateOf("0") }
    var accumulator by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var replaceDisplay by remember { mutableStateOf(false) }

    fun value() = display.toDoubleOrNull() ?: 0.0
    fun format(v: Double): String {
        if (!v.isFinite()) return "Error"
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString().take(18)
    }

    fun calculate(left: Double, operator: String, right: Double): Double? = when (operator) {
        "+" -> left + right
        "−" -> left - right
        "×" -> left * right
        "÷" -> if (right == 0.0) null else left / right
        else -> right
    }

    fun digit(d: String) {
        if (display == "Error" || replaceDisplay) {
            display = d
            replaceDisplay = false
        } else if (display == "0") display = d
        else if (display.length < 18) display += d
    }

    fun decimal() {
        if (display == "Error" || replaceDisplay) {
            display = "0."
            replaceDisplay = false
        } else if (!display.contains('.') && display.length < 18) display += "."
    }
    fun operator(next: String) {
        val current = value()
        if (accumulator != null && pendingOp != null && !replaceDisplay) {
            val result = calculate(accumulator!!, pendingOp!!, current)
            if (result == null) {
                display = "Error"; accumulator = null; pendingOp = null; replaceDisplay = true; return
            }
            accumulator = result
            display = format(result)
        } else accumulator = current
        pendingOp = next
        replaceDisplay = true
    }

    fun equals() {
        val left = accumulator ?: return
        val op = pendingOp ?: return
        val result = calculate(left, op, value())
        display = result?.let(::format) ?: "Error"
        accumulator = null
        pendingOp = null
        replaceDisplay = true
    }

    fun clear() {
        display = "0"; accumulator = null; pendingOp = null; replaceDisplay = false
    }

    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CalculAMO", fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(display, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), fontSize = 38.sp, fontWeight = FontWeight.Bold)
        CalcRow(listOf("C", "⌫", "%", "÷")) { key ->
            when (key) {
                "C" -> clear()
                "⌫" -> if (!replaceDisplay && display != "Error") display = display.dropLast(1).ifBlank { "0" }
                "%" -> { display = format(value() / 100.0); replaceDisplay = true }
                else -> operator(key)
            }
        }
        CalcRow(listOf("7", "8", "9", "×")) { if (it == "×") operator(it) else digit(it) }
        CalcRow(listOf("4", "5", "6", "−")) { if (it == "−") operator(it) else digit(it) }
        CalcRow(listOf("1", "2", "3", "+")) { if (it == "+") operator(it) else digit(it) }
        CalcRow(listOf("±", "0", ".", "=")) { key ->
            when (key) {
                "±" -> if (display != "0" && display != "Error") display = if (display.startsWith("-")) display.drop(1) else "-$display"
                "." -> decimal()
                "=" -> equals()
                else -> digit(key)
            }
        }
    }
}
@Composable
private fun CalcRow(keys: List<String>, onKey: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            Button(onClick = { onKey(key) }, modifier = Modifier.weight(1f)) {
                Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
