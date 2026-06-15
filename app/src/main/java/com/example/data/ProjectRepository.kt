package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<VideoProject>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Int): VideoProject? {
        return projectDao.getProjectById(id)
    }

    suspend fun insertProject(project: VideoProject): Long {
        return projectDao.insertProject(project)
    }

    suspend fun updateProject(project: VideoProject) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(project: VideoProject) {
        projectDao.deleteProject(project)
    }

    suspend fun deleteAll() {
        projectDao.deleteAll()
    }
}
