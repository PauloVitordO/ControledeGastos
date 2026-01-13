package com.example.controledegastos
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TelaGasto()
        }
    }
}

@Composable
fun TelaGasto() {

    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val categorias = listOf(
        "Alimentação",
        "Aluguel",
        "Transporte",
        "Lazer",
        "Outros"
    )

    var categoriaSelecionada by remember { mutableStateOf(categorias[0]) }

    val db = AppDatabase.getDatabase(context)
    val gastoDao = db.gastoDao()
    val coroutineScope = rememberCoroutineScope()
    var gastos by remember { mutableStateOf(listOf<GastoEntity>()) }
    var total by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(Unit) {
        gastos = gastoDao.listarGastos()
        total = gastoDao.totalGastos() ?: 0.0
    }
    var descricao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            bitmap?.let {
                processarImagem(it) { d, v, dt ->
                    descricao = d
                    valor = v
                    data = dt
                }
            }
        }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = uriToBitmap(context, it)
            bitmap?.let { bmp ->
                processarImagem(bmp) { d, v, dt ->
                    // Preenche o que encontrou
                    descricao = d
                    valor = v
                    data = dt

                    // VERIFICAÇÃO E AVISO:
                    if (d.isEmpty() || v.isEmpty() || dt.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Alguns dados não foram lidos. Por favor, preencha manualmente.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Dados lidos com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .verticalScroll(scrollState)) {


        Text("Novo Gasto", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = descricao,
            onValueChange = { descricao = it },
            label = { Text("Descrição") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = valor,
            onValueChange = { valor = it },
            label = { Text("Valor") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = data,
            onValueChange = { input ->
                // Permite apenas números e limita a 8 dígitos (DDMMAAAA)
                if (input.all { it.isDigit() } && input.length <= 8) {
                    data = input
                }
            },
            label = { Text("Data (DDMMAAAA)") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ex: 13012026") },
            // A mágica acontece aqui: transforma os números em formato de data visualmente
            visualTransformation = DateTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))
        var expanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = categoriaSelecionada,
                onValueChange = {
                    categoriaSelecionada = it
                    // Opcional: expanded = true (se quiser que o menu abra enquanto digita)
                },
                label = { Text("Categoria (ou escreva uma nova)") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    // A setinha agora é o botão principal para abrir a lista
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    }
                }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                categorias.forEach { categoria ->
                    DropdownMenuItem(
                        text = { Text(categoria) },
                        onClick = {
                            categoriaSelecionada = categoria
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = { cameraLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📸 Tirar foto do comprovante")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🖼️ Escolher foto da galeria")
        }
        Button(
            onClick = {
                if (descricao.isNotBlank() && valor.isNotBlank()) {
                    coroutineScope.launch {
                        try {
                            // Tenta converter e salvar
                            val valorNumerico = valor.toDouble()

                            gastoDao.inserirGasto(
                                GastoEntity(
                                    descricao = descricao,
                                    valor = valorNumerico,
                                    data = data,
                                    categoria = categoriaSelecionada
                                )
                            )
                            // Atualiza a lista e o total
                            gastos = gastoDao.listarGastos()
                            total = gastoDao.totalGastos() ?: 0.0

                            // Limpa os campos
                            descricao = ""; valor = ""; data = ""
                            Toast.makeText(context, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()

                        } catch (_: Exception) {
                            // Se o valor não for um número (ex: "10,50" com vírgula em vez de ponto)
                            Toast.makeText(context, "Erro: Valor inválido. Use ponto em vez de vírgula.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Preencha a descrição e o valor!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("💾 Salvar gasto")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "💰 Total do mês: R$ ${"%.2f".format(total)}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("Gastos cadastrados:")

        // Este novo bloco cria cartões bonitos para cada gasto
        gastos.forEach { gasto ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = gasto.descricao, style = MaterialTheme.typography.titleMedium)
                        Text(text = "R$ ${"%.2f".format(gasto.valor)}", color = MaterialTheme.colorScheme.primary)
                        Text(text = gasto.categoria, style = MaterialTheme.typography.labelSmall)
                    }

                    // BOTÃO DE DELETAR
                    IconButton(onClick = {
                        coroutineScope.launch {
                            gastoDao.deletarGasto(gasto) // Deleta do banco
                            gastos = gastoDao.listarGastos() // Atualiza a lista na tela
                            total = gastoDao.totalGastos() ?: 0.0 // Atualiza o total
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Deletar Gasto",
                            tint = MaterialTheme.colorScheme.error // Cor vermelha
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Gastos cadastrados:")

        gastos.forEach {
            Text("• ${it.descricao} - ${it.categoria} - R$ ${it.valor}")
        }

    }
}
fun processarImagem(
    bitmap: Bitmap,
    onResult: (descricao: String, valor: String, data: String) -> Unit
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    recognizer.process(image)
        .addOnSuccessListener { result ->
            val texto = result.text
            val linhas = texto.lines()


            val descricaoDetectada = linhas.firstOrNull { it.length > 5 } ?: ""

            val valorRegex = Regex("""R\$ ?\d+[.,]\d{2}""")
            val valorDetectado = valorRegex.findAll(texto).lastOrNull()?.value
                ?.replace("R$", "")
                ?.replace(",", ".")
                ?.trim() ?: ""

            val dataRegex = Regex("""\d{2}/\d{2}/\d{4}""")
            val dataDetectada = dataRegex.find(texto)?.value ?: ""
            onResult(descricaoDetectada, valorDetectado, dataDetectada)
        }
        .addOnFailureListener {
            onResult("", "", "")
        }
}
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
fun formatarData(millis: Long?): String {
    if (millis == null) return ""
    val formatter = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(millis))
}
class DateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val input = text.text
        var out = ""
        for (i in input.indices) {
            out += input[i]
            if (i == 1 || i == 3) out += "/"
        }

        val numberOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset
                if (offset <= 3) return offset + 1
                if (offset <= 8) return offset + 2
                return 10
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset - 1
                if (offset <= 10) return offset - 2
                return 8
            }
        }
        return TransformedText(AnnotatedString(out), numberOffsetTranslator)
    }
}