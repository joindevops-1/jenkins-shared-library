def call(Map configMap){
    pipeline {
        agent {
            label 'AGENT-1'
        }
        options {
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
            ansiColor('xterm')
        }
        environment{
            def appVersion = '' //variable declaration
            nexusUrl = pipelineGlobals.nexusURL()
            component = configMap.get("component")
            account_id = pipelineGlobals.account_id()
            region = pipelineGlobals.region()
            def releaseExists = ''
        }
        
        stages {
            stage('read the version'){
                steps{
                    script{
                        def packageJson = readJSON file: 'package.json'
                        appVersion = packageJson.version
                        echo "application version: $appVersion"
                    }
                }
            }
            stage('Install Dependencies') {
                steps {
                sh """
                    npm install
                    ls -ltr
                    echo "application version: $appVersion"
                """
                }
            }
            stage('Build'){
                steps{
                    sh """
                    zip -q -r ${component}-${appVersion}.zip * -x Jenkinsfile -x ${component}-${appVersion}.zip
                    ls -ltr
                    """
                }
            }
            stage('Docker Build'){
                steps{
                    sh """
                        aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com
                        docker build -t ${account_id}.dkr.ecr.${region}.amazonaws.com/${component}:${appVersion} .
                        docker push ${account_id}.dkr.ecr.${region}.amazonaws.com/${component}:${appVersion}
                    """
                }
            }
            stage('Deploy'){
                steps{
                    script{
                    releaseExists = sh(script: "helm ls --all --short | grep -w ${component} || true", returnStdout: true).trim()
                    echo "Does release exists: $releaseExists"
                    if (!releaseExists.isEmpty()) {
                        echo "Helm release ${component} exists. Running helm upgrade."
                        sh """
                            aws eks update-kubeconfig --region us-east-1 --name expense-dev
                            cd helm
                            sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                            helm upgrade backend -n expense .
                        """
                    } else {
                        echo "Helm release 'backend' does not exist. Running helm install."
                        sh """
                            aws eks update-kubeconfig --region us-east-1 --name expense-dev
                            cd helm
                            sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                            helm install backend -n expense .
                        """
                    }
                    }
                }
            }
            stage('Verify Deployment') {
                steps {
                    script {    
                        def deploymentStatus = sh(script: "kubectl get deploy backend -n expense -o jsonpath='{.status.conditions[?(@.type==\"Available\")].status}'", returnStdout: true).trim()
                        echo "Deployment status: $deploymentStatus"

                        if (deploymentStatus != "True") {
                            echo "Deployment failed. Rolling back to previous version."
                            try {
                                sh """
                                    aws eks update-kubeconfig --region us-east-1 --name expense-dev
                                    helm rollback backend 0 -n expense
                                """
                                // Verify rollback deployment status
                                sleep(60) // Wait for the rollback to take effect
                                def rollbackStatus = sh(script: "kubectl get deploy backend -n expense -o jsonpath='{.status.conditions[?(@.type==\"Available\")].status}'", returnStdout: true).trim()
                                echo "Rollback deployment status: $rollbackStatus"

                                if (rollbackStatus != "True") {
                                    error("Rollback failed. Need to investigate why the earlier successful version failed.")
                                } else {
                                    echo "Rollback successful."
                                }
                            } catch (Exception e) {
                                error("Exception during rollback: ${e}. Need to investigate.")
                            }
                        } else {
                            echo "Deployment successful."
                        }
                    }
                }
            }           
            /* stage('Upload Artifact'){
                steps{
                    script{
                        nexusArtifactUploader(
                            nexusVersion: 'nexus3',
                            protocol: 'http',
                            nexusUrl: "$nexusUrl",
                            groupId: 'com.expense',
                            version: "${appVersion}",
                            repository: "${component}",
                            credentialsId: 'nexus-auth',
                            artifacts: [
                                [artifactId: "${component}",
                                classifier: '',
                                file: "${component}-" + "${appVersion}" + '.zip',
                                type: 'zip']
                            ]
                        )
                    }
                }
            } */
            /* stage('SonarQube Code Analysis') {
                environment {
                    scannerHome = tool 'sonar'
                }
                steps {
                    script {
                        withSonarQubeEnv('sonar') {
                            sh "${scannerHome}/bin/sonar-scanner"
                        }
                    }
                }
        }
        stage("Quality Gate") {
                steps {
                timeout(time: 1, unit: 'HOURS') {
                    waitForQualityGate abortPipeline: true
                }
                }
            }

            stage('Upload Artifact'){
                steps{
                    script{
                        nexusArtifactUploader(
                            nexusVersion: 'nexus3',
                            protocol: 'http',
                            nexusUrl: "$nexusUrl",
                            groupId: 'com.expense',
                            version: "${appVersion}",
                            repository: "${configMap.component}",
                            credentialsId: 'nexus-auth',
                            artifacts: [
                                [artifactId: "${configMap.component}",
                                classifier: '',
                                file: "${configMap.component}-" + "${appVersion}" + '.zip',
                                type: 'zip']
                            ]
                        )
                    }
                }
            }
            stage('Deploy') {
                when {
                    expression{
                        params.deploy
                    }
                }
                steps {
                    script {
                            def params = [
                                string(name: 'appVersion', value: "$appVersion")
                            ]
                            build job: "${configMap.component}-deploy", wait: false, parameters: params
                        }
                }
            } */
        }
        post { 
            always { 
                echo 'I will always say Hello again!'
                deleteDir()
            }
            success { 
                echo 'I will run when pipeline is success'
            }
            failure { 
                echo 'I will run when pipeline is failure'
            }
        }
    }
}