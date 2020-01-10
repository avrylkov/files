@Library('cs-pipeline-lib@2.0') _

import com.lesfurets.jenkins.unit.global.lib.Library
import ru.sbrf.processing.pipeline.vcs.CheckoutConfig
import ru.sbrf.processing.pipeline.gradle.Gradle
import ru.sbrf.processing.pipeline.ose.Openshift
import ru.sbrf.processing.pipeline.jenkins.CurrentBuild

csPipeline() {
    csNode() {
        csStage("Checkout") {
            dir(CheckoutConfig.get().repoName) {
                csCheckout()
            }
        }

        def gradleProperties
        String nexusVersion

        csStage("AutoBuild") {
            dir(CheckoutConfig.get().repoName) {
                gradleProperties = readProperties file: 'gradle.properties'
                nexusVersion = gradleProperties.'org.gradle.project.version' - "SNAPSHOT" - "\n" + BUILD_NUMBER
                Gradle gradle = new Gradle(this, "6.0.1")
                gradle.exec("build -Pversion=${nexusVersion}")
            }
        }
        csStage("Build Image") {
            dir(CheckoutConfig.get().repoName) {
                def buildConfig = readFile(BUILD_CONFIG)
                def dockerFile = readFile(DOCKER_FILE)
                def newBuildConfig = buildConfig.replace('${dockerImageTag}', nexusVersion)
                def newDockerFile = dockerFile.replace('${nexusVersion}', nexusVersion)
                writeFile file: "${BUILD_CONFIG}.new", text: newBuildConfig
                writeFile file: "${DOCKER_FILE}.new", text: newDockerFile
                sh "rm -f ${BUILD_CONFIG}"
                sh "rm -f ${DOCKER_FILE}"
                sh "mv ${BUILD_CONFIG}.new ${BUILD_CONFIG}"
                sh "mv ${DOCKER_FILE}.new ${DOCKER_FILE}"
                println(buildConfig)
                println(dockerFile)
                Openshift buildShift = new Openshift(this, SERVER_ID, CRED_ID, OSE_PROJECT)

                String result = buildShift.build(BUILD_CONFIG, "Dockerfile", "build/libs/jcb-export-0.0.1-${BUILD_NUMBER}.jar")
                if (result) {
                    if (result.contains("build error")) {
                        CurrentBuild.setFailedResult(this)
                    }
                }
            }
        }
    }
}
