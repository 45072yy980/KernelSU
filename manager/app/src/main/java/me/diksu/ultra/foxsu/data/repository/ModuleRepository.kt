package me.diksu.ultra.foxsu.data.repository

import me.diksu.ultra.foxsu.data.model.Module
import me.diksu.ultra.foxsu.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
