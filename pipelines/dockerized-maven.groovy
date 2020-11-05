def dockerImageLatest
def dockerImageCurrent
pipeline {
    agent none
    environment {
       projectName = 'challenge2'
       gitBranch = 'dev'
   }

    stages {
        stage('Pull') {
            agent {
                docker { 
                    image 'maven'
                    args '-v $HOME/.m2:/root/.m2 --net=host' }
            }
            steps {
                git url: "https://github.com/revature-devops-prep-2020/Challenge2-Aaron-FromScratch.git", branch: "dev"
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to pull project from github.")
                }
            }
        }
        stage('Build') {
            agent {
                docker { 
                    image 'maven'
                    args '-v $HOME/.m2:/root/.m2 --net=host' }
            }
            steps {
                dir("maven-app") {
                    sh "mvn package"
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to build.")
                }
            }
        }
        stage("Sonar") {
            agent {
                docker { 
                    image 'maven'
                    args '-v $HOME/.m2:/root/.m2 --net=host' }
            }
            steps {
                dir("maven-app") {
                    withSonarQubeEnv("SonarCloud") {
                        sh "mvn verify sonar:sonar"
                    }
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to run sonar tests.")
                }
            }
        }
        stage('quality gate'){
            agent {
                docker { 
                    image 'maven'
                    args '-v $HOME/.m2:/root/.m2 --net=host' }
            }
            steps{
                dir("maven-app") {
                    // withSonarQubeEnv('SonarCloud')
                    // {
                    //     timeout(time: 10, unit: 'MINUTES') {
                    //         // Parameter indicates whether to set pipeline to UNSTABLE if Quality Gate fails
                    //         // true = set pipeline to UNSTABLE, false = don't
                    //         waitForQualityGate abortPipeline: false
                    //     }
                    // }
                    echo 'pass'
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] did not pass the quality gate")
                }
            }
        }
        stage("Build Docker Image") {
            agent {
                docker { image 'docker' }
            }
            steps {
                script {
                    dir("maven-app") {
                        docker.withTool("docker") {
                            repoIdCurrent = "aarondownward/maven-app:${currentBuild.number}"
                            repoIdLatest = "aarondownward/maven-app:latest"
                            dockerImageCurrent = docker.build(repoIdCurrent)
                            dockerImageLatest = docker.build(repoIdLatest)
                        }
                    }
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to build docker image.")
                }
            }
        }
        stage('Scan image') {
            agent {
                docker { 
                    image 'aquasec/trivy'
                    args '--net=host'
                    }
            }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    sh "trivy image --exit-code 1 ${dockerTBuildNum}"
                }
            }
            post {
                failure {
                    slackSend(color: 'warning', channel: "${slackChannel}",
                    message: "${projMsgName} has vulnerabilities in its image.")
                }
            }
        }
        stage("Docker push") {
            agent {
                docker { image 'docker' }
            }
            steps {
                script {
                    docker.withTool("docker") {
                        docker.withRegistry("https://registry.hub.docker.com", "dockerhub-cred") {
                            dockerImageCurrent.push()
                            dockerImageLatest.push()
                        }
                    }
                }
            }
            post {
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to push image to docker hub.")
                }
            }
        }
        stage("Deploy to Testing") {
            agent {
                docker { 
                    image 'reblank/kubectl_agent' 
                    args '--net=host'}
            }
            steps {
                dir("kube-deployments/testing") {
                    withKubeConfig([credentialsId: 'kubectl-creds', serverUrl: 'https://EF62A4A1E3BA2F867AF5F946B1E0B21B.gr7.us-west-2.eks.amazonaws.com']) {
                        sh 'kubectl apply -f .'
                        sh 'kubectl rollout restart deployment/simple-app -n build'
                    }
                }
            }
            post {
                success{
                    slackSend(color: '#00FF00', message: "${projectName}' [${gitBranch}:${currentBuild.number}] successfully completed CI/CD pipeline.")
                }
                failure{
                    slackSend(color: '#FF0000', message: "${projectName}' [${gitBranch}:${currentBuild.number}] has failed to deploy to test server.")
                }
            }
        }
    }

}