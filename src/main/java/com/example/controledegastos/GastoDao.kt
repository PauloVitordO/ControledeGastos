package com.example.controledegastos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GastoDao {
    @Insert
    suspend fun inserirGasto(gasto: GastoEntity)

    @Query("SELECT * FROM gastos")
    suspend fun listarGastos(): List<GastoEntity>

    @Query("SELECT SUM(valor) FROM gastos")
    suspend fun totalGastos(): Double?

    @Query("DELETE FROM gastos WHERE id = :id")
    suspend fun excluirGasto(id: Int)

    @Query("DELETE FROM gastos")
    suspend fun excluirTudo()

    @Delete
    suspend fun deletarGasto(gasto: GastoEntity)
}