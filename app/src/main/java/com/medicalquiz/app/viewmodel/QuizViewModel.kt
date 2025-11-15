package com.medicalquiz.app.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.utils.Resource
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.medicalquiz.app.data.models.Subject

/**
 * ViewModel that holds quiz state and performs database operations.
 * Keeps question/answers out of the Activity for lifecycle safety.
 */
class QuizViewModel : ViewModel() {

    private var databaseManager: DatabaseManager? = null

    val questionIds = MutableLiveData<List<Long>>(emptyList())
    val currentQuestionIndex = MutableLiveData(0)
    val currentQuestion = MutableLiveData<Question?>(null)
    val currentAnswers = MutableLiveData<List<Answer>>(emptyList())
    val isLoading = MutableLiveData(false)
    val selectedSubjectIds = MutableLiveData<Set<Long>>(emptySet())
    val selectedSystemIds = MutableLiveData<Set<Long>>(emptySet())
    val performanceFilter = MutableLiveData<PerformanceFilter>(PerformanceFilter.ALL)

    fun initializeDatabase(dbPath: String) {
        // Deprecated - prefer setDatabaseManager from Activity which gives control over switching
        viewModelScope.launch(Dispatchers.IO) {
            databaseManager = DatabaseManager(dbPath)
            val ids = databaseManager?.getQuestionIds() ?: emptyList()
            questionIds.postValue(ids)
        }
    }

    fun setDatabaseManager(db: DatabaseManager) {
        databaseManager = db
        viewModelScope.launch(Dispatchers.IO) {
            val ids = databaseManager?.getQuestionIds() ?: emptyList()
            questionIds.postValue(ids)
        }
    }

    fun loadQuestion(index: Int) {
        val ids = questionIds.value ?: emptyList()
        val questionId = ids.getOrNull(index) ?: return
        currentQuestionIndex.postValue(index)

        viewModelScope.launch(Dispatchers.IO) {
            isLoading.postValue(true)
            try {
                val q = databaseManager?.getQuestionById(questionId)
                val a = databaseManager?.getAnswersForQuestion(questionId) ?: emptyList()
                currentQuestion.postValue(q)
                currentAnswers.postValue(a)
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun loadNext() {
        val current = (currentQuestionIndex.value ?: 0) + 1
        if (current < (questionIds.value?.size ?: 0)) loadQuestion(current)
    }

    fun loadPrevious() {
        val current = (currentQuestionIndex.value ?: 0) - 1
        if (current >= 0) loadQuestion(current)
    }

    fun setQuestionIds(ids: List<Long>) {
        questionIds.postValue(ids)
    }

    fun setSelectedSubjects(ids: Set<Long>) {
        selectedSubjectIds.postValue(ids)
        loadFilteredQuestionIds()
    }

    fun setSelectedSystems(ids: Set<Long>) {
        selectedSystemIds.postValue(ids)
        loadFilteredQuestionIds()
    }

    fun setPerformanceFilter(filter: PerformanceFilter) {
        performanceFilter.postValue(filter)
        loadFilteredQuestionIds()
    }

    fun loadFilteredQuestionIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = selectedSubjectIds.value?.toList()
            val sys = selectedSystemIds.value?.toList()
            val pf = performanceFilter.value ?: PerformanceFilter.ALL
            val ids = databaseManager?.getQuestionIds(subjectIds = s, systemIds = sys, performanceFilter = pf) ?: emptyList()
            questionIds.postValue(ids)
        }
    }

    suspend fun pruneInvalidSystems(): List<Long> {
        val subjects = selectedSubjectIds.value?.toList() ?: return emptyList()
        return databaseManager?.getSystems(subjects)?.map { it.id } ?: emptyList()
    }

    suspend fun fetchFilteredQuestionIds(): List<Long> {
        val s = selectedSubjectIds.value?.toList()
        val sys = selectedSystemIds.value?.toList()
        val pf = performanceFilter.value ?: PerformanceFilter.ALL
        return databaseManager?.getQuestionIds(subjectIds = s, systemIds = sys, performanceFilter = pf) ?: emptyList()
    }

    suspend fun getSystemsForSubjects(subjectIds: List<Long>?): List<System> {
        return databaseManager?.getSystems(subjectIds) ?: emptyList()
    }

    suspend fun getSubjects(): List<Subject> {
        return databaseManager?.getSubjects() ?: emptyList()
    }

    // LiveData wrapper for system fetch state
    private val _systemsResource = MutableLiveData<Resource<List<System>>>(Resource.Success(emptyList()))
    val systemsResource: LiveData<Resource<List<System>>> = _systemsResource

    private var lastFetchedSubjectIds: List<Long>? = null

    fun fetchSystemsForSubjects(subjectIds: List<Long>?) {
        // Prevent refetch when same subjects requested
        if (subjectIds == lastFetchedSubjectIds) return
        lastFetchedSubjectIds = subjectIds

        viewModelScope.launch(Dispatchers.IO) {
            _systemsResource.postValue(Resource.Loading)
            try {
                val systems = databaseManager?.getSystems(subjectIds) ?: emptyList()
                _systemsResource.postValue(Resource.Success(systems))
            } catch (e: Exception) {
                _systemsResource.postValue(Resource.Error(e.message ?: "Unknown error"))
            }
        }
    }

    // Expose database manager for other helpers if needed (avoid direct DB calls from Activity)
    fun getDatabaseManager(): DatabaseManager? = databaseManager
}
