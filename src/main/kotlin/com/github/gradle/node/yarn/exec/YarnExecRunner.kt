package com.github.gradle.node.yarn.exec

import com.github.gradle.node.NodeExtension
import com.github.gradle.node.exec.ExecConfiguration
import com.github.gradle.node.exec.ExecRunner
import com.github.gradle.node.exec.NodeExecConfiguration
import com.github.gradle.node.npm.proxy.NpmProxy
import com.github.gradle.node.util.zip
import com.github.gradle.node.variant.VariantComputer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

internal class YarnExecRunner {
    private val variantComputer = VariantComputer()
    fun executeYarnCommand(project: Project, nodeExecConfiguration: NodeExecConfiguration) {
        val nodeExtension = NodeExtension[project]
        val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
        val yarnDirProvider = variantComputer.computeYarnDir(nodeExtension)
        val yarnBinDirProvider = variantComputer.computeYarnBinDir(yarnDirProvider)
        val yarnExecProvider = variantComputer.computeYarnExec(nodeExtension, yarnBinDirProvider)
        val additionalBinPathProvider =
                computeAdditionalBinPath(project, nodeExtension, nodeDirProvider, yarnBinDirProvider)
        val execConfiguration = ExecConfiguration(yarnExecProvider.get(),
                nodeExecConfiguration.command, additionalBinPathProvider.get(),
                addNpmProxyEnvironment(nodeExtension, nodeExecConfiguration), nodeExecConfiguration.workingDir,
                nodeExecConfiguration.ignoreExitValue, nodeExecConfiguration.execOverrides)
        val execRunner = ExecRunner()
        execRunner.execute(project, execConfiguration)
    }

    private fun addNpmProxyEnvironment(nodeExtension: NodeExtension,
                                       nodeExecConfiguration: NodeExecConfiguration): Map<String, String> {
        if (nodeExtension.useGradleProxySettings.get()
                && !NpmProxy.hasProxyConfiguration(System.getenv())) {
            val npmProxyEnvironmentVariables = NpmProxy.computeNpmProxyEnvironmentVariables()
            if (npmProxyEnvironmentVariables.isNotEmpty()) {
                return nodeExecConfiguration.environment.plus(npmProxyEnvironmentVariables)
            }
        }
        return nodeExecConfiguration.environment
    }

    private fun computeAdditionalBinPath(project: Project, nodeExtension: NodeExtension,
                                         nodeDirProvider: Provider<Directory>,
                                         yarnBinDirProvider: Provider<Directory>): Provider<List<String>> {
        return nodeExtension.useDownloaded.flatMap { useDownloaded ->
            if (!useDownloaded) {
                project.providers.provider { listOf<String>() }
            }
            val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
            val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
            val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)
            zip(nodeBinDirProvider, npmBinDirProvider, yarnBinDirProvider)
                    .map { (nodeBinDir, npmBinDir, yarnBinDir) ->
                        listOf(yarnBinDir, npmBinDir, nodeBinDir).map { file -> file.asFile.absolutePath }
                    }
        }
    }
}
