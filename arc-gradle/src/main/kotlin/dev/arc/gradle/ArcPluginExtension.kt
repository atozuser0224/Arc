package dev.arc.gradle

import org.gradle.api.Project

open class ArcPluginExtension(project: Project) {
    var name: String = project.name
    var version: String = project.version.toString()
    var main: String = ""
    var apiVersion: String = "1.21"
    var description: String = ""
    var author: String = ""
    var authors: MutableList<String> = mutableListOf()
    var website: String = ""
    var prefix: String = ""

    private val _depend = mutableListOf<String>()
    private val _softDepend = mutableListOf<String>()
    private val _loadBefore = mutableListOf<String>()

    val depend: List<String> get() = _depend
    val softDepend: List<String> get() = _softDepend
    val loadBefore: List<String> get() = _loadBefore

    fun depend(vararg names: String) { _depend += names }
    fun softDepend(vararg names: String) { _softDepend += names }
    fun loadBefore(vararg names: String) { _loadBefore += names }
}
