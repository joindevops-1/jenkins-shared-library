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
                    sh """
                        aws eks update-kubeconfig --region us-east-1 --name expense-dev
                        cd helm
                        sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                        helm install ${component} .
                    """
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
            }
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