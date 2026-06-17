package com.lumecard.app.ui.screens.knowledgebase

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.repository.KnowledgeBaseRepository
import com.lumecard.shared.repository.LearningPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID

class KnowledgeBaseViewModel(
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val planRepository: LearningPlanRepository
) : ScreenModel {

    private val _knowledgeBases = MutableStateFlow<List<KnowledgeBase>>(emptyList())
    val knowledgeBases: StateFlow<List<KnowledgeBase>> = _knowledgeBases.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadKnowledgeBases()
    }

    fun loadKnowledgeBases() {
        screenModelScope.launch {
            _isLoading.value = true
            knowledgeBaseRepository.getAll().collect { list ->
                _knowledgeBases.value = list
                _isLoading.value = false
            }
        }
    }

    suspend fun createKnowledgeBase(name: String, description: String?): KnowledgeBase {
        val kb = KnowledgeBase(
            id = "kb_${UUID.randomUUID().toString().take(8)}",
            name = name,
            description = description,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        knowledgeBaseRepository.insert(kb)
        return kb
    }

    suspend fun updateKnowledgeBase(id: String, name: String, description: String?) {
        val kb = knowledgeBaseRepository.getById(id) ?: return
        val updated = kb.copy(
            name = name,
            description = description,
            updatedAt = Clock.System.now()
        )
        knowledgeBaseRepository.update(updated)
    }

    suspend fun deleteKnowledgeBase(id: String) {
        knowledgeBaseRepository.delete(id)
        val plans = planRepository.getAll().first()
        for (plan in plans) {
            if (id in plan.knowledgeBaseIds) {
                planRepository.update(plan.copy(
                    knowledgeBaseIds = plan.knowledgeBaseIds - id,
                    updatedAt = Clock.System.now()
                ))
            }
        }
    }

    suspend fun getById(id: String): KnowledgeBase? = knowledgeBaseRepository.getById(id)

    suspend fun getDefaultOrCreate(): KnowledgeBase {
        val existing = knowledgeBases.value.firstOrNull { it.id == "default" }
        if (existing != null) return existing
        return createKnowledgeBase("Default", null)
    }
}
