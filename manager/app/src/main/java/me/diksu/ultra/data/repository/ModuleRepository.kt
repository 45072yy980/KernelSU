package me.diksu.ultra.data.repository

import me.diksu.ultra.data.model.Module
import me.diksu.ultra.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
